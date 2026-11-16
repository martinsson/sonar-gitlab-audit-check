package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.gitlab.RepoPath;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Le projet GitLab et ses merge requests fusionnées sur la branche par défaut pendant la période,
 * chacune avec ses commits : leur plus ancienne date d'auteur ouvre le délai, leurs SHA la relient
 * au déploiement qui les a portés.
 */
final class GitlabReader {

  /** Appels de commits de merge request en vol à la fois. */
  static final int CONCURRENCY = 4;

  /**
   * Le projet tel que GitLab le décrit.
   *
   * @param namespace le full_path du groupe, que Middleware garde comme org_name
   */
  record Project(
      String id, String path, String name, String namespace, String webUrl, String defaultBranch) {}

  private final Gitlab gitlab;
  private final PrintStream err;

  GitlabReader(Gitlab gitlab, PrintStream err) {
    this.gitlab = gitlab;
    this.err = err;
  }

  /** Le projet désigné par « groupe/projet », son URL ou son id ; introuvable, rien n'a de sens. */
  Project project(String ref) throws Stop {
    String path = "projects/" + Gitlab.enc(RepoPath.of(ref));
    RestResponse r = Stop.unlessRefused(gitlab.get(path, Map.of()), gitlab.base);
    if (!r.ok()) throw new Stop("/api/v4/" + path + " : " + r.statusLine());
    JsonNode p = r.tree();
    return new Project(
        p.path("id").asText(),
        p.path("path_with_namespace").asText(),
        p.path("name").asText(),
        p.path("namespace").path("full_path").asText(),
        p.path("web_url").asText(),
        p.path("default_branch").asText("main"));
  }

  /** Les merge requests fusionnées depuis {@code from}, au plus {@code max}, les plus récentes. */
  List<Snapshot.MergeRequest> mergedSince(Project p, Instant from, int max) {
    Map<String, String> params =
        Map.of(
            "state", "merged",
            "target_branch", p.defaultBranch(),
            "updated_after", from.toString(),
            "order_by", "updated_at",
            "sort", "desc");
    List<JsonNode> merged = new ArrayList<>();
    for (JsonNode mr : gitlab.paged("projects/" + p.id() + "/merge_requests", params)) {
      Instant at = Gitlab.instant(mr.path("merged_at"));
      if (at != null && !at.isBefore(from)) merged.add(mr);
    }
    merged.sort(
        Comparator.comparing(mr -> mr.path("merged_at").asText(), Comparator.reverseOrder()));
    if (merged.size() > max) {
      err.println(
          merged.size()
              + " merge requests fusionnées : seules les "
              + max
              + " plus récentes sont lues");
      merged = merged.subList(0, max);
    }
    List<Snapshot.MergeRequest> out =
        new ArrayList<>(gitlab.map(merged, CONCURRENCY, mr -> withCommits(p, mr)));
    out.sort(Comparator.comparing(Snapshot.MergeRequest::mergedAt));
    return out;
  }

  private Snapshot.MergeRequest withCommits(Project p, JsonNode mr) {
    int iid = mr.path("iid").asInt();
    List<JsonNode> commits =
        gitlab.paged("projects/" + p.id() + "/merge_requests/" + iid + "/commits", Map.of());
    Set<String> shas = new LinkedHashSet<>();
    Instant first = null;
    for (JsonNode c : commits) {
      shas.add(c.path("id").asText());
      Instant at = Gitlab.instant(c.path("authored_date"));
      if (at != null && (first == null || at.isBefore(first))) first = at;
    }
    for (String field : List.of("merge_commit_sha", "squash_commit_sha", "sha")) {
      String sha = mr.path(field).asText("");
      if (!sha.isEmpty() && !"null".equals(sha)) shas.add(sha);
    }
    return new Snapshot.MergeRequest(
        iid,
        mr.path("title").asText(),
        mr.path("author").path("username").asText(),
        required(Gitlab.instant(mr.path("created_at"))),
        required(Gitlab.instant(mr.path("merged_at"))),
        first,
        List.copyOf(shas),
        mr.path("web_url").asText());
  }

  /**
   * Les commits de la branche par défaut depuis {@code from}, du plus récent au plus ancien, comme
   * GitLab les liste.
   */
  List<Snapshot.MainCommit> mainCommits(Project p, Instant from) {
    Map<String, String> params = Map.of("ref_name", p.defaultBranch(), "since", from.toString());
    List<Snapshot.MainCommit> out = new ArrayList<>();
    for (JsonNode c : gitlab.paged("projects/" + p.id() + "/repository/commits", params)) {
      out.add(
          new Snapshot.MainCommit(
              c.path("id").asText(),
              c.path("title").asText(""),
              Gitlab.instant(c.path("authored_date")),
              Gitlab.instant(c.path("committed_date"))));
    }
    return out;
  }

  /**
   * Les pushs sur la branche par défaut depuis la veille de {@code from} : GitLab ne filtre les
   * événements qu'au jour près, et seulement après une date.
   */
  List<Snapshot.Push> pushes(Project p, Instant from) {
    String after = from.minus(Duration.ofDays(1)).toString().substring(0, 10);
    Map<String, String> params = Map.of("action", "pushed", "after", after);
    List<Snapshot.Push> out = new ArrayList<>();
    for (JsonNode e : gitlab.paged("projects/" + p.id() + "/events", params)) {
      JsonNode push = e.path("push_data");
      Instant at = Gitlab.instant(e.path("created_at"));
      String to = push.path("commit_to").asText("");
      boolean main =
          "branch".equals(push.path("ref_type").asText())
              && p.defaultBranch().equals(push.path("ref").asText());
      if (at == null || !main || to.isEmpty()) continue;
      String fromSha = push.path("commit_from").asText("");
      out.add(
          new Snapshot.Push(
              at, fromSha.isEmpty() ? null : fromSha, to, push.path("commit_count").asInt()));
    }
    return out;
  }

  /** Une date que GitLab donne toujours ; filtrée en amont pour merged_at. */
  private static Instant required(@Nullable Instant at) {
    return at == null ? Instant.EPOCH : at;
  }
}
