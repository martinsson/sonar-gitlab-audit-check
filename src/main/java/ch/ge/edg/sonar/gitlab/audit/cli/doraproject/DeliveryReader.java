package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Ce que chaque déploiement a livré depuis le précédent, lu dans GitLab d'après leurs seuls commits
 * : la comparaison {@code compare?from=<précédent>&to=<déployé>&straight=false}, soit les commits
 * atteignables depuis le commit déployé et pas depuis le précédent. Ne dépend pas de la source des
 * déploiements. GET seulement.
 */
final class DeliveryReader {

  private final Gitlab gitlab;
  private final String projectId;
  private final PrintStream err;

  DeliveryReader(Gitlab gitlab, String projectId, PrintStream err) {
    this.gitlab = gitlab;
    this.projectId = projectId;
    this.err = err;
  }

  private record Pair(Deploy deploy, @Nullable Deploy previous) {}

  /**
   * Les livraisons des déploiements de la période, de la plus ancienne à la plus récente.
   *
   * @param deploys du plus ancien au plus récent ; un déploiement d'avant {@code from} ne sert que
   *     de borne au suivant
   */
  List<Delivery> read(List<Deploy> deploys, Instant from) {
    List<Pair> pairs = new ArrayList<>();
    for (int i = 0; i < deploys.size(); i++) {
      Deploy d = deploys.get(i);
      if (!d.at().isBefore(from)) pairs.add(new Pair(d, i == 0 ? null : deploys.get(i - 1)));
    }
    return gitlab.map(pairs, GitlabReader.CONCURRENCY, p -> deliver(p, from));
  }

  private Delivery deliver(Pair p, Instant from) {
    Deploy previous = p.previous();
    return previous == null ? since(p.deploy(), from) : compare(p.deploy(), previous);
  }

  /** Faute de déploiement précédent, les commits que celui-ci atteint, commités dans la période. */
  private Delivery since(Deploy d, Instant from) {
    Map<String, String> params = Map.of("ref_name", d.sha(), "since", from.toString());
    List<Delivery.Commit> commits =
        gitlab.paged("projects/" + projectId + "/repository/commits", params).stream()
            .map(DeliveryReader::commit)
            .toList();
    return new Delivery(d, null, commits, false);
  }

  /**
   * GitLab calcule la comparaison d'un bloc, sans pagination, avec les différences de fichiers ;
   * trop longue, il l'interrompt et le dit ({@code compare_timeout}). Un refus ou une interruption
   * marque la livraison incomplète.
   */
  private Delivery compare(Deploy d, Deploy previous) {
    Map<String, String> params = Map.of("from", previous.sha(), "to", d.sha(), "straight", "false");
    RestResponse r = gitlab.get("projects/" + projectId + "/repository/compare", params);
    List<Delivery.Commit> commits = new ArrayList<>();
    boolean truncated = !r.ok();
    if (r.ok()) {
      JsonNode body = r.tree();
      body.path("commits").forEach(c -> commits.add(commit(c)));
      truncated = body.path("compare_timeout").asBoolean(false);
    } else {
      err.println("Comparaison " + previous.name() + "..." + d.name() + " : " + r.statusLine());
    }
    return new Delivery(d, previous, commits, truncated);
  }

  private static Delivery.Commit commit(JsonNode c) {
    List<String> parents = new ArrayList<>();
    c.path("parent_ids").forEach(id -> parents.add(id.asText()));
    String message = c.path("message").asText("");
    return new Delivery.Commit(
        c.path("id").asText(),
        parents,
        message.isEmpty() ? c.path("title").asText("") : message,
        Gitlab.instant(c.path("authored_date")),
        Gitlab.instant(c.path("committed_date")));
  }
}
