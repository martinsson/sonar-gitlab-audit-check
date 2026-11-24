package ch.ge.edg.sonar.gitlab.audit.cli.activity.report;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ScreenTest {

  @Test
  void titleUnderlinesUpTo72Characters() {
    assertThat(printed(con -> con.title("Abc"))).isEqualTo("\nAbc\n---\n");
    String longTitle = "x".repeat(80);
    assertThat(printed(con -> con.title(longTitle)))
        .isEqualTo("\n" + longTitle + "\n" + "-".repeat(72) + "\n");
  }

  @Test
  void lineAlignsLabelVerdictAndDetail() {
    assertThat(printed(con -> con.line("api", ProbeVerdict.OK, "d")))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  d\n");
    assertThat(printed(con -> con.line("api", ProbeVerdict.OK, null)))
        .isEqualTo("  api" + " ".repeat(30) + "[OK]  \n");
  }

  @Test
  void colourCodesWhenColoursAreOn() {
    Screen screen = new Screen(Printed.colored().console());
    assertThat(screen.paint("x", Screen.BOLD)).isEqualTo("\033[1mx\033[0m");
    assertThat(Screen.RESET).isEqualTo("\033[0m");
    assertThat(Screen.DIM).isEqualTo("\033[2m");
  }

  private static String printed(Consumer<Screen> r) {
    Printed printed = Printed.plain();
    r.accept(new Screen(printed.console()));
    return printed.text();
  }
}
