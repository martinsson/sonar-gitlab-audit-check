package ratchet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LimitsTest {

  private static final String CHECKSTYLE =
      """
      <module name="Checker">
        <module name="TreeWalker">
          <module name="MethodLength"><property name="max" value="50"/></module>
          <module name="JavaNCSS"><property name="methodMaximum" value="30"/></module>
          <module name="AvoidStarImport"/>
        </module>
      </module>
      """;

  @Test
  void aLoweredMinimumIsLooser() {
    Map<String, String> before =
        Limits.fromPom("<coverage.line.minimum>0.90</coverage.line.minimum>");
    Map<String, String> now = Limits.fromPom("<coverage.line.minimum>0.80</coverage.line.minimum>");

    assertThat(Limits.loosened("pom.xml", before, now))
        .containsExactly("pom.xml: coverage.line.minimum lowered from 0.90 to 0.80");
  }

  @Test
  void aRaisedMinimumIsFine() {
    Map<String, String> before =
        Limits.fromPom("<mutation.score.minimum>85</mutation.score.minimum>");
    Map<String, String> now = Limits.fromPom("<mutation.score.minimum>90</mutation.score.minimum>");

    assertThat(Limits.loosened("pom.xml", before, now)).isEmpty();
  }

  @Test
  void aRaisedCheckstyleLimitIsLooser() throws IOException {
    Map<String, String> before = Limits.fromCheckstyle(CHECKSTYLE);
    Map<String, String> now = Limits.fromCheckstyle(CHECKSTYLE.replace("\"30\"", "\"45\""));

    assertThat(Limits.loosened("cs", before, now))
        .containsExactly("cs: /Checker/TreeWalker/JavaNCSS.methodMaximum raised from 30 to 45");
  }

  @Test
  void aLoweredCheckstyleLimitIsFine() throws IOException {
    Map<String, String> before = Limits.fromCheckstyle(CHECKSTYLE);
    Map<String, String> now = Limits.fromCheckstyle(CHECKSTYLE.replace("\"50\"", "\"40\""));

    assertThat(Limits.loosened("cs", before, now)).isEmpty();
  }

  @Test
  void aRemovedCheckstyleModuleIsLooser() throws IOException {
    Map<String, String> before = Limits.fromCheckstyle(CHECKSTYLE);
    Map<String, String> now =
        Limits.fromCheckstyle(CHECKSTYLE.replace("<module name=\"AvoidStarImport\"/>", ""));

    assertThat(Limits.loosened("cs", before, now))
        .containsExactly("cs: /Checker/TreeWalker/AvoidStarImport was removed");
  }

  @Test
  void aRaisedMaxConstantIsLooser() {
    Map<String, String> before =
        Limits.fromConstants("A.java", List.of("  private static final int MAX_CLASSES = 15;"));
    Map<String, String> now =
        Limits.fromConstants("A.java", List.of("  private static final int MAX_CLASSES = 25;"));

    assertThat(Limits.loosened("tests", before, now))
        .containsExactly("tests: A.java MAX_CLASSES raised from 15 to 25");
  }

  @Test
  void aValueThatIsNotANumberIsNeverReported() {
    Map<String, String> before = Limits.fromPom("<x.maximum>10</x.maximum>");
    Map<String, String> now = Limits.fromPom("<x.maximum>${other}</x.maximum>");

    assertThat(Limits.loosened("pom.xml", before, now)).isEmpty();
  }
}
