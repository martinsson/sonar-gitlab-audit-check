package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import java.time.Duration;
import org.jspecify.annotations.Nullable;

/**
 * Les niveaux de performance DORA (rapport 2023), avec les seuils que le tableau de bord DORA
 * d'Apache DevLake applique, pour que la page et DevLake classent pareil une même valeur.
 */
final class Bands {

  /** Un niveau, du meilleur au moins bon. */
  enum Band {
    ELITE("élite"),
    HIGH("haut"),
    MEDIUM("moyen"),
    LOW("bas");

    private final String label;

    Band(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /** Une fois par mois, en déploiements par semaine. */
  private static final double MONTHLY = 7.0 / 30;

  private Bands() {}

  /** Au moins cinq par semaine, une par semaine, une par mois. */
  static Band frequency(double perWeek) {
    if (perWeek >= 5) return Band.ELITE;
    if (perWeek >= 1) return Band.HIGH;
    if (perWeek >= MONTHLY) return Band.MEDIUM;
    return Band.LOW;
  }

  /** Moins d'un jour, d'une semaine, d'un mois. */
  static @Nullable Band leadTime(@Nullable Duration d) {
    if (d == null) return null;
    if (d.compareTo(Duration.ofDays(1)) < 0) return Band.ELITE;
    if (d.compareTo(Duration.ofDays(7)) < 0) return Band.HIGH;
    if (d.compareTo(Duration.ofDays(30)) < 0) return Band.MEDIUM;
    return Band.LOW;
  }

  /** Au plus 5 %, 10 %, 15 %. */
  static @Nullable Band failureRate(@Nullable Double ratio) {
    if (ratio == null) return null;
    if (ratio <= 0.05) return Band.ELITE;
    if (ratio <= 0.10) return Band.HIGH;
    if (ratio <= 0.15) return Band.MEDIUM;
    return Band.LOW;
  }

  /** Moins d'une heure, d'un jour, d'une semaine. */
  static @Nullable Band restore(@Nullable Duration d) {
    if (d == null) return null;
    if (d.compareTo(Duration.ofHours(1)) < 0) return Band.ELITE;
    if (d.compareTo(Duration.ofDays(1)) < 0) return Band.HIGH;
    if (d.compareTo(Duration.ofDays(7)) < 0) return Band.MEDIUM;
    return Band.LOW;
  }
}
