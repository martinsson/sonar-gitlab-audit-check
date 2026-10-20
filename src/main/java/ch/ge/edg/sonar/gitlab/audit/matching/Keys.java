package ch.ge.edg.sonar.gitlab.audit.matching;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Clés Sonar, chemins GitLab et liens de dépôt ramenés à une forme comparable des deux côtés. */
public final class Keys {

  private static final Pattern SLASH = Pattern.compile("/");
  private static final Pattern LINK_SEPARATOR = Pattern.compile(" \\| ");
  private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
  private static final Pattern DIGITS = Pattern.compile("\\d+");
  private static final Pattern KEY_PREFIX = Pattern.compile("^[A-Za-z0-9]+[._/-]");

  private Keys() {}

  /**
   * Les clés s'écrivent {@code groupe:projet}, {@code groupe_projet} ou {@code groupe-projet} selon
   * qui les a créées ; un chemin s'écrit {@code groupe/projet}. Tout séparateur devient le même, la
   * casse tombe.
   */
  public static String normalise(String s) {
    return trimDashes(NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll("-"));
  }

  static String trimDashes(String s) {
    return EDGE_DASHES.matcher(s).replaceAll("");
  }

  static String[] segments(String path) {
    return SLASH.split(path);
  }

  /**
   * Les chemins de dépôt que désigne une liste de liens `type url | type url`.
   * `https://hote/groupe/projet.git`, `git@hote:groupe/projet`,
   * `https://hote/groupe/projet/-/tree/main` donnent tous `groupe/projet`. L'hôte est ignoré :
   * l'autre côté ne le porte pas.
   */
  public static List<String> linkedPaths(@Nullable String links) {
    List<String> out = new ArrayList<>();
    if (links == null || links.isBlank()) return out;
    // Les entrées vides, en fin de liste ou non, ne donnent aucun chemin.
    for (String entry : LINK_SEPARATOR.split(links, -1)) {
      String url = entry.contains(" ") ? entry.substring(entry.indexOf(' ') + 1) : entry;
      String path =
          url.trim()
              .replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+/", "")
              .replaceFirst("^[^@/\\s]+@[^:/]+:", "")
              .replaceFirst("/-/.*$", "")
              .replaceFirst("[?#].*$", "")
              .replaceFirst("\\.git/?$", "")
              .replaceFirst("/+$", "")
              .toLowerCase(Locale.ROOT);
      if (path.contains("/") && !path.contains("://") && !out.contains(path)) out.add(path);
    }
    return out;
  }

  /**
   * Mapping namespace GitLab → préfixe Sonar, puis extraction du nom court (dernier segment, sans
   * numéro de ticket) pour construire une clé Sonar approximative.
   *
   * <p>Méthode en deux niveaux :
   *
   * <ol>
   *   <li>leftValue() retourne le nom court seul (sans namespace) pour matcher contre la partie
   *       après le ":" dans les clés Sonar.
   *   <li>rightValues() retourne la clé normalisée + variantes avec namespace GitLab reconstruit.
   * </ol>
   *
   * <p>Exemples :
   *
   * <ul>
   *   <li>DEVELOPPEUR-LOGEMENT/6496-sidlo/sidlo → sidlo
   *   <li>DEVELOPPEUR-AFC/afc.refonte/AFC_rtaxpm → afc-rtaxpm
   *   <li>DEVELOPPEUR-PHP/9949-fao → fao
   *   <li>INFRA/terraform-module-openshift-x → terraform-module-openshift-x
   * </ul>
   */
  public static String mapPathToSonarKey(@Nullable String gitlabPath) {
    if (gitlabPath == null || gitlabPath.isBlank()) return "";
    String[] parts = segments(gitlabPath);
    if (parts.length < 2) return normalise(gitlabPath);

    // Si le chemin a des segments intermédiaires, les inclure
    // mais sans le namespace GitLab (qui ne correspond pas au prefix Sonar)
    if (parts.length >= 3) {
      StringBuilder sb = new StringBuilder();
      for (int i = 1; i < parts.length; i++) {
        String clean = stripTicketNumber(parts[i]);
        if (!clean.isEmpty()) sb.append('-').append(normalise(clean));
      }
      return trimDashes(sb.toString());
    }

    // Nom court = dernier segment, sans numéro de ticket en début
    return normalise(stripTicketNumber(parts[parts.length - 1]));
  }

  /**
   * Version "droite" : pour les clés Sonar, on retourne la clé normalisée + le nom court extrait
   * (sans namespace) pour matcher avec le chemin GitLab correspondant.
   *
   * <p>Exemples :
   *
   * <ul>
   *   <li>ch.ge.logement.sidlo:sidlo → [ch-ge-logement-sidlo-sidlo, sidlo]
   *   <li>ch.ge.afc.refonte.rtaxpm:AFC_rtaxpm → [ch-ge-afc-refonte-rtaxpm-afc-rtaxpm, afc-rtaxpm]
   *   <li>9949-fao → [9949-fao, fao]
   * </ul>
   *
   * <p>Une clé null lève NullPointerException avant le test qui la prévoit : c'est le comportement
   * d'origine, que les tests figent.
   */
  public static List<String> mappedSonarKey(@Nullable String sonarKey) {
    List<String> result = new ArrayList<>();
    if (sonarKey == null) return result;
    result.add(normalise(sonarKey));

    if (sonarKey.isBlank()) return result;

    // Extraire le nom court (après le ":" ou dernier segment)
    String shortName = extractShortName(sonarKey);
    if (!shortName.isEmpty() && !shortName.equals(sonarKey)) {
      result.add(normalise(shortName));
    }

    return result;
  }

  /**
   * Extrait le nom court d'une clé Sonar : après le ":" ou le dernier segment. {@code
   * ch.ge.afc.refonte.rtaxpm:AFC_rtaxpm} → {@code AFC_rtaxpm} {@code ch.ge.ael:ge-televerse-back} →
   * {@code ge-televerse-back} {@code 9949-fao} → {@code fao} (sans numéro de ticket)
   */
  public static String extractShortName(String sonarKey) {
    // D'abord essayer après le ":"
    int colon = sonarKey.indexOf(':');
    if (colon >= 0) {
      return stripTicketNumber(sonarKey.substring(colon + 1).trim());
    }
    // Sinon, le nom après le dernier "-" ou "_" (sans numéro de ticket)
    int lastSep = Math.max(sonarKey.lastIndexOf('-'), sonarKey.lastIndexOf('_'));
    if (lastSep >= 0) {
      return stripTicketNumber(sonarKey.substring(lastSep + 1).trim());
    }
    return stripTicketNumber(sonarKey.trim());
  }

  /** Retire un numéro de ticket en début de nom : {@code 9949-fao} → {@code fao}. */
  public static String stripTicketNumber(String name) {
    String trimmed = name.trim();
    int dash = trimmed.indexOf('-');
    if (dash > 0 && DIGITS.matcher(trimmed.substring(0, dash)).matches()) {
      return trimmed.substring(dash + 1);
    }
    return trimmed;
  }

  /** `ch.ge.afc.refonte:AFC_raff2` → `ch.ge.afc.refonte:` ; `dsi_paie_front` → `dsi_`. */
  public static String prefix(String key) {
    int colon = key.lastIndexOf(':');
    if (colon > 0) return key.substring(0, colon + 1);
    Matcher m = KEY_PREFIX.matcher(key);
    return m.find() ? m.group() : "(sans séparateur)";
  }
}
