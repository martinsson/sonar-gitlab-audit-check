package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.util.concurrent.atomic.AtomicInteger;

/** Ce que la passe profonde compte en chemin. */
final class Counters {
  boolean enterprise;
  // Écrits depuis la passe profonde, qui tourne en parallèle.
  final AtomicInteger ciFromCacheN = new AtomicInteger();
  final AtomicInteger ciByLintN = new AtomicInteger();
  final AtomicInteger ciByFallbackN = new AtomicInteger();
  volatile int lintRefusedStatus;
}
