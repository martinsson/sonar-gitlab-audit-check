package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitAccessTarget;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The outside world comes in at the edge (ARCHITECTURE.md): the process streams, the environment,
 * the clock and sleeping are read in {@code runtime} and handed on; nothing else keeps state
 * between calls.
 */
@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class EdgeTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";
  private static final String RUNTIME = ROOT + "runtime..";

  @ArchTest
  static final ArchRule THE_PROCESS_IS_READ_ONLY_IN_RUNTIME_AND_MAIN =
      noClasses()
          .that()
          .resideOutsideOfPackage(RUNTIME)
          .should(
              touch(
                  "the process (System.out/err/in/exit/getenv/console, printStackTrace)",
                  EdgeTest::touchesTheProcess,
                  true))
          .because(
              "a class that prints on System.out or reads the environment cannot be tested"
                  + " without swapping global state, and two such tests cannot run side by side."
                  + " Host.system() reads them once; main may still exit the process");

  @ArchTest
  static final ArchRule TIME_AND_SLEEP_COME_FROM_RUNTIME =
      noClasses()
          .that()
          .resideOutsideOfPackage(RUNTIME)
          .should(touch("the system clock, zone or sleep", EdgeTest::readsTheSystemClock, false))
          .because(
              "a run has one clock and one zone, the Host's: a now() of its own or the"
                  + " system zone makes a report depend on the machine and the minute, and a real"
                  + " sleep makes a retry test wait. Pass a Clock, its zone, or a Sleeper");

  @ArchTest
  static final ArchRule ONLY_COMMANDS_SEE_THE_HOST =
      noClasses()
          .that()
          .resideOutsideOfPackages(ROOT + "cli..", RUNTIME)
          .or()
          .resideInAPackage(ROOT + "cli.shared..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(Host.class)
          .because(
              "a command takes the Host and hands each piece on separately: a Console to"
                  + " printers, a Clock to whatever needs the time, a Sleeper to the HTTP clients."
                  + " Code that receives the whole Host can reach anything, and says nothing"
                  + " about what it needs");

  @ArchTest
  static final ArchRule ONLY_MATCHING_REPORT_PRINTS =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "matching..")
          .and()
          .resideOutsideOfPackage(ROOT + "matching.report..")
          .should()
          .dependOnClassesThat()
          .belongToAnyOf(PrintStream.class)
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + "console..")
          .because(
              "the matcher computes and the report shows: a pairing that prints cannot be"
                  + " reused by a command that shows it differently, or not at all");

  /**
   * A static field is a constant: final, of a type whose values cannot change. Allowed: primitives
   * and their boxes, String, Pattern, DateTimeFormatter, the java.time values, BigDecimal, enums,
   * records made of these, and the List / Set / Map interfaces — built with List.of, Map.of, copyOf
   * or toList(), which the rule cannot see and review keeps honest. Arrays are never constants.
   * Jackson's ObjectMapper is allowed by name: configured once in its initialiser and only read
   * afterwards, which Jackson documents as thread-safe.
   */
  @ArchTest
  static final ArchRule STATIC_FIELDS_ARE_CONSTANTS =
      fields()
          .that()
          .areStatic()
          .and()
          .doNotHaveModifier(JavaModifier.SYNTHETIC)
          .should(beAConstant())
          .because(
              "static state outlives a run and is shared by every test of the JVM: a value"
                  + " set by one is read by the next. What changes belongs to an instance");

  @ArchTest
  static final ArchRule STATIC_INITIALISERS_DO_NO_IO =
      classes()
          .should(haveAStaticInitialiserWithoutIo())
          .because(
              "a static initialiser runs once per JVM, at a moment nobody chooses: a file it reads"
                  + " is read from wherever the JVM started, and a warning it prints lands in the"
                  + " middle of whatever runs first. Files are read where a command asks for them");

  // ----------------------------------------------------------------------
  // The process and the clock
  // ----------------------------------------------------------------------

  private static final Set<String> PROCESS_FIELDS = Set.of("out", "err", "in");
  private static final Set<String> PROCESS_METHODS =
      Set.of("exit", "getenv", "console", "setOut", "setErr", "setIn");

  private static boolean touchesTheProcess(JavaAccess<?> access) {
    String owner = access.getTargetOwner().getName();
    String name = access.getTarget().getName();
    if (owner.equals(System.class.getName())) {
      return PROCESS_FIELDS.contains(name) || PROCESS_METHODS.contains(name);
    }
    return name.equals("printStackTrace")
        && access.getTargetOwner().isAssignableTo(Throwable.class);
  }

  /** A call to {@code owner.name}, with no argument at all if {@code noArgument}. */
  private record Call(String owner, String name, boolean noArgument) {

    boolean madeBy(JavaAccess<?> access) {
      return access.getTargetOwner().getName().equals(owner)
          && access.getTarget().getName().equals(name)
          && (!noArgument || withoutArgument(access));
    }

    private static boolean withoutArgument(JavaAccess<?> access) {
      return access.getTarget() instanceof CodeUnitAccessTarget t
          && t.getRawParameterTypes().isEmpty();
    }
  }

  private static final List<Call> SYSTEM_TIME =
      Stream.concat(
              Stream.of(
                      "Instant",
                      "LocalDate",
                      "LocalDateTime",
                      "LocalTime",
                      "OffsetDateTime",
                      "OffsetTime",
                      "ZonedDateTime",
                      "Year",
                      "YearMonth",
                      "MonthDay")
                  .map(type -> new Call("java.time." + type, "now", true)),
              Stream.of(
                  new Call("java.lang.System", "currentTimeMillis", false),
                  new Call("java.time.Clock", "systemUTC", false),
                  new Call("java.time.Clock", "systemDefaultZone", false),
                  new Call("java.time.Clock", "system", false),
                  new Call("java.time.ZoneId", "systemDefault", false),
                  new Call("java.util.TimeZone", "getDefault", false),
                  new Call("java.util.Date", "<init>", true),
                  new Call("java.lang.Thread", "sleep", false)))
          .toList();

  private static boolean readsTheSystemClock(JavaAccess<?> access) {
    return SYSTEM_TIME.stream().anyMatch(call -> call.madeBy(access));
  }

  /** Classes that make one of {@code what} accesses; from a static main if {@code mainMayToo}. */
  private static ArchCondition<JavaClass> touch(
      String what, Predicate<JavaAccess<?>> forbidden, boolean mainMayToo) {
    return new ArchCondition<>("touch " + what) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        for (JavaAccess<?> access : javaClass.getAccessesFromSelf()) {
          if (!forbidden.test(access)) continue;
          if (mainMayToo && isMain(access.getOrigin())) continue;
          events.add(SimpleConditionEvent.satisfied(access, access.getDescription()));
        }
      }
    };
  }

  private static boolean isMain(JavaCodeUnit origin) {
    return origin.getName().equals("main") && origin.getModifiers().contains(JavaModifier.STATIC);
  }

  // ----------------------------------------------------------------------
  // Static state
  // ----------------------------------------------------------------------

  private static final Set<String> IMMUTABLE =
      Set.of(
          String.class.getName(),
          Pattern.class.getName(),
          DateTimeFormatter.class.getName(),
          Duration.class.getName(),
          Instant.class.getName(),
          LocalDate.class.getName(),
          LocalDateTime.class.getName(),
          OffsetDateTime.class.getName(),
          BigDecimal.class.getName(),
          Boolean.class.getName(),
          Character.class.getName(),
          Integer.class.getName(),
          Long.class.getName(),
          Double.class.getName(),
          List.class.getName(),
          Set.class.getName(),
          Map.class.getName(),
          ObjectMapper.class.getName());

  private static ArchCondition<JavaField> beAConstant() {
    return new ArchCondition<>("be final and of an immutable type") {
      @Override
      public void check(JavaField field, ConditionEvents events) {
        boolean ok =
            field.getModifiers().contains(JavaModifier.FINAL)
                && immutable(field.getRawType(), new HashSet<>());
        events.add(new SimpleConditionEvent(field, ok, field.getFullName() + " is not a constant"));
      }
    };
  }

  private static boolean immutable(JavaClass type, Set<String> seen) {
    if (type.isPrimitive() || type.isEnum() || IMMUTABLE.contains(type.getName())) return true;
    if (type.isArray() || !type.isRecord() || !seen.add(type.getName())) return false;
    return type.getFields().stream()
        .filter(f -> !f.getModifiers().contains(JavaModifier.STATIC))
        .allMatch(f -> immutable(f.getRawType(), seen));
  }

  // ----------------------------------------------------------------------
  // Static initialisers
  // ----------------------------------------------------------------------

  private static final List<String> IO_PACKAGES =
      List.of("java.io.", "java.nio.file.", "java.net.");

  /** A Path is a name, not a file: building one reads nothing. */
  private static final Set<String> NAMES = Set.of("java.nio.file.Path", "java.nio.file.Paths");

  private static final Call PROPERTY = new Call("java.lang.System", "getProperty", false);

  private static boolean isIo(JavaAccess<?> access) {
    String owner = access.getTargetOwner().getName();
    boolean io = !NAMES.contains(owner) && IO_PACKAGES.stream().anyMatch(owner::startsWith);
    return io || touchesTheProcess(access) || PROPERTY.madeBy(access);
  }

  /**
   * The static initialiser, and what it calls in this code base, transitively: Keys used to read a
   * YAML file through a helper, which a look at the initialiser alone would not see.
   */
  private static ArchCondition<JavaClass> haveAStaticInitialiserWithoutIo() {
    return new ArchCondition<>("have a static initialiser without I/O") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getStaticInitializer().stream()
            .flatMap(init -> ioReachedFrom(init).stream())
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName()
                                + " does I/O when it loads: "
                                + access.getDescription())));
      }
    };
  }

  /** The I/O that {@code start} does, itself or through what it calls in this code base. */
  private static List<JavaAccess<?>> ioReachedFrom(JavaCodeUnit start) {
    List<JavaAccess<?>> io = new ArrayList<>();
    Deque<JavaCodeUnit> todo = new ArrayDeque<>(List.of(start));
    Set<JavaCodeUnit> seen = new HashSet<>();
    while (!todo.isEmpty()) {
      JavaCodeUnit unit = todo.pop();
      if (!seen.add(unit)) continue;
      for (JavaAccess<?> access : unit.getAccessesFromSelf()) {
        if (isIo(access)) io.add(access);
        if (access.getTargetOwner().getPackageName().startsWith(ROOT)
            && access.getTarget() instanceof CodeUnitAccessTarget t) {
          t.resolveMember().ifPresent(todo::push);
        }
      }
    }
    return io;
  }
}
