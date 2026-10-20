package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Paging;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Petits utilitaires : dates de l'API, paramètres, et valeurs absentes qui ne sont pas des zéros.
 */
final class Values {

  private Values() {}

  /**
   * Sonar date les analyses avec un décalage : « 2026-08-16T05:43:07+0200 ». Tronquer à 19
   * caractères jette ce décalage et compare ensuite une heure locale à une heure distante — d'où
   * des âges faux de quelques heures, et un « il y a -1 jour » sur une analyse toute fraîche. On
   * lit donc le décalage quand il est présent et on ramène tout à l'heure du fuseau de la course.
   */
  static @Nullable LocalDateTime parseDate(@Nullable String s, ZoneId zone) {
    if (s == null || s.length() < 19) return null;
    try {
      return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
          .atZoneSameInstant(zone)
          .toLocalDateTime();
    } catch (DateTimeException ignored) {
      // pas de décalage (ou format inattendu) : on retombe sur l'heure nue
    }
    try {
      return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (DateTimeException e) {
      return null;
    }
  }

  /** {@code null} sur une valeur absente ou illisible : jamais 0. */
  static @Nullable Double numeric(@Nullable String s) {
    if (s == null || s.isBlank()) return null;
    try {
      return Double.parseDouble(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Paires clé, valeur ; une valeur null n'est pas envoyée (voir {@code Sonar.queryString}). */
  static Map<String, String> params(@Nullable String... pairs) {
    Map<String, String> m = new LinkedHashMap<>();
    for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }

  /** Point décimal quelle que soit la locale : le CSV est relu par d'autres outils. */
  static String dec(@Nullable Double d) {
    return d == null ? "" : String.format(Locale.ROOT, "%.2f", d);
  }

  static int orZero(@Nullable Integer i) {
    return i == null ? 0 : i;
  }

  static int totalOf(@Nullable Paging p) {
    return p == null ? 0 : orZero(p.total());
  }

  static String total(@Nullable Paging p) {
    return (p == null || p.total() == null) ? "?" : String.valueOf(p.total());
  }

  static int size(@Nullable Collection<?> c) {
    return c == null ? 0 : c.size();
  }

  static boolean isBlank(@Nullable String s) {
    return s == null || s.isBlank();
  }

  static boolean notEmpty(@Nullable Collection<?> c) {
    return c != null && !c.isEmpty();
  }

  static <T> List<T> orEmptyList(@Nullable List<T> l) {
    return l == null ? List.of() : l;
  }

  static String orEmpty(@Nullable String s) {
    return s == null ? "" : s;
  }

  static String orNa(@Nullable String s) {
    return isBlank(s) ? "n/a" : orEmpty(s);
  }

  static String truncate(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max);
  }

  static String shortName(String key) {
    String tail = key.substring(key.lastIndexOf(':') + 1);
    return truncate(tail, 50);
  }
}
