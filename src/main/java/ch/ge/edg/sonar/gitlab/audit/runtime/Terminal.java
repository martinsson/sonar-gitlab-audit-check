package ch.ge.edg.sonar.gitlab.audit.runtime;

import java.util.Map;

/**
 * Ce que la JVM sait du terminal auquel elle parle.
 *
 * @param attached la sortie va à un terminal, pas à un fichier ni à un pipe
 * @param windows le système est Windows, où les couleurs ne vont pas de soi
 */
public record Terminal(boolean attached, boolean windows) {

  /** Pas de terminal : sortie redirigée, CI, tests. */
  public static final Terminal NONE = new Terminal(false, false);

  /**
   * Les couleurs ANSI sont le second symptôme sous Windows : conhost ne les interprète pas tant
   * qu'un programme n'a pas activé le mode VT, et PowerShell 5.1 affiche alors « ←[1m » en clair.
   * On ne les active donc sous Windows que si l'émulateur s'annonce (Windows Terminal, ConEmu,
   * ANSICON). Ailleurs, un terminal suffit. NO_COLOR coupe tout, partout.
   */
  boolean colors(Map<String, String> env) {
    if (env.containsKey("NO_COLOR") || !attached) return false;
    if (!windows) return true;
    return env.containsKey("WT_SESSION")
        || env.containsKey("ConEmuANSI")
        || env.containsKey("ANSICON")
        || env.containsKey("TERM");
  }
}
