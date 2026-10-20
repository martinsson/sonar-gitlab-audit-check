package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** §4 — signaux de pratique sur les sélectionnés. */
final class DeepPass {

  private final Gitlab gl;
  private final Screen con;
  private final Counters counters;
  private final Funnel funnel;

  DeepPass(Gitlab gl, Screen con, Counters counters, Funnel funnel) {
    this.gl = gl;
    this.con = con;
    this.counters = counters;
    this.funnel = funnel;
  }

  /**
   * La passe entière, entre la lecture et l'écriture du cache des clés Sonar ; le cache dit sur
   * l'erreur de la console ce qu'il n'a pas pu écrire.
   */
  void run(Outputs outputs, List<Proj> all) throws IOException {
    SonarCache cache = new SonarCache(outputs.cache(), outputs.useCache(), con.out().err());
    cache.load();
    List<Proj> selected = all.stream().filter(p -> p.selected).toList();
    measureAll(
        outputs, selected, new SonarProbe(gl, counters, cache, new CiConfig(gl, counters, all)));
    cache.save();
  }

  private void measureAll(Outputs outputs, List<Proj> selected, SonarProbe sonar)
      throws IOException {
    if (selected.isEmpty()) return;
    con.title("6. Signaux de pratique sur les %d sélectionnés".formatted(selected.size()));

    // Une dizaine d'appels par projet, sans lien d'un projet à l'autre :
    // la seule raison de les faire l'un après l'autre était qu'on ne
    // s'était pas posé la question. C'est le facteur qui compte.
    gl.parallel(selected, funnel.concurrency(), p -> measure(p, sonar));
    DeepReport report = new DeepReport(con, counters, selected);
    report.ciRoutes();
    report.coverage();
    Path pratiques = outputs.pratiques();
    if (pratiques != null) {
      CsvFiles.write(
          pratiques,
          outputs.comma(),
          CsvFiles.PRATIQUES,
          selected,
          p -> p.pratRow(funnel.sinceDays()));
      con.out().out().printf("%n  Pratiques : %d lignes.%n", selected.size());
    }
  }

  private void measure(Proj p, SonarProbe sonar) {
    Prat r = p.prat;
    protection(p, r);
    new Reviews(gl, counters, funnel).measure(p, r);
    new Pipelines(gl, funnel).measure(p, r);
    new Delivery(gl, counters, funnel).measure(p, r);
    sonar.measure(p, r);
    Churn.measure(gl, funnel, p, r);
  }

  /** Binaire, non ambigu, et cela explique le nombre de poussées directes. */
  private void protection(Proj p, Prat r) {
    Gitlab.Response br =
        gl.get("projects/" + p.id + "/protected_branches", Map.of("per_page", "100"));
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
