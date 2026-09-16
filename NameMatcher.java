//JAVA 25

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ressemblance de noms entre un chemin GitLab et un projet Sonar, pondérée par
 * la rareté des mots dans le parc (TF-IDF « souple », Cohen et al. 2003).
 *
 * Ce que l'égalité de noms normalisés ratait, et ce que ceci corrige :
 *
 *   mots déplacés ou ajoutés   `client-api` / `api-client`, `ocsin-billing` /
 *                              `billing` — on compare des ensembles de mots,
 *                              pas des chaînes ;
 *   fautes de frappe           `paiement` / `paiment` — deux mots de quatre
 *                              lettres ou plus se rejoignent au-dessus de 0,9
 *                              de Jaro-Winkler ;
 *   mots qui ne disent rien    `api`, `service`, le préfixe de l'entreprise :
 *                              présents partout, ils pèsent presque zéro. Deux
 *                              projets « api » ne se ressemblent plus par leur
 *                              seul point commun. Aucune liste à tenir : c'est
 *                              le parc qui dit ce qui est banal.
 *
 * Les nombres sont sortis des mots avant comparaison (`app2` → `app`), puis
 * servent de veto : si les deux côtés en portent et qu'ils diffèrent, ce ne
 * sont pas les mêmes projets. `sirh-v2` et `sirh` se rejoignent ; `app1` et
 * `app2`, jamais.
 *
 * Rien ici ne décide d'une jointure : le score sort en suggestion, et
 * {@link CrossAudit} ne le compte nulle part.
 */
final class NameMatcher {

    /** Deux mots distincts se rejoignent à partir de cette ressemblance. */
    static final double TOKEN_SIMILARITY = 0.9;

    /** En dessous, un mot est trop court pour qu'une ressemblance veuille dire quelque chose. */
    static final int MIN_FUZZY_LENGTH = 4;

    /** Un mot porté par plus de cette part du parc ne sert pas à proposer des candidats. */
    private static final double BLOCKING_MAX_SHARE = 0.2;

    /** Un nom prêt à comparer : mots pondérés (norme 1) et nombres portés. */
    record Doc(Map<String, Double> weights, Set<String> numbers) { }

    /** Un candidat et son score, 0–1. */
    record Scored(int index, double score) { }

    private final List<Doc> sources;
    private final List<Doc> targets;
    private final Map<String, Double> idf;
    private final Map<String, List<Integer>> blocks = new HashMap<>();

    /**
     * Sources et cibles arrivent en mots comptés (tf, voir {@link #addTokens})
     * et en nombres portés (voir {@link #numbers}). Les deux côtés forment le
     * corpus : un mot banal d'un côté l'est aussi de l'autre.
     */
    NameMatcher(List<Map<String, Double>> sourceTf, List<Set<String>> sourceNumbers,
                List<Map<String, Double>> targetTf, List<Set<String>> targetNumbers) {
        Map<String, Integer> df = new HashMap<>();
        for (Map<String, Double> tf : sourceTf) tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
        for (Map<String, Double> tf : targetTf) tf.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
        int n = sourceTf.size() + targetTf.size();
        this.idf = new HashMap<>();
        df.forEach((t, d) -> idf.put(t, Math.log((1.0 + n) / (1.0 + d)) + 1.0));

        this.targets = new ArrayList<>();
        this.sources = new ArrayList<>();
        for (int i = 0; i < targetTf.size(); i++) {
            targets.add(doc(targetTf.get(i), targetNumbers.get(i)));
        }
        for (int i = 0; i < sourceTf.size(); i++) {
            sources.add(doc(sourceTf.get(i), sourceNumbers.get(i)));
        }

        Map<String, Integer> targetDf = new HashMap<>();
        for (Map<String, Double> tf : targetTf) tf.keySet().forEach(t -> targetDf.merge(t, 1, Integer::sum));
        int limit = Math.max(3, (int) Math.ceil(targetTf.size() * BLOCKING_MAX_SHARE));
        for (int i = 0; i < targetTf.size(); i++) {
            for (String t : targetTf.get(i).keySet()) {
                if (targetDf.get(t) > limit) continue;
                for (String b : blockingKeys(t)) blocks.computeIfAbsent(b, k -> new ArrayList<>()).add(i);
            }
        }
    }

    /**
     * Les candidats d'une source, du meilleur au moins bon. Un candidat dont
     * les nombres contredisent ceux de la source n'y figure pas.
     */
    List<Scored> rank(int source) {
        Doc s = sources.get(source);
        Set<Integer> candidates = new LinkedHashSet<>();
        for (String t : s.weights().keySet()) {
            for (String b : blockingKeys(t)) candidates.addAll(blocks.getOrDefault(b, List.of()));
        }
        List<Scored> out = new ArrayList<>();
        for (int i : candidates) {
            Doc t = targets.get(i);
            if (numbersConflict(s.numbers(), t.numbers())) continue;
            double score = similarity(s, t);
            if (score > 0) out.add(new Scored(i, score));
        }
        out.sort(Comparator.comparingDouble(Scored::score).reversed());
        return out;
    }

