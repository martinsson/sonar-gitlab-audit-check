package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.csv.Column;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** §4 — signaux de pratique sur les sélectionnés. */
final class DeepPass {

  private final Gitlab gl;
  private final Screen con;
  private final Preflight.Instance instance;
  private final Funnel funnel;

  DeepPass(Gitlab gl, Screen con, Preflight.Instance instance, Funnel funnel) {
    this.gl = gl;
    this.con = con;
    this.instance = instance;
    this.funnel = funnel;
  }

  /**
   * La passe entière, entre la lecture et l'écriture du cache des clés Sonar ; le cache dit sur
   * l'erreur de la console ce qu'il n'a pas pu écrire.
   */
  void run(Outputs outputs, FunnelResult funnelResult) throws IOException {
    SonarCache cache = new SonarCache(outputs.cache(), outputs.useCache(), con.out().err());
    cache.load();
    List<GitlabProject> all =
        funnelResult.projects().stream().map(AuditedProject::project).toList();
    measureAll(
        outputs, funnelResult.selected(), new PracticeProbes(gl, instance, funnel, cache, all));
    cache.save();
  }

  private void measureAll(Outputs outputs, List<AuditedProject> selected, PracticeProbes probes)
      throws IOException {
    if (selected.isEmpty()) return;
    con.title("6. Signaux de pratique sur les %d sélectionnés".formatted(selected.size()));

    // Une dizaine d'appels par projet, sans lien d'un projet à l'autre :
    // la seule raison de les faire l'un après l'autre était qu'on ne
    // s'était pas posé la question. C'est le facteur qui compte.
    List<Practices> measured = gl.map(selected, funnel.concurrency(), probes::measure);
    List<Assessed> assessed = new ArrayList<>();
    for (int i = 0; i < selected.size(); i++) {
      assessed.add(new Assessed(selected.get(i), measured.get(i)));
    }
    DeepReport report = new DeepReport(con, assessed);
    report.ciRoutes(probes.lintRefusedStatus());
    report.coverage();
    Path pratiques = outputs.pratiques();
    if (pratiques != null) {
      Column.write(pratiques, outputs.comma(), CsvFiles.practices(funnel.sinceDays()), assessed);
      con.out().out().printf("%n  Pratiques : %d lignes.%n", selected.size());
    }
  }
}
