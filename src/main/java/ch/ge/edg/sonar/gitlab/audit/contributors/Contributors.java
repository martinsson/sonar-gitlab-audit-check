package ch.ge.edg.sonar.gitlab.audit.contributors;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Qui a contribué, et ce qui n'en est pas une contribution humaine : les trois conventions que les
 * outils d'activité GitLab partagent (les bots, la personne derrière plusieurs adresses, les
 * commits de fusion). Ce module ne parle à aucune API : il reçoit des champs déjà lus.
 */
public final class Contributors {

  private Contributors() {}

  /**
   * Non filtrés, Renovate et consorts finissent premiers contributeurs et faussent tout : cadence,
   * concentration, taille des commits.
   *
   * <p>Le motif est l'union de ceux que les deux outils s'étaient donnés séparément. Il reste
   * étroit à dessein : « bot » collé à d'autres lettres attraperait Abbot ou Talbot, d'où les
   * bornes autour du mot nu. Les identités écartées se rapportent, elles ne se cachent pas.
   */
  public static final String BOT_PATTERN =
      "(?i)(renovate|dependabot|semantic-release|\\[bot\\]|bot@|gitlab-ci-token"
          + "|jenkins|sonarqube|automation)"
          + "|(^|[^\\p{L}])bots?([^\\p{L}]|$)"
          + "|(^|[^\\p{L}])project_\\d+_bot";

  public static boolean isBot(Pattern bots, @Nullable String email, @Nullable String name) {
    return bots.matcher(Text.orEmpty(email) + " " + Text.orEmpty(name)).find();
  }

  /**
   * Deux adresses pour la même personne fragmentent le compte d'auteurs. On normalise sur la partie
   * locale de l'adresse, à défaut sur le nom. Le résultat vaut à ±1 : il alimente un jugement de
   * bus factor, il lui suffit d'être juste entre 1 et 5.
   */
  public static String identity(@Nullable String email, @Nullable String name) {
    String raw =
        (email != null && email.contains("@")) ? email.substring(0, email.indexOf('@')) : name;
    if (raw == null || raw.isBlank()) return "?";
    // Les deux branches doivent atterrir dans le même espace de noms : sinon
    // « Dev 0 » (commit sans adresse) et « dev0@example.com » comptent pour
    // deux personnes, et le bus factor double silencieusement.
    String norm = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    return norm.isEmpty() ? "?" : norm;
  }

  /**
   * Un commit à plus d'un parent est une fusion. Il ne porte aucun travail propre — ses lignes sont
   * celles des commits qu'il réunit — et sa date est celle du clic sur « fusionner », par quelqu'un
   * qui n'a pas écrit le code. Mesuré sur gitlab-org/cli : les compter changeait le contributeur
   * principal, de la personne qui écrit à celle qui fusionne.
   *
   * @param parentIds les identifiants des parents du commit ; absent vaut aucun parent
   */
  public static boolean isMerge(@Nullable List<String> parentIds) {
    return parentIds != null && parentIds.size() > 1;
  }
}
