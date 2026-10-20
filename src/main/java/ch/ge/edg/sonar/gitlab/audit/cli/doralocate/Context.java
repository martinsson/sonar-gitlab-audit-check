package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import java.io.PrintStream;
import java.time.Clock;
import java.time.ZoneId;

/**
 * Ce que partagent les deux parties de la recherche : où écrire, l'heure qu'il est, et les
 * commandes qu'elles proposent.
 *
 * @param out le rapport
 * @param err la progression et les avertissements
 * @param zone le fuseau des dates affichées
 */
record Context(
    PrintStream out, PrintStream err, Clock clock, ZoneId zone, Suggestions suggestions) {}
