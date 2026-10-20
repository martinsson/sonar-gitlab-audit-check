package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import picocli.CommandLine.Option;

/** --color : les couleurs de la console, communes à toutes les commandes. */
public final class ColorOption {

  @Option(
      names = "--color",
      order = 28,
      defaultValue = "auto",
      description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
  String mode = "auto";

  /** auto | always | never. */
  public String mode() {
    return mode;
  }
}
