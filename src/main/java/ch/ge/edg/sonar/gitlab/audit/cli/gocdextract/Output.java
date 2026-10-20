package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.dora.Jsonl;
import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les quatre fichiers d'une extraction : trois CSV pour les gens (ou pour un outil avec {@code
 * comma}) et, sauf refus, les runs bruts en JSON Lines, pour appliquer une nouvelle règle sans
 * extraire de nouveau.
 */
final class Output implements AutoCloseable {

  /** Un CSV et son nombre de lignes, en-tête non compris. */
  static final class Table implements AutoCloseable {
    private final CSVWriter writer;
    private int rows;

    Table(Path path, boolean comma, List<String> header) throws IOException {
      writer = Csv.writer(path, comma);
      writer.writeNext(header.toArray(String[]::new));
    }

    void row(String[] cells) {
      writer.writeNext(cells);
      rows++;
    }

    int rows() {
      return rows;
    }

    @Override
    public void close() throws IOException {
      writer.close();
    }
  }

  final Table runs;
  final Table stages;
  final Table commits;
  private final @Nullable Jsonl raw;

  Output(Path dir, boolean comma, boolean keepRaw) throws IOException {
    Files.createDirectories(dir);
    runs = new Table(dir.resolve("gocd-runs.csv"), comma, RunRow.HEADER);
    stages = new Table(dir.resolve("gocd-stages.csv"), comma, StageRows.HEADER);
    commits = new Table(dir.resolve("gocd-commits.csv"), comma, Commit.HEADER);
    raw = keepRaw ? new Jsonl(dir.resolve("gocd-runs.jsonl")) : null;
  }

  void raw(JsonNode run) throws IOException {
    if (raw != null) raw.write(run);
  }

  @Override
  public void close() throws IOException {
    try (runs;
        stages;
        commits) {
      if (raw != null) raw.close();
    }
  }
}
