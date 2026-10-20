package ch.ge.edg.sonar.gitlab.audit.csv;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Une colonne d'un CSV : son nom dans l'en-tête, et la cellule qu'elle tire de chaque ligne. Une
 * liste de colonnes est le fichier entier — l'en-tête et les lignes ne peuvent plus se désaligner.
 *
 * @param <T> ce qu'une ligne du fichier décrit
 */
public record Column<T>(String name, Function<? super T, String> cell) {

  /** Un fichier par {@link Csv#writer}, l'en-tête puis une ligne par élément de {@code rows}. */
  public static <T> void write(Path file, boolean comma, List<Column<T>> columns, List<T> rows)
      throws IOException {
    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(columns.stream().map(Column::name).toArray(String[]::new));
      for (T row : rows) {
        w.writeNext(columns.stream().map(c -> c.cell().apply(row)).toArray(String[]::new));
      }
    }
  }
}
