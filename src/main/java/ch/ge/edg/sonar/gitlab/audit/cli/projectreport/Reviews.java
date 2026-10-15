package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/**
 * Ce que la section « Merge requests » a mesuré, et que relisent les constats. {@code
 * selfMergedShare} vaut -1 quand l'auto-fusion n'est pas mesurable.
 */
record Reviews(
    int mrs,
    double leadP90,
    double silentShare,
    double selfMergedShare,
    double squashShare,
    boolean noMrs) {
  static final Reviews EMPTY = new Reviews(0, 0, 0, 0, 0, false);
  static final Reviews NONE = new Reviews(0, 0, 0, 0, 0, true);
}
