package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import java.util.List;

/**
 * Packages of one layer leave each other alone. Test classes are imported too: a test lives in the
 * package of the code it tests and follows the same rules, except the shared {@code testing}
 * package, which belongs to no slice.
 */
@AnalyzeClasses(packages = "ch.ge.edg.sonar.gitlab.audit")
class SlicesTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";

  @ArchTest
  static final ArchRule COMMANDS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .matching(ROOT + "cli.(*)..")
          .should()
          .notDependOnEachOther()
          .ignoreDependency(alwaysTrue(), resideInAPackage(ROOT + "cli.shared.."))
          .because(
              "each tool can then be read, changed and deleted on its own. What two commands"
                  + " share moves down: option groups to cli.shared, logic to a domain package");

  @ArchTest
  static final ArchRule DOMAINS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .assignedFrom(topLevelPackages("matching", "exclusions", "dora"))
          .should()
          .notDependOnEachOther()
          .because(
              "a domain package is one set of concepts. Its sub-packages (matching.similarity,"
                  + " matching.report) belong to it; another domain is another subject, and"
                  + " mixing two of them is the job of the command that needs both");

  @ArchTest
  static final ArchRule CLIENTS_DO_NOT_DEPEND_ON_EACH_OTHER =
      slices()
          .assignedFrom(topLevelPackages("gitlab", "sonar", "jira", "gocd"))
          .should()
          .notDependOnEachOther()
          .because(
              "each client speaks to one external system. If GitLab's client knew Sonar's, an"
                  + " API change on one side could break a tool that only reads the other");

  @ArchTest
  static final ArchRule NO_PACKAGE_CYCLES =
      slices()
          .matching(ROOT + "(**)")
          .should()
          .beFreeOfCycles()
          // The shared test helpers (fakes of the external systems) build real clients and are used
          // by those clients' tests: that is test wiring, not a dependency between packages.
          .ignoreDependency(resideInAPackage(ROOT + "testing.."), alwaysTrue())
          .ignoreDependency(alwaysTrue(), resideInAPackage(ROOT + "testing.."))
          // Iteration 2 moves MappingFile's printing into matching.report and removes this
          // exception: until then MappingFile (matching) prints with matching.report's colours,
          // while matching.report reads matching's results.
          .ignoreDependency(
              nameIs(ROOT + "matching.MappingFile"), resideInAPackage(ROOT + "matching.report"))
          .because(
              "a cycle welds packages together: none of them can be understood, tested or"
                  + " moved without the others. Every package, sub-packages included, is a"
                  + " slice of its own here, so matching and matching.report may not loop either");

  /** One slice per listed top-level package (its sub-packages included); every other class out. */
  private static SliceAssignment topLevelPackages(String... names) {
    List<String> wanted = List.of(names);
    return new SliceAssignment() {
      @Override
      public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(ROOT)) {
          return SliceIdentifier.ignore();
        }
        String top = pkg.substring(ROOT.length()).split("\\.", 2)[0];
        return wanted.contains(top) ? SliceIdentifier.of(top) : SliceIdentifier.ignore();
      }

      @Override
      public String getDescription() {
        return "the top-level packages " + wanted;
      }
    };
  }

  /** The class itself and its nested classes. */
  private static DescribedPredicate<JavaClass> nameIs(String name) {
    return DescribedPredicate.describe(
        name, c -> c.getName().equals(name) || c.getName().startsWith(name + "$"));
  }
}
