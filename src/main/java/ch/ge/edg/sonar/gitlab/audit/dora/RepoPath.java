package ch.ge.edg.sonar.gitlab.audit.dora;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Le chemin d'un dépôt git, sans schéma, identifiants, hôte ni « .git » : https://u@h/g/p.git et
 * git@h:g/p.git donnent tous deux « g/p ». C'est ce qu'on compare au path_with_namespace de GitLab,
 * pour reconnaître un dépôt quelle que soit la forme de son URL.
 */
public final class RepoPath {

  private static final Pattern SSH =
      Pattern.compile("^(?:ssh://)?[^@/]+@[^:/]+(?::\\d+)?[:/](.+)$");

  private RepoPath() {}

  /** En minuscules, pour comparer. */
  public static String of(@Nullable String url) {
    return keepCase(url).toLowerCase(Locale.ROOT);
  }

  /** Sans changer la casse, pour interroger GitLab avec le chemin tel qu'il a été donné. */
  public static String keepCase(@Nullable String url) {
    if (url == null) return "";
    String u = url.trim();
    Matcher ssh = SSH.matcher(u);
    if (ssh.find()) u = ssh.group(1);
    else if (u.contains("://")) u = u.replaceFirst("^[A-Za-z+]+://[^/]*", "");
    return u.replaceFirst("/-/.*$", "")
        .replaceAll("/+$", "")
        .replaceAll("\\.git$", "")
        .replaceAll("^/+", "");
  }

  /**
   * Vrai si {@code url} désigne le projet {@code pathWithNamespace}, y compris sous un sous-chemin.
   */
  public static boolean matches(@Nullable String url, String pathWithNamespace) {
    String path = of(url);
    String wanted = pathWithNamespace.toLowerCase(Locale.ROOT);
    return !wanted.isEmpty() && (path.equals(wanted) || path.endsWith("/" + wanted));
  }
}
