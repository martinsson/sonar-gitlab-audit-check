package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import picocli.CommandLine.Option;

/** --comma : le CSV pour un outil (virgules, sans BOM) plutôt que pour Excel. */
public final class CommaOption {

  @Option(
      names = "--comma",
      order = 20,
      description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
  boolean enabled;

  public boolean enabled() {
    return enabled;
  }
}
