package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.project;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The funnel's business rules, run end to end at a fixed instant so that boundary days can be
 * named: the window of {@code --since 90} starts at {@link #WINDOW_START}. Each test builds a small
 * park, runs the command and reads the rule back from inventaire.csv by project path.
 */
abstract class FunnelRulesTestBase extends GitlabActivityAuditTestBase {

  static final OffsetDateTime NOW = OffsetDateTime.of(2030, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC);
  static final OffsetDateTime WINDOW_START = NOW.minusDays(90);

  final List<ObjectNode> park = new ArrayList<>();

  /** An active project of {@code path}, last active now, {@code history} commits in total. */
  ObjectNode add(long id, String path, long history) {
    ObjectNode p = project(id, path);
    p.put("last_activity_at", NOW.toString());
    ((ObjectNode) p.get("statistics")).put("commit_count", history);
    park.add(p);
    return p;
  }

  /** Same, with {@code n} commits in the window shared among {@code authors} people. */
  ObjectNode add(long id, String path, long history, int n, int authors) {
    ObjectNode p = add(id, path, history);
    fake.commits(id, window(id, n, authors));
    return p;
  }

  /** {@code n} commits one day apart from now, by {@code authors} distinct people. */
  static List<ObjectNode> window(long id, int n, int authors) {
    List<ObjectNode> commits = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      commits.add(
          commit(id + "-" + i, "dev" + id + "-" + (i % authors), NOW.minusDays(i % 80).toString()));
    }
    return commits;
  }

  Path csv() {
    return tmp.resolve("inventaire.csv");
  }

  /** Runs the audit on the park at {@link #NOW}, inventory to {@link #csv()}. */
  Cli.Result run(String... extra) {
    return runTo(csv(), extra);
  }

  Cli.Result runTo(Path inventory, String... extra) {
    fake.enterprise(false).group("team", park.toArray(ObjectNode[]::new));
    List<String> args =
        new ArrayList<>(
            List.of(
                "--url",
                fake.server.url(),
                "--token",
                "glpat-test",
                "--color",
                "never",
                "--group",
                "team",
                "--csv",
                inventory.toString()));
    args.addAll(Arrays.asList(extra));
    Cli.Result r =
        Cli.run(
            Clock.fixed(NOW.toInstant(), ZoneOffset.UTC),
            GitlabActivityAudit::new,
            args.toArray(String[]::new));
    assertOk(r);
    return r;
  }

  /** The inventory rows by project path. */
  static Map<String, Csv.Row> rows(Path inventory) {
    try {
      Map<String, Csv.Row> byPath = new LinkedHashMap<>();
      for (Csv.Row row : Csv.read(inventory).rows()) byPath.put(row.str("path"), row);
      return byPath;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Map<String, Csv.Row> rows() {
    return rows(csv());
  }

  Csv.Row row(String path) {
    Csv.Row r = rows().get(path);
    if (r == null) throw new AssertionError("no inventory row for " + path);
    return r;
  }

  /** The paths whose inventory column equals {@code value}. */
  Stream<String> pathsWhere(String column, String value) {
    return rows().values().stream()
        .filter(r -> r.str(column).equals(value))
        .map(r -> r.str("path"));
  }

  List<String> selectedPaths() {
    return pathsWhere("selectionne", "true").toList();
  }

  boolean countedByRest(long id) {
    return !fake.server.requests("/api/v4/projects/" + id + "/repository/commits").isEmpty();
  }
}
