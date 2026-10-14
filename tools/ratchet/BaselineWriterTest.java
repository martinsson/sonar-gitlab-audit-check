package ratchet;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BaselineWriterTest {

  @Test
  void checkNameDropsPackageAndCheckSuffix() {
    assertThat(
            BaselineWriter.checkName(
                "com.puppycrawl.tools.checkstyle.checks.sizes.MethodLengthCheck"))
        .isEqualTo("MethodLength");
  }

  @Test
  void classNameStartsAtTheJavaSourceRoot() {
    String file = Path.of("src/test/java/com/example/FooTest.java").toAbsolutePath().toString();

    assertThat(BaselineWriter.className(file)).isEqualTo("com.example.FooTest");
  }

  @Test
  void relativeStripsTheWorkingDirectory() {
    String file = Path.of("src/main/java/A.java").toAbsolutePath().toString();

    assertThat(BaselineWriter.relative(file)).isEqualTo("src/main/java/A.java");
  }
}
