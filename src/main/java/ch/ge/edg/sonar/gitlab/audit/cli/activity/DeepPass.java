package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** §4 — signaux de pratique sur les sélectionnés. */
final class DeepPass {

  private final AuditContext ctx;
  private final Outputs outputs;
  private final List<Proj> selected;
  private final SonarProbe sonar;

  private DeepPass(AuditContext ctx, Outputs outputs, SonarCache cache, List<Proj> all) {
    this.ctx = ctx;
    this.outputs = outputs;
    this.selected = all.stream().filter(p -> p.selected).toList();
    this.sonar = new SonarProbe(ctx, cache, new CiConfig(ctx, all));
  }

  /** La passe entière, entre la lecture et l'écriture du cache des clés Sonar. */
  static void run(AuditContext ctx, Outputs outputs, List<Proj> all, PrintStream err)
      throws IOException {
    SonarCache cache = new SonarCache(outputs.cache(), outputs.useCache(), err);
    cache.load();
    new DeepPass(ctx, outputs, cache, all).measureAll();
    cache.save();
  }

  private void measureAll() throws IOException {
    if (selected.isEmpty()) return;
    ctx.con().title("6. Signaux de pratique sur les %d sélectionnés".formatted(selected.size()));

    // Une dizaine d'appels par projet, sans lien d'un projet à l'autre :
    // la seule raison de les faire l'un après l'autre était qu'on ne
    // s'était pas posé la question. C'est le facteur qui compte.
    ctx.gl().parallel(selected, ctx.concurrency(), this::measure);
    DeepReport report = new DeepReport(ctx, selected);
    report.ciRoutes();
    report.coverage();
    Path pratiques = outputs.pratiques();
    if (pratiques != null) {
      CsvFiles.write(
          pratiques,
          outputs.comma(),
          CsvFiles.PRATIQUES,
          selected,
          p -> p.pratRow(ctx.sinceDays()));
      ctx.out().printf("%n  Pratiques : %d lignes.%n", selected.size());
    }
  }

  private void measure(Proj p) {
    Prat r = p.prat;
    protection(p, r);
    new Reviews(ctx).measure(p, r);
    new Pipelines(ctx).measure(p, r);
    new Delivery(ctx).measure(p, r);
    sonar.measure(p, r);
    Churn.measure(ctx, p, r);
  }

  /** Binaire, non ambigu, et cela explique le nombre de poussées directes. */
  private void protection(Proj p, Prat r) {
    Gitlab.Response br =
        ctx.gl().get("projects/" + p.id + "/protected_branches", Map.of("per_page", "100"));
    if (br.status() != 200) return;
    for (JsonNode b : br.json()) {
      if (p.defaultBranch.equals(text(b, "name"))) {
        r.defaultProtected = true;
        JsonNode lvl = b.path("push_access_levels");
        r.pushLocked = lvl.size() > 0 && lvl.get(0).path("access_level").asInt(-1) == 0;
      }
    }
  }
}
