package ch.ge.edg.sonar.gitlab.audit.cli.activity;

/**
 * Ce que l'entonnoir a décidé d'un projet. Un projet encore en lice n'a pas de motif ; un exclu en
 * a un, et l'étape qui l'a écarté ; un éligible a sa strate ; un sélectionné a sa strate et son
 * motif d'entrée.
 */
sealed interface Fate {

  /** L'étape qui a écarté un projet. */
  enum Stage {
    INVENTORY,
    RECENCY,
    ACTIVITY_CUT,
    FLOOR
  }

  /** Toujours en lice : les étapes suivantes décideront. */
  record InPlay() implements Fate {}

  /** Écarté par {@code stage}, pour {@code reason} — le motif publié dans le CSV. */
  record Excluded(Stage stage, String reason) implements Fate {}

  /** Au-dessus du plancher, rangé dans sa strate de taille, pas (encore) tiré. */
  record Eligible(String bucket) implements Fate {}

  /** Tiré, avec le motif de son entrée. */
  record Selected(String bucket, String reason) implements Fate {

    boolean isWitness() {
      return reason.startsWith("témoin");
    }
  }

  default boolean inPlay() {
    return this instanceof InPlay;
  }

  default boolean isSelected() {
    return this instanceof Selected;
  }

  default boolean excludedAt(Stage stage) {
    return this instanceof Excluded e && e.stage() == stage;
  }

  /** La strate d'un éligible ou d'un sélectionné (leur composant la redéfinit) ; vide sinon. */
  default String bucket() {
    return "";
  }

  /** Le motif d'exclusion ; vide sinon. */
  default String exclusion() {
    return this instanceof Excluded e ? e.reason() : "";
  }

  /** Le motif de sélection ; vide sinon. */
  default String selectionReason() {
    return this instanceof Selected s ? s.reason() : "";
  }
}
