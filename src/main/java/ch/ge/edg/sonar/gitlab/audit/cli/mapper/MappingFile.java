package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt.c;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Replay.Reuse;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Un appariement écrit : une ligne par ligne de gauche, relu au lancement suivant pour garder ce
 * qu'un humain y a décidé.
 *
 * @param path le CSV d'appariement
 * @param comma CSV séparé par des virgules, sans BOM, plutôt que le format Excel
 * @param recomputeAll recalculer aussi les paires exact et derived du fichier ({@code
 *     --reapparier})
 */
public record MappingFile(Path path, boolean comma, boolean recomputeAll) {

  /**
   * Le chemin commun aux deux outils : calculer, rejouer le fichier s'il existe, le réécrire
   * complété — l'ancien gardé en .bak. Sans fichier, l'appariement calculé tel quel.
   */
  public static Result mapAndKeep(
      Spec spec, Csv.Table left, Csv.Table right, @Nullable MappingFile file, PrintStream out)
      throws IOException {
    Result fresh = Mapper.map(spec, left, right);
    return file == null ? fresh : file.keep(fresh, out);
  }

  private Result keep(Result fresh, PrintStream out) throws IOException {
    Spec spec = fresh.spec();
    Result r = fresh;
    Reuse stats = Reuse.NONE;
    boolean existed = Files.exists(path);
    if (existed) {
      Csv.Table prev = Csv.read(path);
      prev.require(
          path,
          "un appariement déjà écrit",
          "gauche_" + spec.leftKey(),
          "droite_" + spec.rightKey(),
          "confiance");
      Replay.Outcome replayed = Replay.run(fresh, prev, recomputeAll);
      r = replayed.result();
      stats = replayed.stats();
      Files.copy(
          path,
          path.resolveSibling(path.getFileName() + ".bak"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    write(r, stats.orphans());
    if (existed) printReuse(stats, out);
    else printNew(r.matches().size(), out);
    return r;
  }

  private void write(Result r, List<String[]> orphans) throws IOException {
    Path dir = path.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
    Spec s = r.spec();
    List<String> header =
        new ArrayList<>(
            List.of("gauche_" + s.leftKey(), "droite_" + s.rightKey(), "methode", "confiance"));
    header.addAll(DetailColumns.header(r));
    try (CSVWriter w = Csv.writer(path, comma)) {
      w.writeNext(header.toArray(String[]::new));
      for (Match p : r.matches()) {
        List<String> row =
            new ArrayList<>(
                List.of(
                    p.left().str(s.leftKey()), p.rightKey(s), p.label(), p.confidence().code()));
        row.addAll(DetailColumns.cells(r, p));
        w.writeNext(row.toArray(String[]::new));
      }
      // Les lignes d'un appariement précédent dont le projet a quitté la
      // gauche : gardées telles quelles. Une décision saisie à la main ne
      // doit pas disparaître parce qu'un audit n'a pas retenu le projet.
      for (String[] o : orphans) {
        String[] row = new String[header.size()];
        Arrays.fill(row, "");
        System.arraycopy(o, 0, row, 0, Math.min(o.length, 4));
        w.writeNext(row);
      }
    }
  }

  private void printNew(int rows, PrintStream out) {
    out.printf("%n  Appariement : %d lignes → %s%n", rows, path.toAbsolutePath());
    out.println(c("    Réutilisé aux prochains lancements. Pour corriger une paire :", DIM));
    out.println(c("    confiance = manuel (et la clé dans droite_…), ou rejete.", DIM));
  }

  private void printReuse(Reuse u, PrintStream out) {
    out.printf(
        "%n  Appariement repris de %s%s%n",
        path.toAbsolutePath(),
        recomputeAll ? c(" (--reapparier : exact et derived recalculés)", YELLOW) : "");
    out.printf(
        "    %d gardées, %d saisies à la main, %d rejetées, "
            + "%d recalculées, %d nouveaux projets%n",
        u.kept(), u.manual(), u.rejected(), u.recomputed(), u.added());
    if (!u.orphans().isEmpty()) {
      out.println(
          c(
              "    %d lignes pour des projets absents de cet audit, conservées."
                  .formatted(u.orphans().size()),
              DIM));
    }
    if (!u.stale().isEmpty()) {
      out.println(
          c(
              "    %d paires vers une clé qui n'existe plus, recalculées :"
                  .formatted(u.stale().size()),
              YELLOW));
      u.stale().stream().limit(10).forEach(x -> out.println(c("      " + x, YELLOW)));
    }
    out.println(c("    Ancienne version : " + path.getFileName() + ".bak", DIM));
  }
}
