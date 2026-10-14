package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.util.concurrent.atomic.AtomicInteger;

/** Les effectifs de l'entonnoir, publiés dans la synthèse. */
final class Counters {
  boolean enterprise;
  int inScope, kept, fresh, eligible, selected, floor;
  int sampleSize, sampleLeaks, commitPassed, graphqlCalls, notCounted;
  // Écrits depuis la passe profonde, qui tourne en parallèle.
  final AtomicInteger ciFromCacheN = new AtomicInteger();
  final AtomicInteger ciByLintN = new AtomicInteger();
  final AtomicInteger ciByFallbackN = new AtomicInteger();
  volatile int lintRefusedStatus;
}
