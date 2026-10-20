package ch.ge.edg.sonar.gitlab.audit.console;

import java.io.PrintStream;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Ce qu'une commande affiche : sa sortie, son erreur et le choix des couleurs, pris une fois à
 * l'entrée de la commande et passés à ceux qui écrivent. Les écritures de la sortie standard se
 * font directement sur la console ({@link #println(String)}), les lignes formatées sur {@link
 * #out()} ; les avertissements vont sur {@link #err()}.
 */
public final class Console {

  public static final String RESET = "\033[0m";

  private final PrintStream out;
  private final PrintStream err;
  private final boolean colors;

  public Console(PrintStream out, PrintStream err, boolean colors) {
    this.out = out;
    this.err = err;
    this.colors = colors;
  }

  /**
   * auto | always | never : l'option en ligne de commande a le dernier mot, {@code auto} (ou rien)
   * s'en remet à ce que le terminal sait faire.
   */
  public static boolean colors(@Nullable String mode, boolean auto) {
    if (mode == null) return auto;
    return switch (mode.toLowerCase(Locale.ROOT)) {
      case "always" -> true;
      case "never" -> false;
      default -> auto;
    };
  }

  public PrintStream out() {
    return out;
  }

  public PrintStream err() {
    return err;
  }

  public boolean colors() {
    return colors;
  }

  /** {@code text} dans la couleur ANSI {@code code}, ou tel quel sans couleurs. */
  public String color(String text, String code) {
    return colors ? code + text + RESET : text;
  }

  public void println() {
    out.println();
  }

  public void println(String line) {
    out.println(line);
  }

  public void print(String text) {
    out.print(text);
  }

  public void flush() {
    out.flush();
  }
}
