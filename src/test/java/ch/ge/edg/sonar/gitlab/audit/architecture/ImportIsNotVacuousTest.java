package ch.ge.edg.sonar.gitlab.audit.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * An architecture rule over nothing passes. If the import silently shrinks (a moved root package, a
 * wrong classpath) or a declared layer empties out, every other rule here goes green for no reason:
 * this test fails first.
 */
class ImportIsNotVacuousTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit";
  private static final int MINIMUM_CLASSES = 150;

  private static final List<String> LAYER_PACKAGES =
      List.of(
          "cli",
          "cli.shared",
          "matching",
          "exclusions",
          "dora",
          "exchange",
          "gitlab",
          "sonar",
          "jira",
          "gocd",
          "rest",
          "csv",
          "console",
          "text",
          "parallel",
          "runtime");

  private static final JavaClasses MAIN =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages(ROOT);

  @Test
  void theImportSeesTheWholeCodebase() {
    assertThat(MAIN.size()).isGreaterThanOrEqualTo(MINIMUM_CLASSES);
  }

  @Test
  void everyDeclaredLayerPackageHoldsClasses() {
    for (String pkg : LAYER_PACKAGES) {
      assertThat(MAIN.stream().filter(c -> c.getPackageName().startsWith(ROOT + "." + pkg)))
          .as("classes in %s", pkg)
          .isNotEmpty();
    }
  }
}