    /** Vrai quand la source avait un candidat écarté pour ses seuls nombres. */
    boolean hasNumberVeto(int source) {
        Doc s = sources.get(source);
        if (s.numbers().isEmpty()) return false;
        for (String t : s.weights().keySet()) {
            for (String b : blockingKeys(t)) {
                for (int i : blocks.getOrDefault(b, List.of())) {
                    if (numbersConflict(s.numbers(), targets.get(i).numbers())) return true;
                }
            }
        }
        return false;
    }

    private Doc doc(Map<String, Double> tf, Set<String> numbers) {
        Map<String, Double> w = new HashMap<>();
        double norm = 0;
        for (var e : tf.entrySet()) {
            double v = e.getValue() * idf.getOrDefault(e.getKey(), 1.0);
            w.put(e.getKey(), v);
            norm += v * v;
        }
        double len = Math.sqrt(norm);
        if (len > 0) w.replaceAll((k, v) -> v / len);
        return new Doc(w, numbers);
    }

    /**
     * Pour chaque mot de la source, le mot le plus proche de la cible, s'il
     * l'est assez. Borné à 1 : deux mots proches d'un même mot ne font pas
     * plus qu'une identité.
     */
    static double similarity(Doc a, Doc b) {
        double sum = 0;
        for (var ea : a.weights().entrySet()) {
            double best = 0;
            for (var eb : b.weights().entrySet()) {
                double s = tokenSimilarity(ea.getKey(), eb.getKey());
                if (s > 0) best = Math.max(best, eb.getValue() * s);
            }
            sum += ea.getValue() * best;
        }
        return Math.min(1.0, sum);
    }

    static double tokenSimilarity(String a, String b) {
        if (a.equals(b)) return 1.0;
        if (a.length() < MIN_FUZZY_LENGTH || b.length() < MIN_FUZZY_LENGTH) return 0;
        double jw = jaroWinkler(a, b);
        return jw >= TOKEN_SIMILARITY ? jw : 0;
    }

    static boolean numbersConflict(Set<String> a, Set<String> b) {
        return !a.isEmpty() && !b.isEmpty() && !a.equals(b);
    }

    /**
     * Le mot lui-même, et ses trois premières lettres pour qu'une faute plus
     * loin dans le mot trouve encore son candidat.
     */
    private static List<String> blockingKeys(String token) {
        return token.length() >= MIN_FUZZY_LENGTH
                ? List.of("=" + token, "^" + token.substring(0, 3))
                : List.of("=" + token);
    }

    // ----------------------------------------------------------------------
    // Des noms aux mots
    // ----------------------------------------------------------------------

    private static final Pattern CAMEL = Pattern.compile(
            "(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    /**
     * `MonApi_Service-v2` → [mon, api, service]. Accents retirés, casse
     * ignorée, espace, tiret, point et souligné équivalents, camelCase
     * découpé, nombres sortis, mots d'une lettre écartés — `v` de `v2` compris.
     */
    static List<String> tokens(String s) {
        if (s == null) return List.of();
        String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        plain = CAMEL.matcher(plain).replaceAll(" ").toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String t : plain.split("[^a-z]+")) {
            if (t.length() >= 2) out.add(t);
        }
        return out;
    }

    /** Les nombres portés par un nom, sans zéros de tête : `app-02` et `app2` portent « 2 ». */
    static Set<String> numbers(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        Matcher m = DIGITS.matcher(s);
        while (m.find()) out.add(m.group().replaceFirst("^0+(?=\\d)", ""));
        return out;
    }

    /** Ajoute les mots de {@code s} à {@code tf}, chacun compté {@code weight} fois. */
    static void addTokens(Map<String, Double> tf, String s, double weight) {
        for (String t : tokens(s)) tf.merge(t, weight, Double::sum);
    }

    // ----------------------------------------------------------------------
    // Jaro-Winkler
    // ----------------------------------------------------------------------

    static double jaroWinkler(String a, String b) {
        if (a.equals(b)) return 1.0;
        int window = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1);
        boolean[] ma = new boolean[a.length()];
        boolean[] mb = new boolean[b.length()];
        int matches = 0;
        for (int i = 0; i < a.length(); i++) {
            int from = Math.max(0, i - window), to = Math.min(b.length() - 1, i + window);
            for (int j = from; j <= to; j++) {
                if (!mb[j] && a.charAt(i) == b.charAt(j)) {
                    ma[i] = mb[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) return 0;
        int transpositions = 0;
        for (int i = 0, j = 0; i < a.length(); i++) {
            if (!ma[i]) continue;
            while (!mb[j]) j++;
            if (a.charAt(i) != b.charAt(j)) transpositions++;
            j++;
        }
        double m = matches;
        double jaro = (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
        int prefix = 0;
        while (prefix < Math.min(4, Math.min(a.length(), b.length()))
                && a.charAt(prefix) == b.charAt(prefix)) prefix++;
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
