package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The GitLab objects the GitlabActivityAudit tests describe: projects, commits, merges. Every name
 * and date is invented. Dates in 2099 sit inside any activity window, dates in 2000 outside the
 * default one, so nothing depends on today's date. Served by {@link
 * ch.ge.edg.sonar.gitlab.audit.testing.GitlabFake}.
 */
final class ActivityFixtures {

  static final String FUTURE = "2099-01-01T10:00:00Z";
  static final String PAST = "2000-01-01T10:00:00Z";

  private ActivityFixtures() {}

  /** An active, unarchived project in the namespace of its path, 50 commits in its history. */
  static ObjectNode project(long id, String path) {
    ObjectNode p = JsonNodeFactory.instance.objectNode();
    p.put("id", id);
    p.put("path_with_namespace", path);
    p.put("name", path.substring(path.lastIndexOf('/') + 1).toUpperCase(Locale.ROOT));
    p.putObject("namespace")
        .put("full_path", path.substring(0, Math.max(0, path.lastIndexOf('/'))));
    p.put("default_branch", "main");
    p.put("visibility", "private");
    p.put("created_at", "2020-01-01T00:00:00Z");
    p.put("last_activity_at", FUTURE);
    p.put("archived", false);
    p.putObject("statistics").put("commit_count", 50).put("repository_size", 1000);
    return p;
  }

  /** A single-parent commit on {@code date}. */
  static ObjectNode commit(String sha, String author, String date) {
    ObjectNode c = JsonNodeFactory.instance.objectNode();
    c.put("id", sha);
    c.put("author_email", author + "@example.org");
    c.put("author_name", author);
    c.put("title", "change " + sha);
    c.put("committed_date", date);
    c.putArray("parent_ids").add("p-" + sha);
    return c;
  }

  static ObjectNode merge(ObjectNode commit) {
    commit.putArray("parent_ids").add("p1").add("p2");
    return commit;
  }

  static ObjectNode stats(ObjectNode commit, int additions, int deletions) {
    commit.putObject("stats").put("additions", additions).put("deletions", deletions);
    return commit;
  }

  /** {@code n} commits by {@code author}, one per day from 2099-01-01. */
  static List<ObjectNode> daily(String prefix, String author, int n) {
    List<ObjectNode> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      out.add(commit(prefix + i, author, OffsetDateTime.parse(FUTURE).minusDays(i).toString()));
    }
    return out;
  }
}
