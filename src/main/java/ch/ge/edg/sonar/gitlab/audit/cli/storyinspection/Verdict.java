package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

/** Ce qu'un point de contrôle conclut pour une story, avec le symbole qui l'affiche. */
enum Verdict {
  RESPECTE("✅"),
  PARTIEL("⚠️"),
  NON_RESPECTE("❌"),
  SANS_OBJET("⏭️");

  private final String symbol;

  Verdict(String symbol) {
    this.symbol = symbol;
  }

  String symbol() {
    return symbol;
  }
}
