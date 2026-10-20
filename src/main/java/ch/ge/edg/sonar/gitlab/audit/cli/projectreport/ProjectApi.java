package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Les appels GitLab du rapport, un par question posée au projet. */
final class ProjectApi {

  private final Gitlab gitlab;
  private final Settings settings;

  ProjectApi(Gitlab gitlab, Settings settings) {
    this.gitlab = gitlab;
    this.settings = settings;
  }

  int calls() {
    return gitlab.calls();
  }

  Gitlab.Response project(String projectPath) {
    return gitlab.get("projects/" + enc(projectPath), params("statistics", "true"));
  }

  /**
   * Les commits sont lus sur la seule branche par défaut. C'est délibéré : `all=true` ramène aussi
   * les branches de travail abandonnées et les commits qui n'ont jamais été fusionnés, ce qui
   * gonfle la cadence d'un travail qui n'a jamais atterri.
   */
  Commits commits(Project p, LocalDateTime since) {
    String defaultBranch = p.defaultBranch();
    if (defaultBranch == null || defaultBranch.isBlank())
      return new Commits(List.of(), List.of(), false, "pas de branche par défaut");

    Gitlab.Page<Commit> f =
        gitlab.paged(
            "projects/" + p.id() + "/repository/commits",
            params(
                "ref_name",
                defaultBranch,
                "since",
                since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "with_stats",
                "true"),
            Commit.class,
            settings.maxCommits());

    List<Commit> all = f.items();
    List<Commit> humans =
        settings.includeBots() ? all : all.stream().filter(x -> !isBot(x)).toList();
    List<Commit> botCommits = all.stream().filter(this::isBot).toList();
    return new Commits(humans, botCommits, f.truncated(), f.error());
  }

  Gitlab.Page<Contributor> contributors(Project p) {
    return gitlab.paged(
        "projects/" + p.id() + "/repository/contributors",
        params("order_by", "commits", "sort", "desc"),
        Contributor.class,
        500);
  }

  Gitlab.Page<MergeRequest> mergeRequests(Project p, LocalDateTime since) {
    return gitlab.paged(
        "projects/" + p.id() + "/merge_requests",
        params(
            "scope",
            "all",
            "state",
            "all",
            "created_after",
            since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            "order_by",
            "created_at",
            "sort",
            "desc"),
        MergeRequest.class,
        settings.maxMrs());
  }

  Gitlab.Page<Note> notes(Project p, MergeRequest m) {
    return gitlab.paged(
        "projects/" + p.id() + "/merge_requests/" + m.iid() + "/notes",
        params("sort", "asc", "order_by", "created_at"),
        Note.class,
        100);
  }

  private boolean isBot(Commit x) {
    return Gitlab.isBot(settings.bots(), x.authorEmail(), x.authorName());
  }

  static Map<String, String> params(String... pairs) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  static String enc(String s) {
    return Gitlab.enc(s);
  }
}
