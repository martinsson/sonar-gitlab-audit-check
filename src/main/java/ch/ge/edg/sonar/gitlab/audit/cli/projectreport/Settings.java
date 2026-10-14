package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.util.regex.Pattern;

/**
 * Ce que l'auditeur a choisi : fenêtre d'observation en jours, plafonds de rapatriement, taille de
 * l'échantillon de revue, et le sort réservé aux bots.
 */
public record Settings(
    int days, int maxCommits, int maxMrs, int sample, boolean includeBots, Pattern bots) {}
