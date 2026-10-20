package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.jira.JiraApi;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Quel projet Jira va avec le projet GitLab : l'intégration Jira de GitLab si elle est lisible,
 * puis les préfixes de clés cités, vérifiés un à un auprès de Jira (UTF-8 a la forme d'une clé). Un
 * préfixe cité dans au moins 5 % des commits, et que Jira connaît, reçoit une commande
 * d'extraction.
 */
final class JiraSection {

  /** Préfixes affichés, et donc vérifiés auprès de Jira. */
  static final int SHOWN = 15;

  /** 1 commit sur 20 : 5 %. */
  static final int SHARE = 20;

  private final Gitlab gitlab;
  private final JiraApi jira;
  private final Context ctx;
  private final PrintStream out;

  public JiraSection(Gitlab gitlab, JiraApi jira, Context ctx) {
    this.gitlab = gitlab;
    this.jira = jira;
    this.ctx = ctx;
    this.out = ctx.out();
  }

  public void run(GitlabProject project, int days, int maxCommits) throws Stop {
    out.println("\n== Jira");
    integration(project);
    KeyMentions m =
        KeyMentions.read(
            gitlab, project.id(), ctx.clock().instant().minus(Duration.ofDays(days)), maxCommits);
    out.printf(
        "  Lus : %d commits, %d MR, %d branches sur %d jours%n",
        m.commits(), m.mrs(), m.branches(), days);
    if (m.byPrefix().isEmpty()) {
      out.println("  Aucune clé de ticket trouvée.");
      return;
    }
    List<Map.Entry<String, Mentions>> ranked = m.ranked();
    out.printf(
        "  %-12s %8s %5s %8s %8s  %s%n",
        "préfixe", "commits", "MR", "branches", "tickets", "projet Jira");
    for (Map.Entry<String, Mentions> e : ranked.subList(0, Math.min(SHOWN, ranked.size()))) {
      row(project, e.getKey(), e.getValue(), m.commits());
    }
    if (m.commits() > 0) {
      out.printf(
          Locale.ROOT,
          "  Le préfixe le plus cité l'est dans %.0f %% des commits.%n",
          100.0 * ranked.get(0).getValue().commits() / m.commits());
    }
  }

  private void integration(GitlabProject project) throws Stop {
    String path = "projects/" + project.id() + "/integrations/jira";
    Gitlab.Response r = Access.checked(gitlab.get(path, Map.of()), gitlab);
    JsonNode j = r.json();
    if (r.ok() && j.path("active").asBoolean(false)) {
      JsonNode props = j.path("properties");
      out.println(
          "  Intégration Jira active dans GitLab : clé(s) "
              + props.path("project_keys")
              + " "
              + props.path("project_key").asText("")
              + " "
              + props.path("jira_issue_prefix").asText(""));
    } else {
      String state =
          r.ok() ? "inactive" : "illisible (" + Access.why(r) + ", droit Maintainer requis)";
      out.println("  Intégration Jira de GitLab : " + state);
    }
  }

  private void row(GitlabProject project, String prefix, Mentions m, int commits) throws Stop {
    RestResponse p = Access.checked(jira.project(prefix), jira.http());
    String name;
    if (p.ok()) name = p.json().path("name").asText();
    else name = p.status() == 404 ? "(pas un projet Jira visible)" : p.why();
    if (p.ok() && commits > 0 && m.commits() * SHARE >= commits) {
      ctx.suggestions().jira(prefix, project.path());
    }
    out.printf(
        "  %-12s %8d %5d %8d %8d  %s%n",
        prefix, m.commits(), m.mrs(), m.branches(), m.tickets().size(), name);
  }
}
