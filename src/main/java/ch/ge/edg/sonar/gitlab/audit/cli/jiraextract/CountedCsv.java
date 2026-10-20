package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Un CSV de sortie qui compte ses lignes, pour le bilan affiché à la fin. Une cellule tient sur une
 * ligne : les retours à la ligne d'une description deviennent des espaces, ce qui garde un ticket
 * par ligne pour qui lit le fichier avec grep.
 */
final class CountedCsv implements AutoCloseable {

  private final Path path;
  private final CSVWriter out;
  private int rows;

  CountedCsv(Path path, boolean comma, List<String> header) throws IOException {
    this.path = path;
    this.out = Csv.writer(path, comma);
    out.writeNext(flat(header), false);
  }

  void row(List<String> cells) {
    out.writeNext(flat(cells), false);
    rows++;
  }

  Path path() {
    return path;
  }

  int rows() {
    return rows;
  }

  static String[] flat(List<String> cells) {
    return cells.stream().map(c -> c.replace('\r', ' ').replace('\n', ' ')).toArray(String[]::new);
  }

  @Override
  public void close() throws IOException {
    out.close();
  }
}
