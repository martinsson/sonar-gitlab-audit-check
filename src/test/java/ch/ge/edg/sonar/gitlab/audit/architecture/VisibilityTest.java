package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.conditions.ArchConditions.be;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
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

  /**
   * Public, and so is every class it is nested in. The members of an interface are public by the
   * language, but those of a package-private sealed interface are no more reachable than it is.
   */
  private static final DescribedPredicate<JavaClass> REACHABLE_FROM_ANOTHER_PACKAGE =
      DescribedPredicate.describe(
          "public (with every enclosing class)", VisibilityTest::reachableFromAnotherPackage);

  private static boolean reachableFromAnotherPackage(JavaClass c) {
    for (JavaClass k = c; k != null; k = k.getEnclosingClass().orElse(null)) {
      if (!k.getModifiers().contains(JavaModifier.PUBLIC)) return false;
    }
    return true;
  }

  @ArchTest
  static final ArchRule ONLY_THE_COMMAND_IS_PUBLIC_IN_A_COMMAND_PACKAGE =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "cli..")
          .and()
          .resideOutsideOfPackage(ROOT + "cli.shared..")
          .and()
          .areNotAnnotatedWith(Command.class)
          .should(be(REACHABLE_FROM_ANOTHER_PACKAGE))
          .because(
              "the @Command class is a tool's only entry point, the one its JBang script calls."
                  + " Everything else in the package is how the tool works inside: keeping it"
                  + " package-private means no other package can come to depend on it");
}
