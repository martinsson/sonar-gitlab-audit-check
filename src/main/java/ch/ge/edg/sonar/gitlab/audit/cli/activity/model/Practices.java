package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les signaux de pratique d'un projet sélectionné, une partie par sonde. Une mesure qui n'a pas pu
 * se lire reste absente : elle ne se publie pas comme un zéro.
 */
public record Practices(
    Practices.Protection protection,
    Practices.Review review,
    Practices.Builds builds,
    Practices.Deployment deployment,
    Practices.Sonar sonar,
    Practices.Lines lines) {

  /** La branche par défaut est-elle protégée, et la poussée directe y est-elle fermée. */
  public record Protection(boolean defaultProtected, boolean pushLocked) {
    public static Protection none() {
      return new Protection(false, false);
    }
  }

  /**
   * Les MR fusionnées sur la fenêtre.
   *
   * @param medianTtmDays la médiane du délai d'ouverture à fusion, en jours
   * @param approvals l'échantillon d'approbations, absent hors Enterprise ou sans MR
   */
  public record Review(
      int mergedMrs,
      @Nullable Double medianTtmDays,
      int selfMerged,
      @Nullable Double notesPerMr,
      @Nullable Approvals approvals) {

    public static Review none() {
      return new Review(0, null, 0, null, null);
    }
  }

  /** Sur {@code sample} MR lues, la part approuvée et les auto-approbations. */
  public record Approvals(int sample, double share, int selfApproved) {}

  /**
   * Les pipelines de la branche par défaut.
   *
   * @param success la part de succès, absente sans pipeline
   * @param recovery les incidents rouges, absents sans pipeline vert ou rouge daté
   */
  public record Builds(int pipelines, @Nullable Double success, @Nullable Recovery recovery) {
    public static Builds none() {
      return new Builds(0, null, null);
    }
  }

  /**
   * Combien de fois la branche par défaut a cassé, et combien de temps elle l'est restée.
   *
   * @param unresolved 1 quand la fenêtre finit sur un rouge, 0 sinon
   * @param medianHours la médiane des retours au vert, absente sans retour
   */
  public record Recovery(int incidents, int unresolved, @Nullable Double medianHours) {}

  /**
   * Les environnements, et les déploiements que DORA en compte.
   *
   * @param doraUnavailable DORA a répondu 403 ou 404
   */
  public record Deployment(
      @Nullable Integer environments, @Nullable Double deployments, boolean doraUnavailable) {}

  /**
   * Ce que le dépôt dit de Sonar.
   *
   * @param files les fichiers surveillés présents à la racine
   * @param facts ce que la CI et le pom disent de la clé
   * @param fromCache les faits viennent du cache, pas d'une lecture de la CI
   */
  public record Sonar(List<String> files, CiFacts facts, boolean fromCache) {

    /** La route de qui n'a pas relu sa CI : le cache l'a donnée. */
    public static final String CACHE = "cache";

    /** La route de lecture qui voit tout l'arbre d'inclusion. */
    public static final String LINT = "ci/lint";

    /** Le repli, qui ne voit que les includes qu'il sait suivre. */
    public static final String FALLBACK = "includes suivis";

    /** Par où la CI a été lue : depuis le cache, ou par la route de lecture ; null sans lecture. */
    public @Nullable String ciRoute() {
      return fromCache ? CACHE : facts.ciRoute;
    }
  }

  /**
   * Lignes ajoutées + supprimées sur la fenêtre ; absentes quand les commits ne se lisent pas.
   *
   * @param commits les commits humains hors merges dont les lignes sont comptées
   */
  public record Lines(@Nullable Long changed, @Nullable Integer commits, boolean truncated) {
    public static Lines none() {
      return new Lines(null, null, false);
    }
  }
}
