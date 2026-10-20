package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Les commandes d'extraction proposées à la fin : des suggestions, à vérifier avant de lancer. */
public final class Suggestions {

  private final List<String> commands = new ArrayList<>();
  private boolean jira;

  /** Une seule extraction Jira : celle du premier préfixe retenu. */
  void jira(String key, String slug) {
    if (jira) return;
    jira = true;
    commands.add(
        "jbang dora/JiraExtract.java --project " + key + " --out-dir extraction/" + slug + "/jira");
  }

  void gocd(Collection<String> pipelines, String slug) {
    commands.add(
        "jbang dora/GocdExtract.java --pipeline "
            + String.join(",", pipelines)
            + " --out-dir extraction/"
            + slug
            + "/gocd");
  }

  public void print(PrintStream out) {
    if (commands.isEmpty()) return;
    out.println("\n== Extractions proposées (à vérifier avant de lancer)");
    commands.forEach(c -> out.println("  " + c));
  }
}
