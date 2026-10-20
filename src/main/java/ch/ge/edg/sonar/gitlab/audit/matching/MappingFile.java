package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.Mapping;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
   * Ce que le fichier est devenu.
   *
   * @param result l'appariement, repris du fichier s'il existait
   * @param reused le fichier existait et a été rejoué ; sinon il vient d'être écrit
   * @param stats ce que la reprise a fait ({@link Reuse#NONE} sans reprise)
   */
  public record Kept(MappingFile file, Result result, boolean reused, Reuse stats) {}

  /**
   * Le chemin commun aux deux outils : calculer, rejouer le fichier s'il existe, le réécrire
   * complété — l'ancien gardé en .bak.
   */
  public static Kept mapAndKeep(Spec spec, Csv.Table left, Csv.Table right, MappingFile file)
      throws IOException {
    return file.keep(Mapper.map(spec, left, right));
  }

  /** Sans fichier, l'appariement calculé tel quel. */
  public static Result map(Spec spec, Csv.Table left, Csv.Table right) {
    return Mapper.map(spec, left, right);
  }

  private Kept keep(Result fresh) throws IOException {
    Spec spec = fresh.spec();
    Result r = fresh;
    Reuse stats = Reuse.NONE;
    boolean existed = Files.exists(path);
    if (existed) {
      Csv.Table prev = Csv.read(path);
      prev.require(
          path,
          "un appariement déjà écrit",
          Mapping.left(spec.leftKey()),
          Mapping.right(spec.rightKey()),
          Mapping.CONFIDENCE);
      Replay.Outcome replayed = Replay.run(fresh, prev, recomputeAll);
      r = replayed.result();
      stats = replayed.stats();
      Files.copy(
          path,
          path.resolveSibling(path.getFileName() + ".bak"),
          StandardCopyOption.REPLACE_EXISTING);
    }
    write(r, stats.orphans());
    return new Kept(this, r, existed, stats);
  }

  private void write(Result r, List<String[]> orphans) throws IOException {
    Path dir = path.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
    Spec s = r.spec();
    List<String> header =
        new ArrayList<>(
            List.of(
                Mapping.left(s.leftKey()),
                Mapping.right(s.rightKey()),
                Mapping.METHOD,
                Mapping.CONFIDENCE));
    header.addAll(DetailColumns.header(r));
    try (CSVWriter w = Csv.writer(path, comma)) {
      w.writeNext(header.toArray(String[]::new));
      for (Match p : r.matches()) {
        List<String> row =
            new ArrayList<>(
                List.of(
                    p.left().str(s.leftKey()), p.writtenKey(s), p.label(), p.confidence().code()));
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
}
