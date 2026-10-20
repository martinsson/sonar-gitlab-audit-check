package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import picocli.CommandLine.Command;

/** What a package shows to the others. */
@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class VisibilityTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";

  @ArchTest
  static final ArchRule ONLY_THE_COMMAND_IS_PUBLIC_IN_A_COMMAND_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "cli..")
          .and()
          .resideOutsideOfPackage(ROOT + "cli.shared..")
          .and()
          .areNotAnnotatedWith(Command.class)
          .should()
          .bePublic()
          .because(
              "the @Command class is a tool's only entry point, the one its JBang script calls."
                  + " Everything else in the package is how the tool works inside: keeping it"
                  + " package-private means no other package can come to depend on it");
}
