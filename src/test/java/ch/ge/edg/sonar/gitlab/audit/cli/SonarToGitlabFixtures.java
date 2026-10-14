package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Choice;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Fmt;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Match;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Small tables and a one-line rendering of SonarToGitlab results, for its unit tests. */
final class SonarToGitlabFixtures {

  static final String SONAR = "key,name,alm,alm_repository,analysisDate,ncloc";

  private SonarToGitlabFixtures() {}

  static Csv.Table sonar(Path dir, String... rows) throws IOException {
    return CrossAuditFixtures.table(dir, "sonar.csv", SONAR, rows);
  }

  static Csv.Table inventory(Path dir, String... rows) throws IOException {
    return CrossAuditFixtures.table(
        dir, "inventaire.csv", CrossAuditFixtures.INVENTORY_HEADER, rows);
  }

  static Match match(Csv.Row gitlab, Csv.@Nullable Row sonar, Confidence c, String label) {
    return new Match(0, gitlab, new Choice(sonar, "m", label, c), Map.of(), null, null);
  }

  static SonarToGitlab.Result map(Csv.Table sonar, Csv.Table inventory, List<Match> cross) {
    return SonarToGitlab.map(
        sonar,
        inventory,
        cross,
        new SonarToGitlab.Rejections(Set.of(), Set.of()),
        new SonarToGitlab.NameThresholds(0.5, 0.1));
  }

  /** key → path | method | confidence | score | candidates | reason, one line per pair. */
  static String describe(SonarToGitlab.Result r) {
    return r.pairs().stream()
        .map(
            p ->
                String.join(
                    " | ",
                    p.key() + " -> " + p.path(),
                    p.method(),
                    p.confidence().code(),
                    p.score() == null ? "-" : Fmt.score(p.score()),
                    p.candidates(),
                    p.reason()))
        .collect(Collectors.joining("\n"));
  }

  static String checks(SonarToGitlab.Result r) {
    return r.checks().stream()
        .map(c -> c.method() + " = " + c.agrees())
        .collect(Collectors.joining("\n"));
  }
}
