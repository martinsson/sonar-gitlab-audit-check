package ch.ge.edg.sonar.gitlab.audit.matching.report;

import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.DIM;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.MappingFile;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import ch.ge.edg.sonar.gitlab.audit.matching.Reuse;
import ch.ge.edg.sonar.gitlab.audit.matching.Spec;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;

/** Ce que le fichier d'appariement est devenu, sur la console : écrit, ou repris. */
public final class MappingFilePrinter {

  private final Console out;

  public MappingFilePrinter(Console out) {
    this.out = out;
  }

  /**
   * Le chemin commun aux deux outils : calculer, rejouer le fichier s'il existe, le réécrire
   * complété, et dire ce qu'il est devenu.
   */
  public static Result mapAndKeep(
      Spec spec, Csv.Table left, Csv.Table right, MappingFile file, Console out)
      throws IOException {
    MappingFile.Kept kept = MappingFile.mapAndKeep(spec, left, right, file);
    new MappingFilePrinter(out).print(kept);
    return kept.result();
  }

  public void print(MappingFile.Kept kept) {
    if (kept.reused()) printReuse(kept.file(), kept.stats());
    else printNew(kept.file(), kept.result().matches().size());
  }

  private void printNew(MappingFile file, int rows) {
    out.print(
        Text.format("%n  Appariement : %d lignes → %s%n", rows, file.path().toAbsolutePath()));
    out.println(
        out.color("    Réutilisé aux prochains lancements. Pour corriger une paire :", DIM));
    out.println(out.color("    confiance = manuel (et la clé dans droite_…), ou rejete.", DIM));
  }

  private void printReuse(MappingFile file, Reuse u) {
    out.print(
        Text.format(
            "%n  Appariement repris de %s%s%n",
            file.path().toAbsolutePath(),
            file.recomputeAll()
                ? out.color(" (--reapparier : exact et derived recalculés)", YELLOW)
                : ""));
    out.print(
        Text.format(
            "    %d gardées, %d saisies à la main, %d rejetées, "
                + "%d recalculées, %d nouveaux projets%n",
            u.kept(), u.manual(), u.rejected(), u.recomputed(), u.added()));
    if (!u.orphans().isEmpty()) {
      out.println(
          out.color(
              Text.format(
                  "    %d lignes pour des projets absents de cet audit, conservées.",
                  u.orphans().size()),
              DIM));
    }
    if (!u.stale().isEmpty()) {
      out.println(
          out.color(
              Text.format(
                  "    %d paires vers une clé qui n'existe plus, recalculées :", u.stale().size()),
              YELLOW));
      u.stale().stream().limit(10).forEach(x -> out.println(out.color("      " + x, YELLOW)));
    }
    out.println(out.color("    Ancienne version : " + file.path().getFileName() + ".bak", DIM));
  }
}
