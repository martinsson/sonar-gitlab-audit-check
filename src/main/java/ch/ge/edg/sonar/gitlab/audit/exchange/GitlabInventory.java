package ch.ge.edg.sonar.gitlab.audit.exchange;

/** inventaire.csv de GitlabActivityAudit, tel que le croisement le relit. */
public final class GitlabInventory {

  public static final String ID = Columns.named("id");
  public static final String PATH = Columns.named("path");
  public static final String NAME = Columns.named("name");
  public static final String LAST_ACTIVITY_AT = Columns.named("last_activity_at");
  public static final String SELECTED = Columns.named("selectionne");

  private GitlabInventory() {}
}
