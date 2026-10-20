package ch.ge.edg.sonar.gitlab.audit.dora;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Les clés de tickets Jira citées dans un texte : ABC-123, entre deux séparateurs. Une clé trouvée
 * n'est qu'un candidat : UTF-8 ou ISO-8859 ont la même forme, seul Jira dit si le projet existe.
 */
public final class JiraKeys {

  private static final Pattern KEY =
      Pattern.compile("(?<![A-Za-z0-9_-])([A-Z][A-Z0-9_]{1,14})-([1-9][0-9]{0,6})(?![A-Za-z0-9])");

  private JiraKeys() {}

  /** Les clés dans l'ordre d'apparition, sans doublon. */
  public static Set<String> in(@Nullable String text) {
    Set<String> keys = new LinkedHashSet<>();
    if (text == null) return keys;
    Matcher m = KEY.matcher(text);
    while (m.find()) keys.add(m.group(1) + "-" + m.group(2));
    return keys;
  }

  /** Le projet d'une clé : ABC pour ABC-123. */
  public static String prefix(String key) {
    int dash = key.lastIndexOf('-');
    return dash < 0 ? key : key.substring(0, dash);
  }
}
