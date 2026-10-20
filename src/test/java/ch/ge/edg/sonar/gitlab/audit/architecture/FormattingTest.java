package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Numbers and dates are formatted with an explicit locale, only in {@code console}, {@code text}
 * and the CSV column formatters (ARCHITECTURE.md). The default locale is the machine's: a French
 * one writes 1,5 in a report and in a CSV another tool reads back.
 */
@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class FormattingTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";
  private static final String[] FORMATTERS = {ROOT + "console..", ROOT + "csv..", ROOT + "text.."};

  private static final Set<String> STREAMS = Set.of("java.io.PrintStream", "java.io.PrintWriter");

  @ArchTest
  static final ArchRule NOTHING_FORMATS_WITH_THE_DEFAULT_LOCALE =
      classes()
          .should(
              notCall(
                  "a formatter that takes the default locale"
                      + " (String.format/formatted, printf/format, ofPattern, NumberFormat,"
                      + " Formatter without a Locale)",
                  FormattingTest::usesTheDefaultLocale))
          .because(
              "the report and the CSV must read the same on every machine: pass Locale.ROOT, or"
                  + " go through Text.format");

  @ArchTest
  static final ArchRule ONLY_CONSOLE_CSV_AND_TEXT_FORMAT =
      classes()
          .that()
          .resideOutsideOfPackages(FORMATTERS)
          .should(notCall("String.format or printf/format", FormattingTest::formatsText))
          .because(
              "formatting lives in one place, which pins the locale: build the line with"
                  + " Text.format and print it");

  /** A call to {@code owner.name}; with no Locale among its parameters if {@code localeless}. */
  private record Call(String owner, String name, boolean localeless) {

    boolean madeBy(JavaAccess<?> access) {
      return access.getTargetOwner().getName().equals(owner)
          && access.getTarget().getName().equals(name)
          && !(localeless && takesALocale(access));
    }
  }

  private static final List<Call> DEFAULT_LOCALE =
      List.of(
          new Call("java.lang.String", "format", true),
          new Call("java.lang.String", "formatted", false),
          new Call("java.io.PrintStream", "printf", true),
          new Call("java.io.PrintStream", "format", true),
          new Call("java.io.PrintWriter", "printf", true),
          new Call("java.io.PrintWriter", "format", true),
          new Call("java.time.format.DateTimeFormatter", "ofPattern", true),
          new Call("java.text.NumberFormat", "getInstance", true),
          new Call("java.text.NumberFormat", "getNumberInstance", true),
          new Call("java.text.NumberFormat", "getPercentInstance", true),
          new Call("java.text.NumberFormat", "getIntegerInstance", true),
          new Call("java.text.DecimalFormat", "<init>", false),
          new Call("java.util.Formatter", "<init>", true));

  /** A locale-less formatting call: the default locale decides the decimal separator. */
  private static boolean usesTheDefaultLocale(JavaAccess<?> access) {
    return DEFAULT_LOCALE.stream().anyMatch(call -> call.madeBy(access));
  }

  /** String.format or a stream's printf/format, with a locale or not. */
  private static boolean formatsText(JavaAccess<?> access) {
    String owner = access.getTargetOwner().getName();
    String name = access.getTarget().getName();
    if (owner.equals("java.lang.String")) return name.equals("format") || name.equals("formatted");
    return STREAMS.contains(owner) && (name.equals("printf") || name.equals("format"));
  }

  private static boolean takesALocale(JavaAccess<?> access) {
    return access.getTarget() instanceof CodeUnitAccessTarget t
        && t.getRawParameterTypes().stream().anyMatch(p -> p.isEquivalentTo(Locale.class));
  }

  /** Satisfied when the class makes none of the {@code forbidden} calls. */
  private static ArchCondition<JavaClass> notCall(String what, Predicate<JavaAccess<?>> forbidden) {
    return new ArchCondition<>("not call " + what) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
          if (forbidden.test(access)) {
            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
          }
        }
      }
    };
  }
}
