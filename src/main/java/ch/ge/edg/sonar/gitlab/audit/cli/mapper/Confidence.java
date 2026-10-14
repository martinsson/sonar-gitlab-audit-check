package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * MANUEL et REJETE ne sortent jamais du calcul : ils sont saisis dans un appariement déjà écrit, et
 * c'est ce qui les rend définitifs (voir {@link Replay}).
 */
public enum Confidence {
  EXACT,
  DERIVED,
  SUGGESTION,
  NONE,
  MANUEL,
  REJETE;

  private static final Pattern MARKS = Pattern.compile("\\p{M}");

  /** Ce qu'on écrit dans la colonne confiance, et qu'on relit. */
  public String code() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Confidence parse(String s) {
    String v =
        MARKS
            .matcher(Normalizer.normalize(s.trim(), Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT);
    return switch (v) {
      case "exact" -> EXACT;
      case "derived", "derive" -> DERIVED;
      case "suggestion" -> SUGGESTION;
      case "manuel", "manual", "confirme" -> MANUEL;
      case "rejete", "rejected" -> REJETE;
      default -> NONE;
    };
  }
}
