package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.dora.Platforms;
import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.Materials;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Une ligne de gocd-runs.csv : un run, ses matériaux, son issue, et les commits embarqués. */
final class RunRow {

  static final List<String> HEADER =
      List.of(
          "pipeline",
          "groupe",
          "plateforme_supposee",
          "compteur",
          "label",
          "planifie_le",
          "declenchement",
          "resultat",
          "stages",
          "depots_git",
          "amont",
          "nb_commits",
          "premier_commit_le",
          "dernier_commit_le",
          "delai_premier_commit_h",
          "cles_jira");

  private RunRow() {}

  static String[] cells(
      String pipeline, String group, JsonNode run, List<Commit> found, ZoneId zone) {
    JsonNode cause = run.path("build_cause");
    Instant scheduled = Times.parse(run.path("scheduled_date"));
    Span span = Span.of(found);
    return new String[] {
      pipeline,
      group,
      Platforms.guess(pipeline),
      String.valueOf(run.path("counter").asInt()),
      run.path("label").asText(),
      Times.format(scheduled, zone),
      cause.path("trigger_forced").asBoolean(false) ? "forcé" : "automatique",
      Outcome.of(run),
      String.join("|", stages(run)),
      String.join("|", repos(cause)),
      String.join("|", upstream(cause)),
      String.valueOf(found.size()),
      Times.format(span.first(), zone),
      Times.format(span.last(), zone),
      Times.hours(span.first(), scheduled),
      String.join("|", span.keys())
    };
  }

  /** « build:Passed », ou « NonLancé » pour un stage qui attend son approbation. */
  static List<String> stages(JsonNode run) {
    List<String> out = new ArrayList<>();
    for (JsonNode s : run.path("stages")) {
      String result = s.path("scheduled").asBoolean(true) ? s.path("result").asText() : "NonLancé";
      out.add(s.path("name").asText() + ":" + result);
    }
    return out;
  }

  /** Les dépôts git du run, changés ou non. */
  static Set<String> repos(JsonNode cause) {
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode mr : cause.path("material_revisions")) {
      if (Materials.isGit(mr.path("material"))) out.add(Materials.gitUrl(mr.path("material")));
    }
    return out;
  }

  /** Les révisions amont prises par le run, « recette/3/deploy/1 ». */
  static Set<String> upstream(JsonNode cause) {
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode mr : cause.path("material_revisions")) {
      if (!Materials.isPipeline(mr.path("material"))) continue;
      for (JsonNode mod : mr.path("modifications")) out.add(mod.path("revision").asText());
    }
    return out;
  }

  /** Le premier et le dernier commit embarqués, et toutes les clés Jira qu'ils citent. */
  record Span(@Nullable Instant first, @Nullable Instant last, Set<String> keys) {

    static Span of(List<Commit> found) {
      Instant first = null;
      Instant last = null;
      Set<String> keys = new LinkedHashSet<>();
      for (Commit c : found) {
        Instant at = c.at();
        if (at != null && (first == null || at.isBefore(first))) first = at;
        if (at != null && (last == null || at.isAfter(last))) last = at;
        keys.addAll(c.keys());
      }
      return new Span(first, last, keys);
    }
  }
}
