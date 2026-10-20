package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossPair.num;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossPair.nz;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossPair.orZero;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossPair.perKlocChanged;
import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossReport.c;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Ce que ni l'un ni l'autre ne pouvait dire seul : les constats de {@link CrossAudit}. */
final class CrossFindings {

  private final PrintStream out;

  CrossFindings(PrintStream out) {
    this.out = out;
  }

  /**
   * @param wideJoined les dépôts qu'un projet Sonar désigne sûrement dans la passe sur tout GitLab
   *     : un dépôt resté sans paire dans la sélection peut y avoir trouvé la sienne.
   */
  void print(List<CrossPair> pairs, Set<String> wideJoined) {
    CrossReport.title(out, "2. Ce qu'aucun des deux rapports ne pouvait dire seul");
    List<CrossPair> ok = pairs.stream().filter(CrossPair::joined).toList();
    List<CrossPair> unpaired =
        pairs.stream()
            .filter(p -> !p.joined() && !wideJoined.contains(p.gl().str("path")))
            .toList();
    activeUnanalysed(unpaired);
    ciWithoutSonar(unpaired);
    unreviewedDebt(ok);
    naked(ok);
    carrying(ok);
    unwired(ok);
    degrading(ok);
  }

  /**
   * 1. Vivant dans GitLab, absent de Sonar. La distinction que le seul inventaire Sonar ne permet
   * pas : un dépôt absent y est indiscernable d'un dépôt mort, et c'est l'inverse qui est
   * intéressant.
   */
  private void activeUnanalysed(List<CrossPair> unpaired) {
    List<CrossPair> active =
        unpaired.stream()
            .filter(p -> nz(p.gl(), "commits_window") >= 20)
            .sorted(
                Comparator.comparingDouble((CrossPair p) -> nz(p.gl(), "commits_window"))
                    .reversed())
            .toList();
    finding(
        "Actifs dans GitLab, absents de SonarQube",
        "Vivants et non analysés. Séparés enfin des dépôts morts, qu'un "
            + "inventaire\n    Sonar seul confond avec eux.",
        active,
        p -> "%s — %s commits".formatted(p.gl().str("path"), p.gl().str("commits_window")));
  }

  /**
   * 2. La CI dit qu'elle lance Sonar, et Sonar n'a rien. Vérifiable en une minute par n'importe
   * qui, ce qui en fait le constat le plus utile du lot : soit le job échoue en silence, soit il
   * pousse ailleurs.
   */
  private void ciWithoutSonar(List<CrossPair> unpaired) {
    List<CrossPair> claimed = unpaired.stream().filter(p -> p.gl().flag("ci_sonar")).toList();
    finding(
        "CI configurée pour Sonar, sans projet Sonar apparié",
        "Le pipeline dit lancer une analyse ; rien ne la reçoit sous une clé\n"
            + "    qu'on sache rattacher. Job en échec silencieux, ou clé "
            + "qui ne correspond\n    à rien. Constat concret, vérifiable projet par projet.",
        claimed,
        p ->
            p.gl().str("path")
                + (p.gl().str("cle_sonar").isEmpty() ? "" : " → " + p.gl().str("cle_sonar")));
  }

  /**
   * 3. Dette qui se crée, revue qui ne l'arrête pas. Sonar dit que la dette arrive ; GitLab dit que
   * rien ne la relit. Ni l'un ni l'autre ne peut l'affirmer seul, et c'est le signal « cette équipe
   * a besoin d'aide ».
   */
  private void unreviewedDebt(List<CrossPair> ok) {
    List<CrossPair> unreviewed =
        ok.stream()
            .filter(p -> nz(p.sq(), "new_violations") > 0)
            .filter(CrossFindings::busyWithoutReview)
            .sorted(
                Comparator.comparingDouble((CrossPair p) -> nz(p.sq(), "new_violations"))
                    .reversed())
            .toList();
    finding(
        "Dette créée sur du code que personne n'a relu",
        "Sonar voit des violations sur le code neuf ; GitLab voit ce code "
            + "atteindre\n    la branche par défaut sans une seule MR fusionnée.",
        unreviewed,
        p ->
            "%s — %s violations neuves, %s commits, 0 MR"
                .formatted(
                    p.gl().str("path"), p.sqStr("new_violations"), p.gl().str("commits_window")));
  }

