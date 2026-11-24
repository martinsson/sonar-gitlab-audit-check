package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Le titre d'un commit, lu comme un commit conventionnel : {@code type(scope)!: [ABC-123] message}.
 * Ce qu'il dit de sa fonctionnalité ({@link DeliveredFeatures}).
 *
 * @param type en minuscules ; {@link #NO_TYPE} pour un titre hors convention
 * @param key la clé Jira entre crochets juste après le type ; aucune sinon
 */
record CommitTitle(String type, @Nullable String key) {

  /** Le type d'un commit qui ne suit pas la convention, dans les comptes. */
  static final String NO_TYPE = "(sans type)";

  /** La règle de #51, telle quelle. */
  private static final Pattern CONVENTIONAL =
      Pattern.compile("^(\\w+)(\\([^)]*\\))?!?:\\s*(\\[([A-Z][A-Z0-9]+-\\d+)\\])?");

  /** Les commits que la CI pose pour tagger et pour préparer la version suivante. */
  private static final Pattern CI_RELEASE = Pattern.compile("^ci\\(release\\)!?:");

  /** « Merge branch 'feature/ABC-1_x' into 'main' », la forme de GitLab ; sans « into », git. */
  private static final Pattern MERGE =
      Pattern.compile(
          "^Merge (?:remote-tracking )?branch '([^']+)'(?: of \\S+)?(?: into '?([^'\\s]+)'?)?");

  /** La clé d'une branche : feature/ABC-123_quelque-chose ou bugfix/ABC-123_…. */
  private static final Pattern BRANCH_KEY =
      Pattern.compile("^(?:feature|bugfix)/([A-Z][A-Z0-9]+-\\d+)");

  /** Les seuls types qui comptent sans story. */
  private static final Set<String> WORK = Set.of("feat", "fix");

  static CommitTitle of(String title) {
    Matcher m = CONVENTIONAL.matcher(title);
    if (!m.find()) return new CommitTitle(NO_TYPE, null);
    return new CommitTitle(m.group(1).toLowerCase(Locale.ROOT), m.group(4));
  }

  /** Un feat: ou un fix:, qui compte même sans story. */
  boolean work() {
    return WORK.contains(type);
  }

  /** Un commit {@code ci(release):}, que la CI pose pour tagger. */
  static boolean ciRelease(String title) {
    return CI_RELEASE.matcher(title).find();
  }

  /**
   * La branche qu'un commit de fusion a fusionnée dans la branche par défaut ; aucune pour une
   * autre fusion, dont celle de la branche par défaut dans une branche.
   */
  static @Nullable String mergedBranch(String title, String defaultBranch) {
    Matcher m = MERGE.matcher(title);
    if (!m.find()) return null;
    String source = m.group(1);
    String target = m.group(2);
    if (target != null && !isBranch(target, defaultBranch)) return null;
    return isBranch(source, defaultBranch) ? null : source;
  }

  private static boolean isBranch(String ref, String branch) {
    return ref.equals(branch)
        || ref.equals("refs/heads/" + branch)
        || ref.equals("origin/" + branch)
        || ref.equals("refs/remotes/origin/" + branch);
  }

  /** La clé Jira d'un nom de branche ; aucune sans clé. */
  static @Nullable String branchKey(String branch) {
    Matcher m = BRANCH_KEY.matcher(branch);
    return m.find() ? m.group(1) : null;
  }
}
