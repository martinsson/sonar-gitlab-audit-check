package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import java.time.ZoneId;

/**
 * Ce que l'extraction lit de GoCD, et dans quel fuseau elle date ce qu'elle écrit.
 *
 * @param maxUpstream appels de runs amont permis pour un seul run
 * @param durations lire chaque stage pour sa fin (un appel de plus par stage)
 * @param zone fuseau des dates écrites
 */
record Reading(int maxUpstream, boolean durations, ZoneId zone) {}
