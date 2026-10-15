package ch.ge.edg.sonar.gitlab.audit.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks a test that pins behaviour believed to be wrong. The test asserts what the code does today,
 * so that refactoring cannot change it unnoticed; fixing the bug means changing this test on
 * purpose. {@link #value} says what looks wrong. Listed in QUALITY_LOG.md; run them all with {@code
 * mvn test -Dgroups=suspected-bug}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Tag("suspected-bug")
public @interface SuspectedBug {
  /** What looks wrong, and what a fix would change. */
  String value();
}
