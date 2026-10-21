package ch.ge.edg.sonar.gitlab.audit.gitlab;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.text.Text;

/**
 * Ce que le client GitLab signale en chemin, dit à la console : une pagination interrompue en
 * jaune, sur la sortie ou sur l'erreur selon que la sortie est un rapport ou non ; une capture non
 * écrite, toujours sur l'erreur.
 */
public final class GitlabWarnings implements GitlabListener {

  private static final String YELLOW = "\033[33m";

  private final Console console;
  private final boolean onError;

  private GitlabWarnings(Console console, boolean onError) {
    this.console = console;
    this.onError = onError;
  }

  /** Les paginations interrompues sur la sortie, avec le reste de ce qu'affiche la commande. */
  public static GitlabWarnings onOutput(Console console) {
    return new GitlabWarnings(console, false);
  }

  /** Les paginations interrompues sur l'erreur : la sortie est un rapport qu'on garde propre. */
  public static GitlabWarnings onError(Console console) {
    return new GitlabWarnings(console, true);
  }

  @Override
  public void pagingInterrupted(String page, String status) {
    String line =
        console.color(Text.format("    Pagination interrompue page %s : %s", page, status), YELLOW);
    if (onError) console.err().println(line);
    else console.println(line);
  }

  @Override
  public void captureNotWritten(String reason) {
    console.err().println("  (capture non écrite : " + reason + ")");
  }
}
