package ch.ge.edg.sonar.gitlab.audit.dora;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * La plateforme qu'un nom de pipeline, de stage ou d'environnement laisse supposer : prod, recette,
 * dev… Une supposition, affichée comme telle, que chaque projet doit valider.
 */
public final class Platforms {

  private static final Map<String, String> WORDS =
      Map.ofEntries(
          Map.entry("prod", "prod"),
          Map.entry("prd", "prod"),
          Map.entry("production", "prod"),
          Map.entry("preprod", "preprod"),
          Map.entry("pprod", "preprod"),
          Map.entry("ppr", "preprod"),
          Map.entry("rec", "recette"),
          Map.entry("recette", "recette"),
          Map.entry("rct", "recette"),
          Map.entry("qual", "recette"),
          Map.entry("qualif", "recette"),
          Map.entry("uat", "recette"),
          Map.entry("val", "recette"),
          Map.entry("validation", "recette"),
          Map.entry("homol", "recette"),
          Map.entry("int", "integration"),
          Map.entry("integ", "integration"),
          Map.entry("integration", "integration"),
          Map.entry("test", "test"),
          Map.entry("tst", "test"),
          Map.entry("dev", "dev"),
          Map.entry("develop", "dev"));

  private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

  private Platforms() {}

  private static List<String> tokens(String name) {
    return SEPARATORS.splitAsStream(name.toLowerCase(Locale.ROOT)).toList();
  }

  /** Les plateformes reconnues, séparées par « | », dans l'ordre où les noms les citent. */
  public static String guess(String... names) {
    Set<String> found = new LinkedHashSet<>();
    for (String name : names) {
      for (String token : tokens(name)) {
        String platform = WORDS.get(token);
        if (platform != null) found.add(platform);
      }
    }
    return String.join("|", found);
  }

  /**
   * Les mots du nom qui désignent une plateforme, tels qu'écrits (pour compter les conventions).
   */
  public static Set<String> words(String name) {
    Set<String> found = new LinkedHashSet<>();
    for (String token : tokens(name)) {
      if (WORDS.containsKey(token)) found.add(token);
    }
    return found;
  }
}
