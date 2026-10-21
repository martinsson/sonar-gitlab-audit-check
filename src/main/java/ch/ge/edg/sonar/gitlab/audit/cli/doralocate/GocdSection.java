package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.Platforms;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Quels pipelines GoCD vont avec le projet GitLab : ceux dont un dépôt git a le chemin du projet
 * (formes http et ssh confondues), puis leurs pipelines en aval. Un pipeline qui n'a jamais tourné
 * n'a pas de matériau connu et ne peut pas être rattaché.
 */
final class GocdSection {

  private static final String ROW = "  %-40s %-20s %-18s %-12s %s%n";

  private final GocdApi gocd;
  private final Context ctx;
  private final PrintStream out;

  public GocdSection(GocdApi gocd, Context ctx) {
    this.gocd = gocd;
    this.ctx = ctx;
    this.out = ctx.out();
  }

  public void run(GitlabProject project, Path indexFile, boolean refresh) throws Stop {
    out.println("\n== GoCD");
    GocdIndex index = new IndexFile(indexFile, ctx).loadOrBuild(gocd, refresh);
    Map<String, Integer> depth = RepoPipelines.of(index, project.pathWithNamespace());
    out.print(
        Text.format(
            "  Index : %d pipelines visibles (dont %d sans run lisible, donc sans matériau connu),"
                + " construit le %s%n",
            index.pipelines().size(), index.unknown(), index.builtAt()));
    if (depth.isEmpty()) {
      out.println(
          "  Aucun pipeline ne prend "
              + project.pathWithNamespace().toLowerCase(Locale.ROOT)
              + " comme dépôt git.");
      return;
    }
    out.print(Text.format(ROW, "pipeline", "groupe", "rôle", "plateforme?", "dernier run"));
    Set<String> groups = new LinkedHashSet<>();
    depth.forEach(
        (name, level) -> {
          GocdIndex.Pipeline p = index.pipeline(name);
          groups.add(p.group());
          String role = level == 0 ? "dépôt direct" : "aval niveau " + level;
          out.print(Text.format(ROW, name, p.group(), role, Platforms.guess(name), p.lastRun()));
        });
    ctx.suggestions().gocd(depth.keySet(), project.path());
    if (groups.size() == 1) {
      String g = groups.iterator().next();
      out.println(
          "  Tous dans le groupe « "
              + g
              + " » : --group "
              + g
              + " prendrait aussi les autres pipelines du groupe.");
    }
  }
}
