package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import picocli.CommandLine.Mixin;

/** La manière dont une commande rend compte : couleurs en console, et forme des CSV écrits. */
public final class StyleOptions {

  @Mixin ColorOption color = new ColorOption();

  @Mixin CommaOption comma = new CommaOption();

  /** auto | always | never. */
  public String colorMode() {
    return color.mode();
  }

  /** CSV à virgules et sans BOM, pour un outil plutôt que pour Excel. */
  public boolean comma() {
    return comma.enabled();
  }
}
