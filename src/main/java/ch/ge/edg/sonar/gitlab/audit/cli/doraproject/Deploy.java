package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import java.time.Instant;
import java.util.List;

/**
 * Un déploiement, d'où qu'il vienne : quoi, où, quand, et quel commit du dépôt il a mis en place.
 * C'est l'étape partagée : la suite (les commits livrés depuis le déploiement précédent, leurs
 * fonctionnalités, le délai) ne dépend que de cette forme, et un import par l'API REST de DevLake
 * prendrait la même liste (un déploiement et son commit ; DevLake retrouve les commits livrés dans
 * git).
 *
 * @param name ce qui le nomme pour un humain : un tag de version
 * @param environment l'environnement mis à jour : {@link #PRODUCTION}
 * @param at quand il a eu lieu
 * @param sha le commit déployé
 * @param repo le chemin GitLab du dépôt de ce commit
 */
record Deploy(String name, String environment, Instant at, String sha, String repo, Kind kind) {

  /** Le seul environnement mesuré aujourd'hui. */
  static final String PRODUCTION = "production";

  /** D'où vient le déploiement. */
  enum Kind {
    /**
     * Un tag de version de la branche par défaut ({@code --release-tag-pattern}), daté par le tag
     * annoté, sinon par la date de commit du commit tagué.
     */
    RELEASE_TAG
  }

  /** Une source de déploiements. */
  interface Source {

    /**
     * Les déploiements de {@code from} à {@code to}, du plus ancien au plus récent, précédés du
     * dernier d'avant {@code from} s'il y en a un : la borne d'où part le premier.
     */
    List<Deploy> read(Instant from, Instant to);
  }
}
