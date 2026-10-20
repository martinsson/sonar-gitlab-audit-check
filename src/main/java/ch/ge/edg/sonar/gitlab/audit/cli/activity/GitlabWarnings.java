package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.GitlabListener;
import ch.ge.edg.sonar.gitlab.audit.text.Text;

/** Ce que le client GitLab signale en chemin, dit à la console : sur la sortie, en jaune. */
record GitlabWarnings(Console console) implements GitlabListener {

  private static final String YELLOW = "\033[33m";

  @Override
  public void pagingInterrupted(String page, int status) {
    console.println(
        console.color(
            Text.format("    Pagination interrompue page %s : HTTP %d", page, status), YELLOW));
  }

  @Override
  public void captureNotWritten(String reason) {
    console.err().println("  (capture non écrite : " + reason + ")");
  }
}
