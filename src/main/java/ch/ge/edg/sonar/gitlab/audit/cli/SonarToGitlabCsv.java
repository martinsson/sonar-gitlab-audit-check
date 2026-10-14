package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Pair;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarToGitlab.Result;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** sonar-gitlab.csv : une ligne par projet Sonar, son dépôt GitLab s'il en a un. */
final class SonarToGitlabCsv {

  static final List<String> HEADER =
      List.of(
          "sonar_key",
          "sonar_name",
          "gitlab_path",
          "gitlab_id",
          "methode",
          "confiance",
          "score",
          "autres_candidats",
          "raison",
          "cles_sonar_sur_ce_projet",
          "dans_selection",
          "gl_commits_window",
          "gl_last_activity_at",
          "gl_exclu",
          "sq_analysisDate",
          "sq_ncloc");

  private SonarToGitlabCsv() {}

  static void write(Result r, Path file, boolean comma) throws IOException {
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
    Map<String, Integer> perPath = new HashMap<>();
    for (Pair p : r.pairs()) if (p.gitlab() != null) perPath.merge(p.path(), 1, Integer::sum);
    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(HEADER.toArray(String[]::new));
      for (Pair p : r.pairs()) w.writeNext(cells(p, perPath));
    }
  }

  private static String[] cells(Pair p, Map<String, Integer> perPath) {
    Csv.Row g = p.gitlab();
    Double score = p.score();
    return new String[] {
      p.key(),
      p.sonar().str("name"),
      p.path(),
      cell(g, "id"),
      p.method(),
      p.confidence().code(),
      score == null ? "" : ProjectMapper.score(score),
      p.candidates(),
      p.reason(),
      g == null ? "" : String.valueOf(perPath.get(p.path())),
      g == null ? "" : g.flag("selectionne") ? "oui" : "non",
      cell(g, "commits_window"),
      cell(g, "last_activity_at"),
      cell(g, "exclu"),
      p.sonar().str("analysisDate"),
      p.sonar().str("ncloc")
    };
  }

  private static String cell(Csv.@Nullable Row g, String column) {
    return g == null ? "" : g.str(column);
  }
}
