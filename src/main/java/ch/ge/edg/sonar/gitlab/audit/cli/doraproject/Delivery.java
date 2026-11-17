package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import ch.ge.edg.sonar.gitlab.audit.dora.RunOutcome;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Ce qu'un déploiement a livré : les commits atteignables depuis son commit et pas depuis celui du
 * déploiement précédent, quelle que soit la source des deux ({@link Deploy}). Fusions et commits de
 * CI compris : le tri se fait au calcul ({@link DeliveredFeatures}).
 *
 * @param previous le déploiement précédent, d'où part la comparaison ; absent quand aucun ne le
 *     précède : ses commits sont alors ceux qu'il atteint, commités pendant la période
 * @param truncated GitLab a interrompu ou refusé la comparaison : des commits peuvent manquer
 */
record Delivery(Deploy deploy, @Nullable Deploy previous, List<Commit> commits, boolean truncated) {

  /** Le « pipeline » des déploiements par tag, dans la page et l'import Middleware. */
  static final String PIPELINE = "tag";

  Delivery {
    commits = commits == null ? List.of() : List.copyOf(commits);
  }

  /**
   * Un commit tel que la comparaison le donne.
   *
   * @param parents deux ou plus pour un commit de fusion, le premier étant la branche dans laquelle
   *     on a fusionné
   */
  record Commit(
      String sha,
      List<String> parents,
      String message,
      @Nullable Instant authoredAt,
      @Nullable Instant committedAt) {

    Commit {
      parents = parents == null ? List.of() : List.copyOf(parents);
    }

    /** La première ligne du message. */
    String title() {
      int end = message.indexOf('\n');
      return (end < 0 ? message : message.substring(0, end)).strip();
    }

    /** Un commit de fusion : deux parents ou plus. */
    boolean merge() {
      return parents.size() >= 2;
    }

    String shortSha() {
      return sha.substring(0, Math.min(8, sha.length()));
    }
  }

  /**
   * Le déploiement tel que les mesures communes le lisent : un succès à sa date, qui embarque tous
   * les commits livrés.
   *
   * @param rank son rang dans les livraisons de l'instantané, de 1 pour la plus ancienne
   * @param webUrl l'adresse du projet GitLab, d'où part le lien vers le tag
   */
  Snapshot.Deployment deployment(int rank, String webUrl) {
    List<Snapshot.Change> changes =
        commits.stream().map(c -> new Snapshot.Change(c.sha(), c.authoredAt())).toList();
    return new Snapshot.Deployment(
        PIPELINE,
        rank,
        deploy.at(),
        RunOutcome.SUCCESS,
        false,
        changes,
        webUrl + "/-/tags/" + deploy.name());
  }
}
