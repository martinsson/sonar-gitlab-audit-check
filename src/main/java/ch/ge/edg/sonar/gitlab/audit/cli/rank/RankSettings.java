package ch.ge.edg.sonar.gitlab.audit.cli.rank;

/**
 * Les seuils du classement, tels que la ligne de commande les a reçus.
 *
 * @param staleDays au-delà, un projet analysé est obsolète
 * @param abandonedDays au-delà, un projet obsolète est dormant
 * @param minNcloc en deçà, aucun ratio n'a de sens
 * @param topPercent part de chaque strate retenue
 * @param minSignals signaux minimum pour retenir un score
 */
public record RankSettings(
    int staleDays, int abandonedDays, int minNcloc, int topPercent, int minSignals) {}
