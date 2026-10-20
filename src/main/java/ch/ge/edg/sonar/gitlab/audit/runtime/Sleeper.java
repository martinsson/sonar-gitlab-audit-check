package ch.ge.edg.sonar.gitlab.audit.runtime;

import java.time.Duration;

/** Attendre avant un nouvel essai. Le seul sommeil du programme ; les tests n'attendent pas. */
@FunctionalInterface
public interface Sleeper {

  void sleep(Duration pause) throws InterruptedException;

  /** Le vrai sommeil, celui du fil courant. */
  static Sleeper system() {
    return pause -> Thread.sleep(pause);
  }
}
