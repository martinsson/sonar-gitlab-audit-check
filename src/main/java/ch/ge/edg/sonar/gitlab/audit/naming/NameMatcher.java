package ch.ge.edg.sonar.gitlab.audit.naming;

import static ch.ge.edg.sonar.gitlab.audit.naming.SoftTfIdf.MIN_FUZZY_LENGTH;
import static ch.ge.edg.sonar.gitlab.audit.naming.SoftTfIdf.numbersConflict;
import static ch.ge.edg.sonar.gitlab.audit.naming.SoftTfIdf.similarity;

import ch.ge.edg.sonar.gitlab.audit.naming.SoftTfIdf.Doc;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Ressemblance de noms entre un chemin GitLab et un projet Sonar, pondérée par la rareté des mots
 * dans le parc (TF-IDF « souple », Cohen et al. 2003).
 *
 * <p>Ce que l'égalité de noms normalisés ratait, et ce que ceci corrige :
 *
 * <p>mots déplacés ou ajoutés `client-api` / `api-client`, `ocsin-billing` / `billing` — on compare
 * des ensembles de mots, pas des chaînes ; fautes de frappe `paiement` / `paiment` — deux mots de
 * quatre lettres ou plus se rejoignent au-dessus de 0,9 de Jaro-Winkler ; mots qui ne disent rien
 * `api`, `service`, le préfixe de l'entreprise : présents partout, ils pèsent presque zéro. Deux
 * projets « api » ne se ressemblent plus par leur seul point commun. Aucune liste à tenir : c'est
 * le parc qui dit ce qui est banal.
 *
 * <p>Les nombres sont sortis des mots avant comparaison (`app2` → `app`), puis servent de veto : si
 * les deux côtés en portent et qu'ils diffèrent, ce ne sont pas les mêmes projets. `sirh-v2` et
 * `sirh` se rejoignent ; `app1` et `app2`, jamais.
 *
 * <p>Rien ici ne décide d'une jointure : le score sort en suggestion, et {@link CrossAudit} ne le
 * compte nulle part.
 */
public final class NameMatcher {

  /** Un mot porté par plus de cette part du parc ne sert pas à proposer des candidats. */
  private static final double BLOCKING_MAX_SHARE = 0.2;

  /** Un candidat et son score, 0–1. */
  public record Scored(int index, double score) {}

  private final List<Doc> sources;
  private final List<Doc> targets;
  private final Map<String, List<Integer>> blocks = new HashMap<>();

  /** Le même blocage côté sources, construit au premier {@link #rankSources}. */
  private @Nullable Map<String, List<Integer>> sourceBlocks;

  /**
   * Sources et cibles arrivent en mots comptés (tf, voir {@link #addTokens}) et en nombres portés
   * (voir {@link #numbers}). Les deux côtés forment le corpus : un mot banal d'un côté l'est aussi
   * de l'autre.
   */
  private final List<Map<String, Double>> sourceTf;

  public NameMatcher(
      List<Map<String, Double>> sourceTf,
      List<Set<String>> sourceNumbers,
      List<Map<String, Double>> targetTf,
      List<Set<String>> targetNumbers) {
    this.sourceTf = sourceTf;
    Map<String, Integer> df = new HashMap<>();
    for (Map<String, Double> tf : sourceTf) tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
    for (Map<String, Double> tf : targetTf) tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
    int n = sourceTf.size() + targetTf.size();
    Map<String, Double> idf = new HashMap<>();
    df.forEach((t, d) -> idf.put(t, Math.log((1.0 + n) / (1.0 + d)) + 1.0));

    this.targets = new ArrayList<>();
    this.sources = new ArrayList<>();
    for (int i = 0; i < targetTf.size(); i++) {
      targets.add(SoftTfIdf.doc(targetTf.get(i), targetNumbers.get(i), idf));
    }
    for (int i = 0; i < sourceTf.size(); i++) {
      sources.add(SoftTfIdf.doc(sourceTf.get(i), sourceNumbers.get(i), idf));
    }

    block(targetTf, blocks);
  }

  /** Un mot porté par trop de documents ne sert pas à proposer des candidats. */
  private static void block(List<Map<String, Double>> tfs, Map<String, List<Integer>> into) {
    Map<String, Integer> df = new HashMap<>();
    for (Map<String, Double> tf : tfs) tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
    int limit = Math.max(3, (int) Math.ceil(tfs.size() * BLOCKING_MAX_SHARE));
    for (int i = 0; i < tfs.size(); i++) {
      for (String t : tfs.get(i).keySet()) {
        if (df.getOrDefault(t, 0) > limit) continue;
        for (String b : blockingKeys(t)) into.computeIfAbsent(b, k -> new ArrayList<>()).add(i);
      }
    }
  }

  /**
   * Les candidats d'une source, du meilleur au moins bon. Un candidat dont les nombres contredisent
   * ceux de la source n'y figure pas.
   */
  public List<Scored> rank(int source) {
    return rank(source, i -> true);
  }

