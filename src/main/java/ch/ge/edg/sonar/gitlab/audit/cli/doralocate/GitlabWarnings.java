package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.gitlab.GitlabListener;

/** Ce que le client GitLab signale en chemin, dit sur l'erreur : la sortie est le rapport. */
record GitlabWarnings(Console console) implements GitlabListener {

  private static final String YELLOW = "\033[33m";

  @Override
  public void pagingInterrupted(String page, int status) {
    console
        .err()
        .println(
            console.color(
                "    Pagination interrompue page %s : HTTP %d".formatted(page, status), YELLOW));
  }

  @Override
  public void captureNotWritten(String reason) {
    console.err().println("  (capture non écrite : " + reason + ")");
  }
}
