package ch.ge.edg.sonar.gitlab.audit.gocd;

import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Une révision de matériau pipeline, « build/12/stage/1 » : le run amont qu'un run a pris.
 *
 * @param counter le compteur du run amont, toujours positif
 */
public record Upstream(String pipeline, int counter) {

  private static final Pattern SLASH = Pattern.compile("/");

  /** null si la révision n'a pas cette forme. */
  public static @Nullable Upstream parse(@Nullable String revision) {
    if (revision == null) return null;
    List<String> parts = SLASH.splitAsStream(revision).toList();
    if (parts.size() < 2 || parts.get(0).isBlank()) return null;
    try {
      int counter = Integer.parseInt(parts.get(1).trim());
      return counter > 0 ? new Upstream(parts.get(0), counter) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public String toString() {
    return pipeline + "/" + counter;
  }
}