  /** Les mêmes, parmi les seules cibles que {@code allowed} laisse passer. */
  public List<Scored> rank(int source, IntPredicate allowed) {
    Doc s = sources.get(source);
    return ranked(s, blocks, targets, allowed, t -> similarity(s, t));
  }

  /**
   * Dans l'autre sens : les sources candidates pour une cible, du meilleur au moins bon, avec le
   * même score {@code similarity(source, cible)} et le même veto des nombres. Pour chercher, parmi
   * tout un parc de chemins, celui qui va à un nom donné, sans changer la mesure qu'on a validée.
   */
  public List<Scored> rankSources(int target, IntPredicate allowed) {
    Map<String, List<Integer>> bySource = sourceBlocks;
    if (bySource == null) {
      bySource = new HashMap<>();
      block(sourceTf, bySource);
      sourceBlocks = bySource;
    }
    Doc t = targets.get(target);
    return ranked(t, bySource, sources, allowed, s -> similarity(s, t));
  }

  /**
   * Les candidats que le blocage propose pour {@code probe}, pris dans {@code pool}, sans ceux que
   * {@code allowed} écarte ni ceux dont les nombres contredisent, triés par score décroissant.
   */
  private static List<Scored> ranked(
      Doc probe,
      Map<String, List<Integer>> index,
      List<Doc> pool,
      IntPredicate allowed,
      ToDoubleFunction<Doc> score) {
    List<Scored> out = new ArrayList<>();
    for (int i : candidates(probe, index)) {
      if (!allowed.test(i)) continue;
      Doc other = pool.get(i);
      if (numbersConflict(probe.numbers(), other.numbers())) continue;
      double sc = score.applyAsDouble(other);
      if (sc > 0) out.add(new Scored(i, sc));
    }
    out.sort(Comparator.comparingDouble(Scored::score).reversed());
    return out;
  }

  private static Set<Integer> candidates(Doc probe, Map<String, List<Integer>> index) {
    Set<Integer> candidates = new LinkedHashSet<>();
    for (String t : probe.weights().keySet()) {
      for (String b : blockingKeys(t)) candidates.addAll(index.getOrDefault(b, List.of()));
    }
    return candidates;
  }

  /**
   * Le score d'une paire précise, hors blocage et hors veto : de quoi dire à quel point deux noms
   * qu'on sait appariés se ressemblent.
   */
  public double score(int source, int target) {
    return similarity(sources.get(source), targets.get(target));
  }

  /** Vrai quand la source avait un candidat écarté pour ses seuls nombres. */
  public boolean hasNumberVeto(int source) {
    Doc s = sources.get(source);
    if (s.numbers().isEmpty()) return false;
    for (int i : candidates(s, blocks)) {
      if (numbersConflict(s.numbers(), targets.get(i).numbers())) return true;
    }
    return false;
  }

  /**
   * Le mot lui-même, et ses trois premières lettres pour qu'une faute plus loin dans le mot trouve
   * encore son candidat.
   */
  private static List<String> blockingKeys(String token) {
    return token.length() >= MIN_FUZZY_LENGTH
        ? List.of("=" + token, "^" + token.substring(0, 3))
        : List.of("=" + token);
  }

  // ----------------------------------------------------------------------
  // Des noms aux mots
  // ----------------------------------------------------------------------

  private static final Pattern CAMEL =
      Pattern.compile("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");
  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private static final Pattern NON_LETTERS = Pattern.compile("[^a-z]+");

  /**
   * `MonApi_Service-v2` → [mon, api, service]. Accents retirés, casse ignorée, espace, tiret, point
   * et souligné équivalents, camelCase découpé, nombres sortis, mots d'une lettre écartés — `v` de
   * `v2` compris.
   */
  public static List<String> tokens(@Nullable String s) {
    if (s == null) return List.of();
    String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    plain = CAMEL.matcher(plain).replaceAll(" ").toLowerCase(Locale.ROOT);
    // Les morceaux vides que laisse un séparateur en tête sont écartés avec les mots d'une lettre.
    return NON_LETTERS
        .splitAsStream(plain)
        .filter(t -> t.length() >= 2)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** Les nombres portés par un nom, sans zéros de tête : `app-02` et `app2` portent « 2 ». */
  public static Set<String> numbers(@Nullable String s) {
    Set<String> out = new HashSet<>();
    if (s == null) return out;
    Matcher m = DIGITS.matcher(s);
    while (m.find()) out.add(m.group().replaceFirst("^0+(?=\\d)", ""));
    return out;
  }

  /** Ajoute les mots de {@code s} à {@code tf}, chacun compté {@code weight} fois. */
  public static void addTokens(Map<String, Double> tf, @Nullable String s, double weight) {
    for (String t : tokens(s)) tf.merge(t, weight, Double::sum);
  }
}
