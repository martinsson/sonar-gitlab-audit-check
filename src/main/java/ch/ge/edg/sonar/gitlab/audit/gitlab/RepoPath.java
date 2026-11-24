package ch.ge.edg.sonar.gitlab.audit.gitlab;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Le chemin GitLab, « groupe/projet », que désigne une URL git : sans schéma, identifiants, hôte, «
 * .git », « /-/… » ni requête. https://u@h/g/p.git, git@h:g/p.git, ssh://git@h:2222/g/p et
 * https://h/g/p/-/tree/main donnent tous « g/p ». C'est la seule façon dont l'outil lit une URL :
 * l'URL d'un dépôt pour reconnaître un projet GitLab (matching, dora), ou la référence donnée à
 * l'outil pour interroger GitLab.
 *
 * <p>La casse est gardée ; {@link #matches} compare en minuscules.
 */
public final class RepoPath {

  private static final Pattern SSH =
      Pattern.compile("^(?:ssh://)?[^@/]+@[^:/]+(?::\\d+)?[:/](.+)$");

  private RepoPath() {}

  /** Le chemin tel qu'il est écrit dans l'URL, sans schéma, hôte, « .git », « /-/… » ni « ?x ». */
  public static String of(@Nullable String url) {
    if (url == null) return "";
    String u = url.trim();
    Matcher ssh = SSH.matcher(u);
    if (ssh.find()) u = ssh.group(1);
    else if (u.contains("://")) u = u.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*", "");
    return u.replaceFirst("[?#].*$", "")
        .replaceFirst("/-/.*$", "")
        .replaceAll("/+$", "")
        .replaceAll("\\.git$", "")
        .replaceAll("/+$", "")
        .replaceAll("^/+", "");
  }

  /**
   * Vrai si {@code url} désigne le projet {@code pathWithNamespace}, y compris sous un sous-chemin.
   * La casse ne compte pas.
   */
  public static boolean matches(@Nullable String url, String pathWithNamespace) {
    String path = of(url).toLowerCase(Locale.ROOT);
    String wanted = pathWithNamespace.toLowerCase(Locale.ROOT);
    return !wanted.isEmpty() && (path.equals(wanted) || path.endsWith("/" + wanted));
  }
}
