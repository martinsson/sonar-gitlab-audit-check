package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Match;
import org.jspecify.annotations.Nullable;

/**
 * Un projet GitLab et, s'il en a un, son homologue Sonar. L'appariement et son évaluation vivent
 * dans {@code ProjectMapper}, qui tourne aussi seul : ce qui suit n'est que la lecture qu'en font
 * les constats de {@link CrossAudit}.
 */
record CrossPair(Match m) {

  Csv.Row gl() {
    return m.left();
  }

  Csv.@Nullable Row sq() {
    return m.right();
  }

  /** Une colonne Sonar, vide sans homologue. */
  String sqStr(String column) {
    Csv.Row sq = sq();
    return sq == null ? "" : sq.str(column);
  }

  /** Ni suggestion, ni absence : les seules paires qui alimentent un constat. */
  boolean joined() {
    return m.joined();
  }

  // ----------------------------------------------------------------------
  // La tendance rapportée au travail fait
  // ----------------------------------------------------------------------

  /**
   * Les issues gagnées sur la fenêtre GitLab : la pente Sonar (régression sur les analyses, par
   * jour) multipliée par la durée de la fenêtre.
   *
   * <p>La pente plutôt que (dernière − première valeur) : les deux fenêtres ne coïncident jamais —
   * les analyses tombent quand la CI tourne — et la pente se ramène à n'importe quelle durée. Null
   * dès qu'un morceau manque, et aussi quand GitLab a tronqué sa pagination : un dénominateur
   * sous-compté gonflerait le ratio sans que rien ne le signale.
   */
  static @Nullable Double issuesOverWindow(CrossPair p) {
    if (!p.joined()) return null;
    Double perMonth = num(p.sq(), "violations_pente_mois");
    Double days = num(p.gl(), "fenetre_j");
    if (perMonth == null || days == null || p.gl().flag("lignes_tronquees")) return null;
    return perMonth / 30 * days;
  }

  static @Nullable Double perKlocChanged(CrossPair p) {
    Double issues = issuesOverWindow(p);
    Double lines = num(p.gl(), "lignes_modifiees");
    return issues == null || lines == null || lines == 0 ? null : issues / (lines / 1000);
  }

  static @Nullable Double perCommit(CrossPair p) {
    Double issues = issuesOverWindow(p);
    Double commits = num(p.gl(), "commits_lignes");
    return issues == null || commits == null || commits == 0 ? null : issues / commits;
  }

  /** {@code null} quand la cellule est vide : absent n'est pas zéro. */
  static @Nullable Double num(Csv.@Nullable Row r, String column) {
    return r == null ? null : r.num(column);
  }

  /** La même, ramenée à zéro là où un seuil a besoin d'un nombre. */
  static double nz(Csv.@Nullable Row r, String column) {
    return orZero(num(r, column));
  }

  static double orZero(@Nullable Double d) {
    return d == null ? 0 : d;
  }
}
