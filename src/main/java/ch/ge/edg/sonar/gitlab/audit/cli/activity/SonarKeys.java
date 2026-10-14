package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.orEmpty;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Extraction de la clé Sonar : des propriétés, du YAML de CI, et des variables de GitLab. */
final class SonarKeys {

  /** sonar.projectKey=… dans un fichier de propriétés. */
  static final Pattern SONAR_KEY_PROP =
      Pattern.compile("(?m)^\\s*sonar\\.projectKey\\s*=\\s*(\\S+)\\s*$");

  /**
   * Les formes rencontrées dans un pipeline : l'option de ligne de commande, la variable que le
   * scanner lit, l'input du composant, et la propriété posée en clair. Cherchées dans cet ordre :
   * la ligne de commande gagne, c'est elle qui s'applique en dernier.
   */
  private static final List<Pattern> SONAR_KEY_CI =
      List.of(
          Pattern.compile("-Dsonar\\.projectKey=([^\\s'\"]+)"),
          Pattern.compile("(?m)^\\s*SONAR_PROJECT_KEY\\s*:\\s*[\"']?([^\\s'\"]+)"),
          // L'input du composant Sonar partagé, dans `include: component:
          // … inputs:`. Le repli ne déplie pas les composants : c'est la
          // seule trace de la surcharge qu'il puisse lire.
          Pattern.compile("(?m)^\\s*project_key\\s*:\\s*[\"']?([^\\s'\"]+)"),
          Pattern.compile("(?m)^\\s*sonar\\.projectKey\\s*[:=]\\s*[\"']?([^\\s'\"]+)"));

  /**
   * Ce qui reste d'une variable après expansion : $X, ${X}, %X%, et l'interpolation d'input `$[[
   * inputs.x ]]`, qu'un repli lit non dépliée et dont le motif de clé ne capture que « $[[ ».
   */
  private static final Pattern UNRESOLVED_VAR = Pattern.compile("\\$\\{?\\w+\\}?|\\$\\[\\[|%\\w+%");

  private SonarKeys() {}

  /**
   * La clé que la CI donne au scanner. Plusieurs motifs peuvent répondre dans le même YAML : le
   * composant Sonar écrit à la fois `-Dsonar.projectKey=$SONAR_PROJECT_KEY` et `SONAR_PROJECT_KEY:
   * <input>`. Le premier ne se calcule pas, le second si : on garde le premier candidat qui se
   * résout, et on ne signale une variable non résolue que si aucun ne l'est.
   *
   * <p>-Dsonar.projectKey=${CI_PROJECT_PATH_SLUG} est une clé, pas un échec : les variables
   * prédéfinies de GitLab se calculent ici, sans un appel de plus, et la clé résolue est celle que
   * le scanner a réellement envoyée.
   */
  static void ciKey(CiConfig.Ci ci, Proj p, CiFacts f) {
    String firstUnresolved = null;
    for (Pattern pat : SONAR_KEY_CI) {
      Matcher m = pat.matcher(ci.yaml());
      while (m.find()) {
        String raw = m.group(1);
        String expanded = expandPredefined(raw, p);
        if (!UNRESOLVED_VAR.matcher(expanded).find()) {
          f.sonarKey = expanded;
          f.keySource = ci.route() + (expanded.equals(raw) ? "" : " (variables)");
          return;
        }
        if (firstUnresolved == null) firstUnresolved = expanded;
      }
    }
    if (firstUnresolved != null) {
      // Une variable qu'on ne sait pas calculer : la clé est inconnue.
      // La publier telle quelle en ferait une fausse jointure côté
      // Sonar, ce qui est pire que pas de jointure.
      f.unresolvedKey = firstUnresolved;
      f.keySource = "variable non résolue";
    }
  }

  /**
   * Les variables prédéfinies que GitLab aurait substituées, calculées ici à partir de ce que
   * l'inventaire sait déjà du projet. Sans cela, `-Dsonar.projectKey=${CI_PROJECT_PATH_SLUG}` — la
   * forme la plus répandue dans un template partagé, puisqu'un template ne peut pas coder la clé en
   * dur — se lirait « clé inconnue » sur tout le parc, alors que c'est précisément le cas où elle
   * est calculable.
   */
  static String expandPredefined(String raw, Proj p) {
    String path = orEmpty(p.path);
    String out = raw;
    // CI_PROJECT_NAME est le répertoire du dépôt — le dernier segment du
    // chemin —, pas le nom affiché : celui-là est CI_PROJECT_TITLE.
    // Les confondre donnait « Service Actif » là où le scanner a
    // envoyé « service-actif ».
    // Les plus longs d'abord : $CI_PROJECT_PATH est un préfixe de
    // $CI_PROJECT_PATH_SLUG, $CI_PROJECT_NAME de $CI_PROJECT_NAMESPACE.
    // Un Map.of, sans ordre, remplaçait parfois le court en premier.
    for (var e :
        List.of(
            Map.entry("CI_PROJECT_PATH_SLUG", slug(p.path)),
            Map.entry("CI_PROJECT_NAMESPACE", orEmpty(p.namespace)),
            Map.entry("CI_PROJECT_TITLE", orEmpty(p.name)),
            Map.entry("CI_PROJECT_NAME", path.substring(path.lastIndexOf('/') + 1)),
            Map.entry("CI_PROJECT_PATH", path),
            Map.entry("CI_PROJECT_ID", String.valueOf(p.id)))) {
      out =
          out.replace("${" + e.getKey() + "}", e.getValue())
              .replace("$" + e.getKey(), e.getValue());
    }
    return out;
  }

  /** CI_PROJECT_PATH_SLUG : minuscules, tout le reste en tirets. */
  static String slug(@Nullable String path) {
    if (path == null || isBlank(path)) return "";
    return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
  }

  static @Nullable String firstMatch(Pattern pat, @Nullable String text) {
    if (text == null) return null;
    Matcher m = pat.matcher(text);
    return m.find() ? m.group(1) : null;
  }

  static @Nullable String unquote(@Nullable String s) {
    return s == null ? null : s.replaceAll("^['\"]|['\",]$", "");
  }
}
