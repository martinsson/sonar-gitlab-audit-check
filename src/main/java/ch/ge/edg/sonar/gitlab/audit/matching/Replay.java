package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.Mapping;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Rejoue un appariement déjà écrit sur un appariement fraîchement calculé.
 *
 * <p>Le fichier gagne, sauf là où il n'a rien décidé : * manuel — la paire saisie à la main, même
 * contre le calcul ; * rejete — pas de paire, et le calcul ne doit pas en reproposer ; * exact,
 * derived — la paire d'avant, tant que sa clé existe encore ; sinon recalculée, et signalée ; *
 * suggestion, none — recalculées : une méthode ajoutée depuis (la clé du pom, par exemple) doit
 * pouvoir trouver ce qu'on ne trouvait pas.
 *
 * <p>{@code --reapparier} recalcule aussi exact et derived, jamais manuel ni rejete : ce sont les
 * seules lignes qui ont coûté un humain. Pour les oublier aussi, on supprime le fichier.
 *
 * <p>Chaque ligne de gauche donne un {@link Replayed} ; le bilan se compte dans {@link Reuse}.
 */
final class Replay {

  static final String MANUAL_LABEL = "saisi à la main";
  static final String REJECTED_LABEL = "rejeté à la main";

  /** L'appariement rejoué, et ce que la reprise a fait. */
  record Outcome(Result result, Reuse stats) {}

  /**
   * Ce que la reprise a fait d'une ligne de gauche : la ligne d'appariement, ce qui a été fait, et
   * {@code stale} — la paire d'avant dont la clé de droite n'existe plus, sinon null.
   */
  record Replayed(Step step, Match match, @Nullable String stale) {

    /** Ce qu'une ligne est devenue. */
    enum Step {
      /** Une ligne de gauche que le fichier ne connaissait pas. */
      ADDED,
      /** Une paire exact ou derived gardée : sa clé de droite existe encore. */
      KEPT,
      /** Une paire saisie à la main, tenue même contre le calcul. */
      MANUAL,
      /** Une ligne rejetée à la main : pas de paire, et le calcul ne la reproposera pas. */
      REJECTED,
      /** Une ligne recalculée (suggestion, none, ou exact/derived sous --reapparier). */
      RECOMPUTED,
      /** Une paire saisie à la main dont la clé n'existe plus : signalée, ni tenue ni comptée. */
      STALE_MANUAL
    }
  }

  /** Une ligne du fichier : sa confiance, sa clé de droite, et la ligne qu'elle désigne encore. */
  private record Previous(
      Csv.Row row, Confidence confidence, String key, Csv.@Nullable Row right) {}

  private final Result fresh;
  private final String leftKey;
  private final String rightColumn;
  private final boolean recomputeAll;
  private final Map<String, String> idByLabel = new HashMap<>();

  private Replay(Result fresh, boolean recomputeAll) {
    this.fresh = fresh;
    this.leftKey = fresh.spec().leftKey();
    this.rightColumn = Mapping.right(fresh.spec().rightKey());
    this.recomputeAll = recomputeAll;
    for (KeyMethod m : fresh.available()) idByLabel.put(m.label(), m.id());
    idByLabel.put(Spec.NAMES_LABEL, Spec.NAMES_ID);
  }

  static Outcome run(Result fresh, Csv.Table existing, boolean recomputeAll) {
    return new Replay(fresh, recomputeAll).over(existing);
  }

  private Outcome over(Csv.Table existing) {
    String lk = Mapping.left(leftKey);
    Map<String, Csv.Row> byLeft = new LinkedHashMap<>();
    for (Csv.Row r : existing.rows()) {
      String k = r.str(lk);
      if (!k.isEmpty()) byLeft.put(k, r);
    }
    List<Replayed> rows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Match m : fresh.matches()) {
      String left = m.left().str(leftKey);
      seen.add(left);
      Csv.Row prev = byLeft.get(left);
      rows.add(
          prev == null
              ? new Replayed(Replayed.Step.ADDED, m, null)
              : replay(m, left, previous(prev)));
    }
    List<String[]> orphans = new ArrayList<>();
    for (var e : byLeft.entrySet()) {
      if (seen.contains(e.getKey())) continue;
      Csv.Row r = e.getValue();
      orphans.add(
          new String[] {e.getKey(), r.str(rightColumn), r.str("methode"), r.str("confiance")});
    }
    List<Match> matches = rows.stream().map(Replayed::match).toList();
    return new Outcome(fresh.withMatches(matches), Reuse.of(rows, orphans));
  }

  private Previous previous(Csv.Row prev) {
    String key = prev.str(rightColumn).trim();
    Integer j = key.isEmpty() ? null : fresh.rightIndex().get(key);
    return new Previous(
        prev,
        Confidence.parse(prev.str("confiance")),
        key,
        j == null ? null : fresh.right().rows().get(j));
  }

  private Replayed replay(Match m, String left, Previous p) {
    Confidence c = p.confidence();
    if (c == Confidence.REJETE) {
      Match rejected =
          m.with(
              new Choice(null, null, REJECTED_LABEL, Confidence.REJETE, p.key()),
              m.guess(),
              m.free());
      return new Replayed(Replayed.Step.REJECTED, rejected, null);
    }
    if (c == Confidence.MANUEL) return manual(m, left, p);
    if ((c == Confidence.EXACT || c == Confidence.DERIVED) && !recomputeAll) {
      return sure(m, left, p);
    }
    return new Replayed(Replayed.Step.RECOMPUTED, m, null);
  }

  private Replayed manual(Match m, String left, Previous p) {
    Csv.Row right = p.right();
    if (right == null) {
      // Saisie à la main vers une clé qui n'existe plus : on ne peut
      // pas la tenir, et on ne la remplace pas en silence.
      return new Replayed(
          Replayed.Step.STALE_MANUAL,
          m,
          left + " → " + (p.key().isEmpty() ? "" : p.key() + " ") + "(manuel)");
    }
    Match manual =
        m.with(new Choice(right, "manuel", MANUAL_LABEL, Confidence.MANUEL), m.guess(), m.free());
    return new Replayed(Replayed.Step.MANUAL, manual, null);
  }

  private Replayed sure(Match m, String left, Previous p) {
    Csv.Row right = p.right();
    if (right == null) {
      return new Replayed(Replayed.Step.RECOMPUTED, m, left + " → " + p.key());
    }
    String label = p.row().str("methode");
    Match kept =
        m.with(
            new Choice(right, idByLabel.getOrDefault(label, "fichier"), label, p.confidence()),
            m.guess(),
            m.free());
    return new Replayed(Replayed.Step.KEPT, kept, null);
  }
}
