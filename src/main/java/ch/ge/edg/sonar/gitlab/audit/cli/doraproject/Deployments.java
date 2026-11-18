package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les déploiements d'une extraction, selon {@code --deploy-mapping} : les tags de version quand on
 * les demande, ou sans GoCD quand il y en a sur la période ; sinon les runs GoCD ; faute de GoCD,
 * les fusions.
 *
 * <p>Avec les tags, deux étapes qui ne se connaissent pas : une source donne les déploiements
 * ({@link Deploy.Source}, ici {@link TagReader}), puis la comparaison de chacun au précédent donne
 * ce qu'il a livré ({@link DeliveryReader}). Une autre source (un fichier de version dans un autre
 * dépôt qui pointe un de ces tags) ne changerait que la première.
 */
final class Deployments {

  /**
   * Les déploiements lus.
   *
   * @param source la règle qui les a faits
   * @param releases avec les tags, ce que chacun a livré ; absent sinon
   */
  record Read(
      Snapshot.DeploySource source,
      List<Snapshot.Deployment> deployments,
      Snapshot.@Nullable Releases releases) {

    Read {
      deployments = List.copyOf(deployments);
    }
  }

  private final Extraction.Request request;
  private final Extraction.DeployRule rule;
  private final Connections connections;
  private final Sleeper sleeper;
  private final PrintStream err;

  Deployments(
      Extraction.Request request,
      Extraction.DeployRule rule,
      Connections connections,
      Sleeper sleeper,
      PrintStream err) {
    this.request = request;
    this.rule = rule;
    this.connections = connections;
    this.sleeper = sleeper;
    this.err = err;
  }

  Read read(
      Gitlab gitlab,
      GitlabReader.Project p,
      List<Snapshot.MergeRequest> mrs,
      Instant from,
      Instant to)
      throws Stop, IOException {
    Read released = released(gitlab, p, from, to);
    if (released != null) return released;
    if (request.pipelines().isEmpty()) {
      return new Read(Snapshot.DeploySource.MERGE, Merges.asDeployments(mrs), null);
    }
    List<Snapshot.Deployment> runs =
        new GocdReader(connections.gocd(sleeper), request.maxUpstream(), err)
            .deployments(request.pipelines(), p.path(), from);
    return new Read(Snapshot.DeploySource.GOCD, runs, null);
  }

  /**
   * Les tags de version, s'ils sont demandés, ou en automatique sans GoCD s'il y en a sur la
   * période, chacun avec ce qu'il a livré depuis le précédent ; aucun sinon, et une autre règle
   * s'applique.
   */
  private @Nullable Read released(Gitlab gitlab, GitlabReader.Project p, Instant from, Instant to) {
    boolean gocd = !request.pipelines().isEmpty();
    boolean forced = rule.mapping() == Extraction.Mapping.TAGS;
    if (!forced && (rule.mapping() != Extraction.Mapping.AUTO || gocd)) return null;
    Deploy.Source source = new TagReader(gitlab, p, rule.releaseTagPattern(), err);
    List<Deploy> found = source.read(from, to);
    if (!forced && found.stream().allMatch(d -> d.at().isBefore(from))) return null;
    if (forced && gocd) err.println("Règle des tags : les pipelines GoCD ne sont pas lus");
    if (!forced) err.println("Tags de version sur la période : règle des tags appliquée");
    List<Delivery> deliveries = new DeliveryReader(gitlab, p.id(), err).read(found, from);
    Snapshot.Releases releases =
        new Snapshot.Releases(rule.releaseTagPattern().pattern(), deliveries);
    return new Read(Snapshot.DeploySource.TAGS, releases.deployments(p.webUrl()), releases);
  }
}
