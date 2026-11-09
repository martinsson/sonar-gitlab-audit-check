package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;

/**
 * What a package shows to the others. A command is a package and its sub-packages (cli.activity,
 * cli.activity.model, ...): they are one tool, so the rules are about the command as a whole.
 */
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

  /** The command package a class belongs to: cli.activity for cli.activity.model.Api. */
  private static String commandPackage(JavaClass c) {
    String afterCli = c.getPackageName().substring((ROOT + "cli.").length());
    return ROOT + "cli." + afterCli.split("\\.", 2)[0];
  }

  /** The package itself or one of its sub-packages. */
  private static boolean inPackage(String pkg, String root) {
    return pkg.equals(root) || pkg.startsWith(root + ".");
  }

  /** A class of a command, other than its @Command class and the shared option groups. */
  private static boolean isCommandInternal(JavaClass c) {
    return inPackage(c.getPackageName(), ROOT + "cli")
        && !inPackage(c.getPackageName(), ROOT + "cli.shared")
        && !c.isAnnotatedWith(Command.class);
  }

  /** Every class that depends on the item lives in the item's command. */
  private static final class UsedOnlyFromItsCommand extends ArchCondition<JavaClass> {

    UsedOnlyFromItsCommand() {
      super("be used only from inside its own command");
    }

    @Override
    public void check(JavaClass item, ConditionEvents events) {
      String command = commandPackage(item);
      for (Dependency d : item.getDirectDependenciesToSelf()) {
        JavaClass user = d.getOriginClass();
        if (!inPackage(user.getPackageName(), command)) {
          events.add(
              SimpleConditionEvent.violated(
                  d,
                  user.getName()
                      + " uses "
                      + item.getName()
                      + ", which is not the @Command class of "
                      + command));
        }
      }
    }
  }

  @ArchTest
  static final ArchRule ONLY_THE_COMMAND_IS_USED_FROM_OUTSIDE_ITS_COMMAND =
      classes()
          .that()
          .resideInAPackage(ROOT + "cli..")
          .and()
          .resideOutsideOfPackage(ROOT + "cli.shared..")
          .and()
          .areNotAnnotatedWith(Command.class)
          .should(new UsedOnlyFromItsCommand())
          .because(
              "the @Command class is a tool's only entry point, the one its JBang script calls."
                  + " A command's other classes, its sub-packages included, are how the tool works"
                  + " inside: only its own command may depend on them, so each command can be"
                  + " read, changed and moved as one unit");

  /**
   * A public class of a command is used from another package of that command, so that it needs to
   * be public at all. This reads every class at once, because a use of a nested type
   * (Preflight.Instance) is a use of its outer classes too: they must be public to be named. A
   * compile-time constant is inlined and leaves no dependency, so such a constant is not a use.
   *
   * <p>A class that no other package uses is public by habit: keeping it package-private means the
   * next change to it cannot break anyone.
   */
  @Test
  void aPublicClassOfACommandIsUsedFromAnotherPackageOfIt() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(ROOT);
    Map<JavaClass, Set<String>> usingPackages = new HashMap<>();
    for (JavaClass user : classes) {
      for (Dependency d : user.getDirectDependenciesFromSelf()) {
        Optional<JavaClass> target = Optional.of(d.getTargetClass());
        while (target.isPresent()) {
          usingPackages
              .computeIfAbsent(target.get(), k -> new HashSet<>())
              .add(user.getPackageName());
          target = target.get().getEnclosingClass();
        }
      }
    }
    List<String> unused = new ArrayList<>();
    for (JavaClass c : classes) {
      if (!isCommandInternal(c) || !REACHABLE_FROM_ANOTHER_PACKAGE.test(c)) continue;
      String command = commandPackage(c);
      boolean used =
          usingPackages.getOrDefault(c, Set.of()).stream()
              .anyMatch(pkg -> !pkg.equals(c.getPackageName()) && inPackage(pkg, command));
      if (!used) unused.add(c.getName());
    }
    assertThat(unused)
        .as(
            "public classes that no other package of their command uses (make them"
                + " package-private): ")
        .isEmpty();
  }
}