  /**
   * 4. Sans test et sans revue : les deux filets à la fois. Aucun des deux rapports ne peut le
   * dire, et c'est la seule intersection qui justifie d'intervenir avant que quelque chose casse.
   */
  private void naked(List<CrossPair> ok) {
    List<CrossPair> naked =
        ok.stream()
            .filter(p -> num(p.sq(), "coverage") != null && nz(p.sq(), "coverage") < 20)
            .filter(CrossFindings::busyWithoutReview)
            .sorted(
                Comparator.comparingDouble((CrossPair p) -> nz(p.gl(), "commits_window"))
                    .reversed())
            .toList();
    finding(
        "Ni tests ni revue, sur du code qui bouge",
        "Couverture sous 20 %, aucune MR fusionnée, activité soutenue. Les "
            + "deux\n    filets manquent en même temps.",
        naked,
        p ->
            "%s — couverture %s %%, %s commits"
                .formatted(p.gl().str("path"), p.sqStr("coverage"), p.gl().str("commits_window")));
  }

  /** Au moins 20 commits sur la fenêtre, et aucune MR fusionnée. */
  private static boolean busyWithoutReview(CrossPair p) {
    return nz(p.gl(), "commits_window") >= 20 && nz(p.gl(), "mr_fusionnees") == 0;
  }

  /**
   * 5. Dette portée sans être créée. Le confirmer par une source indépendante, plutôt que de le
   * déduire d'une date d'analyse ancienne — qui ne dit rien de ce qui se passe dans le dépôt.
   */
  private void carrying(List<CrossPair> ok) {
    List<CrossPair> carrying =
        ok.stream()
            .filter(p -> nz(p.sq(), "sqale_index") > 0)
            .filter(p -> num(p.gl(), "commits_window") != null && nz(p.gl(), "commits_window") == 0)
            .sorted(
                Comparator.comparingDouble((CrossPair p) -> nz(p.sq(), "sqale_index")).reversed())
            .toList();
    finding(
        "Dette portée, pas créée",
        "De la dette au bilan, zéro commit sur la fenêtre. Confirmé par le "
            + "dépôt\n    lui-même, pas déduit d'une date d'analyse ancienne.",
        carrying,
        p ->
            "%s — %s min de dette, 0 commit".formatted(p.gl().str("path"), p.sqStr("sqale_index")));
  }

  /**
   * 6. Le cas qui ne se voit que depuis les deux côtés : des tests existent, et SonarQube n'en sait
   * rien. Sans le compte de tests, « couverture 0 » et « pas de tests » sont la même cellule vide.
   */
  private void unwired(List<CrossPair> ok) {
    List<CrossPair> unwired =
        ok.stream()
            .filter(p -> nz(p.sq(), "tests") > 0)
            .filter(p -> num(p.sq(), "coverage") != null && nz(p.sq(), "coverage") == 0)
            .toList();
    finding(
        "Des tests tournent, la couverture n'arrive pas",
        "Tests comptés par SonarQube, couverture à zéro : le rapport de "
            + "couverture\n    n'est pas branché. Problème de tuyauterie CI, pas "
            + "d'ingénierie — et les\n    deux se ressemblent tant qu'on ne "
            + "regarde que le pourcentage.",
        unwired,
        p -> "%s — %s tests, couverture 0 %%".formatted(p.gl().str("path"), p.sqStr("tests")));
  }

  /**
   * 7. La dérivée rapportée au travail fait. La pente seule confond « le projet grossit » et «
   * chaque modification abîme » ; divisée par les lignes réellement modifiées, elle dit ce que
   * coûte une modification.
   */
  private void degrading(List<CrossPair> ok) {
    List<CrossPair> degrading =
        ok.stream()
            .filter(p -> orZero(perKlocChanged(p)) > 0)
            .filter(p -> nz(p.sq(), "tendance_analyses") >= 5)
            .filter(p -> nz(p.gl(), "lignes_modifiees") >= 500)
            .sorted(
                Comparator.comparingDouble((CrossPair p) -> orZero(perKlocChanged(p))).reversed())
            .toList();
    finding(
        "Chaque modification ajoute des issues",
        "Pente des issues Sonar sur la fenêtre, rapportée aux lignes modifiées "
            + "dans\n    GitLab. Au moins 5 analyses et 500 lignes, sinon "
            + "c'est du bruit.",
        degrading,
        p ->
            "%s — %+.1f issues / kLOC modifié, %s lignes"
                .formatted(p.gl().str("path"), perKlocChanged(p), p.gl().str("lignes_modifiees")));
  }

  /** Un constat, son effectif, sa lecture, et au plus cinq exemples nommés. */
  private void finding(
      String title, String reading, List<CrossPair> examples, Function<CrossPair, String> line) {
    int n = examples.size();
    out.printf(
        "%n  %s : %s%n",
        title, c(String.valueOf(n), n > 0 ? CrossReport.YELLOW : CrossReport.GREEN));
    if (n == 0) return;
    out.println(c("    " + reading, CrossReport.DIM));
    examples.stream().limit(5).forEach(p -> out.println("      " + line.apply(p)));
    if (n > 5) out.printf("      … et %d autres, dans le CSV.%n", n - 5);
  }
}
