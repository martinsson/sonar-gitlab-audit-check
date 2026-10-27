package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/**
 * Les 15 points de la checklist de qualité d'une User Story (section 6.1), dans l'ordre : leur
 * numéro et leur intitulé. Le contrôle de chacun est dans {@link Checklist}.
 */
enum ChecklistPoint {
  TITRAGE(1, "Respect du titrage"),
  TYPE(2, "Respect du type de story"),
  DESCRIPTION(3, "Respect de la description"),
  NUMEROTATION_AC(4, "Numérotation des AC"),
  AC_DE_CONTEXTE(5, "Présence de l'AC de contexte"),
  FORME_DES_AC(6, "Qualité de la forme des AC"),
  INVEST(7, "Respect du modèle INVEST"),
  CYCLE_DE_VIE(8, "Respect du cycle de vie"),
  ATTRIBUTION(9, "Respect de l'attribution"),
  SOUS_TACHES(10, "Découpage en sous-tâches"),
  ESTIMATION(11, "Estimation des sous-tâches en heures"),
  RESTE_A_FAIRE(12, "Reste à faire des tâches en heures"),
  LIEN_FEATURE(13, "Lien vers une Feature ou épic"),
  LIEN_COMPOSANT(14, "Lien vers le composant concerné"),
  LIEN_RELEASE(15, "Lien vers la Release concernée");

  /** Les points 1 à 6 pèsent dans le décompte des non-conformités ; les autres sont à venir. */
  static final int LAST_COUNTED = 6;

  private final int number;
  private final String title;

  ChecklistPoint(int number, String title) {
    this.number = number;
    this.title = title;
  }

  int number() {
    return number;
  }

  /** « 4. Numérotation des AC » : l'intitulé tel que le rapport l'écrit. */
  String label() {
    return number + ". " + title;
  }

  /** Le point de ce numéro, de 1 à 15. */
  static ChecklistPoint of(int number) {
    return values()[number - 1];
  }
}
