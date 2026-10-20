package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class SharedOptionsTest {

  private static final DescribedPredicate<JavaClass> DECLARES_AN_OPTION =
      new DescribedPredicate<>("declare a picocli @Option or @Mixin") {
        @Override
        public boolean test(JavaClass c) {
          for (JavaField f : c.getFields())
            if (f.isAnnotatedWith(Option.class) || f.isAnnotatedWith(Mixin.class)) return true;
          return false;
        }
      };

  private static final DescribedPredicate<JavaClass> IS_A_CONVERTER =
      JavaClass.Predicates.assignableTo(ITypeConverter.class).as("be a picocli ITypeConverter");

  @ArchTest
  static final ArchRule SHARED_HOLDS_ONLY_OPTION_GROUPS_AND_CONVERTERS =
      classes()
          .that()
          .resideInAPackage("ch.ge.edg.sonar.gitlab.audit.cli.shared..")
          .should(ArchCondition.from(IS_A_CONVERTER.or(DECLARES_AN_OPTION)))
          .because(
              "cli.shared exists for the options several commands repeat: a class there that"
                  + " is neither an option group nor a converter is logic that belongs to a"
                  + " domain package");

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";
  private static final String SHARED = ROOT + "cli.shared..";

  @ArchTest
  static final ArchRule SHARED_DEPENDS_ONLY_ON_OPTION_PLUMBING =
      classes()
          .that()
          .resideInAPackage(SHARED)
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage(
              SHARED,
              "picocli..",
              "org.jspecify..",
              "java.lang..",
              "java.util..",
              "java.nio.file..",
              "java.io",
              ROOT + "matching..",
              ROOT + "exclusions..",
              ROOT + "gitlab..",
              ROOT + "console..",
              ROOT + "text..")
          .because(
              "an option group resolves what the command line said into a value the commands"
                  + " use, nothing else");

  @ArchTest
  static final ArchRule SHARED_DOES_NO_IO_BEYOND_PATHS =
      noClasses()
          .that()
          .resideInAPackage(SHARED)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("java.net..", "javax.net..")
          .orShould()
          .callMethodWhere(callsIo())
          .because("reading or writing a file is the work of a command or a domain package");

  private static DescribedPredicate<JavaCall<?>> callsIo() {
    return DescribedPredicate.describe(
        "java.nio.file.Files or java.io streams",
        (JavaCall<?> call) -> {
          String owner = call.getTargetOwner().getName();
          return owner.equals("java.nio.file.Files")
              || (owner.startsWith("java.io.") && !owner.equals("java.io.PrintStream"));
        });
  }
}
