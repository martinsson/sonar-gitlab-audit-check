package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Builds the GitLab responses {@link GitLabProjectReport} reads, as JSON, and serves them from a
 * {@link FakeHttpServer}. Dates the report measures against now are given as "so long ago",
 * relative to the instant this fixture was built; dates whose wall-clock time matters (authored
 * dates: weekday, hour of the author) are absolute.
 *
 * <p>"Now" is a fixed instant, shared with the report through {@link #clock()}: the report counts
 * calendar days, so a fixture relative to the real clock would count one more or one less day
 * depending on the hour of the run.
 */
final class GitLabProjectReportFakeGitlab {

  static final String API = "/api/v4/projects/";

  static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

  final LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

  /** The clock the report under test reads: stopped at {@link #NOW}. */
  Clock clock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }

  /** An instant {@code days} days and {@code hours} hours ago, as GitLab writes it (UTC, Z). */
  String ago(int days, int hours) {
    return now.minusDays(days).minusHours(hours).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        + "Z";
  }

  /** The calendar date (UTC) the report prints for {@link #ago}. */
  String dateAgo(int days, int hours) {
    return now.minusDays(days).minusHours(hours).toLocalDate().toString();
  }

  static String json(Object o) {
    try {
      return Gitlab.MAPPER.writeValueAsString(o);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Alternating key/value pairs, nulls kept (GitLab sends them). */
  static Map<String, Object> obj(@Nullable Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  static Map<String, Object> user(String username) {
    return obj("id", (long) username.hashCode(), "username", username, "name", username);
  }

  Map<String, Object> project(long id, String path, String branch, boolean archived) {
    return obj(
        "id",
        id,
        "name",
        path.substring(path.lastIndexOf('/') + 1),
        "path_with_namespace",
        path,
        "default_branch",
        branch,
        "visibility",
        "private",
        "archived",
        archived,
        "created_at",
        ago(400, 6),
        "last_activity_at",
        ago(3, 6),
        "statistics",
        obj("commit_count", 1234, "repository_size", 5L * 1024 * 1024 + 300_000));
  }

  /** A commit; {@code stats} null leaves the field out, as when the diff is unreadable. */
  static Map<String, Object> commit(
      @Nullable String name,
      @Nullable String email,
      @Nullable String authored,
      @Nullable String committed,
      int @Nullable [] stats) {
    Map<String, Object> c =
        obj(
            "id",
            "c" + Integer.toHexString((name + authored + committed).hashCode()),
            "short_id",
            "c0ffee",
            "title",
            "work",
            "author_name",
            name,
            "author_email",
            email,
            "authored_date",
            authored,
            "committed_date",
            committed,
            "parent_ids",
            List.of("p1"));
    if (stats != null) {
      c.put(
          "stats", obj("additions", stats[0], "deletions", stats[1], "total", stats[0] + stats[1]));
    }
    return c;
  }

  static Map<String, Object> merge(Map<String, Object> commit) {
    commit.put("parent_ids", List.of("p1", "p2"));
    return commit;
  }

  static Map<String, Object> mr(long iid, String state, String author, @Nullable String created) {
    return obj(
        "iid",
        iid,
        "state",
        state,
        "title",
        "change " + iid,
        "created_at",
        created,
        "author",
        user(author),
        "user_notes_count",
        0,
        "squash",
        false);
  }

  static Map<String, Object> note(
      @Nullable String author, @Nullable String created, boolean system) {
    return obj(
        "id",
        1L,
        "system",
        system,
        "created_at",
        created,
        "author",
        author == null ? null : user(author));
  }

  /** Serves {@code items} by pages of {@code perPage}, with X-Next-Page as GitLab does. */
  static Response paged(FakeHttpServer.Request req, List<?> items, int perPage) {
    int page = Integer.parseInt(req.param("page"));
    int from = Math.min(items.size(), (page - 1) * perPage);
    int to = Math.min(items.size(), from + perPage);
    Response r = Response.json(json(new ArrayList<>(items.subList(from, to))));
    return to < items.size() ? r.withHeader("X-Next-Page", String.valueOf(page + 1)) : r;
  }
}
