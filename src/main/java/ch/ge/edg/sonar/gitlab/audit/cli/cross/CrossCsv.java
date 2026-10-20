package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import static ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossReport.DIM;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.Crossing;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabPractices;
import ch.ge.edg.sonar.gitlab.audit.matching.DetailColumns;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** croisement.csv : une ligne par projet GitLab, appariée ou non, les deux côtés préfixés. */
final class CrossCsv {

  private CrossCsv() {}

  static void write(Result mapping, List<CrossPair> pairs, Path file, boolean comma, Console out)
      throws IOException {
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);

    try (CSVWriter w = Csv.writer(file, comma)) {
      w.writeNext(header(mapping).toArray(String[]::new));
      for (CrossPair p : pairs) w.writeNext(row(mapping, p));
    }
    out.print(
        Text.format("%n  Croisement : %d lignes → %s%n", pairs.size(), file.toAbsolutePath()));
    out.println(out.color(Csv.openingHint(file, comma), DIM));
    out.println(
        out.color(
            "    Une ligne par projet GitLab, appariée ou non. " + "Filtrer sur confiance", DIM));
    out.println(
        out.color(
            "    = suggestion donne la liste des rapprochements " + "qu'un humain doit", DIM));
    out.println(
        out.color("    confirmer ; ils ne comptent dans aucun des constats " + "ci-dessus.", DIM));
  }

  private static List<String> header(Result mapping) {
    return Crossing.header(DetailColumns.header(mapping));
  }

  private static String[] row(Result mapping, CrossPair p) {
    List<String> row =
        new ArrayList<>(
            List.of(p.gl().str(GitlabPractices.PATH), p.m().label(), p.m().confidence().code()));
    row.addAll(DetailColumns.cells(mapping, p.m()));
    GitlabPractices.CROSSED.forEach(col -> row.add(p.gl().str(col)));
    Crossing.SONAR_SIDE.forEach(col -> row.add(p.sqStr(col)));
    row.add(Text.decimal(CrossPair.perKlocChanged(p)));
    row.add(Text.decimal(CrossPair.perCommit(p)));
    return row.toArray(String[]::new);
  }
}
