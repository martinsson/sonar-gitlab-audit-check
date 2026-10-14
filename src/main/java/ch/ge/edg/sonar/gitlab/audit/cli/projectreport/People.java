package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

/** Ce que la section « Contributeurs » a mesuré. */
record People(int authors, int busFactor, double topShare, long activeRecently) {
  static final People EMPTY = new People(0, 0, 0, 0);
}
