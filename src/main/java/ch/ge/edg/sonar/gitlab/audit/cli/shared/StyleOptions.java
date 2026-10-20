package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import picocli.CommandLine.Mixin;

/** How a command reports: colours on the console, and the CSV form it writes. */
public final class StyleOptions {

  @Mixin ColorOption color = new ColorOption();

  @Mixin CommaOption comma = new CommaOption();

  /** auto | always | never. */
  public String colorMode() {
    return color.mode();
  }

  /** CSV with commas and no BOM, for a tool, rather than Excel's form. */
  public boolean comma() {
    return comma.enabled();
  }
}
