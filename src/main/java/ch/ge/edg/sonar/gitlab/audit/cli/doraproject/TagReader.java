package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Une source de déploiements : les tags de version de la branche par défaut. Un tag dont le nom
 * contient {@code --release-tag-pattern} et dont le commit est sur la branche par défaut est une
 * mise en production, à sa création. GET seulement : la liste des tags, puis les commits de la
 * branche par défaut, pour savoir lesquels y sont.
 */
final class TagReader implements Deploy.Source {

  /** Avant la période, la branche par défaut est lue sur cette marge, pour y trouver ses tags. */
  static final Duration MAIN_MARGIN = Duration.ofDays(30);

  /**
   * Un tag tel que GitLab le liste.
   *
   * @param sha le commit tagué
   * @param at la date du tag annoté, sinon la date de commit du commit tagué
   */
  record TagRef(String name, String sha, Instant at) {}

  private final Gitlab gitlab;
  private final GitlabReader.Project project;
  private final Pattern pattern;
  private final PrintStream err;

  /**
   * Les tags d'un projet.
   *
   * @param pattern l'expression des tags de version, cherchée dans leur nom
   */
  TagReader(Gitlab gitlab, GitlabReader.Project project, Pattern pattern, PrintStream err) {
    this.gitlab = gitlab;
    this.project = project;
    this.pattern = pattern;
    this.err = err;
  }

  @Override
  public List<Deploy> read(Instant from, Instant to) {
    List<TagRef> all = tags();
    long inPeriod =
        all.stream()
            .filter(t -> pattern.matcher(t.name()).find())
            .filter(t -> !t.at().isBefore(from) && !t.at().isAfter(to))
            .count();
    if (inPeriod == 0) {
      err.println(all.size() + " tags, aucun de version (" + pattern + ") sur la période");
      return List.of();
    }
    List<TagRef> releases = releases(all, pattern, from, to, onMain(from.minus(MAIN_MARGIN)));
    long kept = releases.stream().filter(t -> !t.at().isBefore(from)).count();
    err.println(
        inPeriod + " tags de version sur la période, " + kept + " sur " + project.defaultBranch());
    return releases.stream()
        .map(
            t ->
                new Deploy(
                    t.name(),
                    Deploy.PRODUCTION,
                    t.at(),
                    t.sha(),
                    project.path(),
                    Deploy.Kind.RELEASE_TAG))
        .toList();
  }

  /**
   * Les tags de version de la période sur la branche par défaut, du plus ancien au plus récent,
   * précédés du dernier tag de version d'avant la période. Celui-là n'est vérifié que s'il date de
   * moins de {@link #MAIN_MARGIN} avant la période ; plus ancien, il est pris tel quel : il ne sert
   * que de borne.
   *
   * @param onMain les commits de la branche par défaut depuis {@link #MAIN_MARGIN} avant {@code
   *     from}
   */
  static List<TagRef> releases(
      List<TagRef> tags, Pattern pattern, Instant from, Instant to, Set<String> onMain) {
    Instant checkedSince = from.minus(MAIN_MARGIN);
    List<TagRef> sorted =
        new ArrayList<>(tags.stream().filter(t -> pattern.matcher(t.name()).find()).toList());
    sorted.sort(Comparator.comparing(TagRef::at).thenComparing(TagRef::name));
    List<TagRef> out = new ArrayList<>();
    TagRef before = null;
    for (TagRef t : sorted) {
      if (t.at().isAfter(to)) break;
      boolean main = onMain.contains(t.sha());
      if (t.at().isBefore(from)) {
        if (main || t.at().isBefore(checkedSince)) before = t;
      } else if (main) {
        out.add(t);
      }
    }
    if (before != null) out.addFirst(before);
    return out;
  }

  /**
   * Les tags du dépôt : nom, commit tagué, et date de création. GitLab donne {@code created_at} (la
   * date du tagueur) pour un tag annoté seulement ; un tag léger prend la date de commit du commit
   * tagué.
   */
  private List<TagRef> tags() {
    List<TagRef> out = new ArrayList<>();
    for (JsonNode t : gitlab.paged("projects/" + project.id() + "/repository/tags", Map.of())) {
      JsonNode commit = t.path("commit");
      String sha = commit.path("id").asText("");
      Instant tagged = Gitlab.instant(t.path("created_at"));
      Instant at = tagged != null ? tagged : Gitlab.instant(commit.path("committed_date"));
      if (!sha.isEmpty() && at != null) out.add(new TagRef(t.path("name").asText(), sha, at));
    }
    return out;
  }

  /** Les commits de la branche par défaut depuis {@code since}. */
  private Set<String> onMain(Instant since) {
    Map<String, String> params =
        Map.of("ref_name", project.defaultBranch(), "since", since.toString());
    Set<String> out = new HashSet<>();
    for (JsonNode c : gitlab.paged("projects/" + project.id() + "/repository/commits", params)) {
      out.add(c.path("id").asText());
    }
    return out;
  }
}
