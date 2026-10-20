package ch.ge.edg.sonar.gitlab.audit.matching;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

/**
 * Namespace GitLab → préfixe Sonar, lu dans un YAML {@code NAMESPACE: prefixe}. Les clés du fichier
 * sont gardées telles qu'écrites, et cherchées en majuscules.
 */
public final class NamespaceMapping {

  /** Le fichier lu dans le répertoire de travail. */
  public static final String DEFAULT_FILE = "namespace-mapping.yaml";

  private final Map<String, String> glToSonar;

  private NamespaceMapping(Map<String, String> glToSonar) {
    this.glToSonar = glToSonar;
  }

  /**
   * Lit le fichier. Absent ou illisible, le mapping reste vide (ou partiel) et {@code err} le dit ;
   * un contenu qui n'est pas une table de chaînes lève l'exception du lecteur YAML ou du cast.
   */
  public static NamespaceMapping load(String filename, PrintStream err) {
    Map<String, String> glToSonar = new HashMap<>();
    try {
      Path mappingFile = Path.of(filename);
      if (!Files.exists(mappingFile)) {
        err.println(
            "Avertissement : fichier de mapping "
                + filename
                + " introuvable — le matching namespace sera désactivé");
        return new NamespaceMapping(glToSonar);
      }
      try (InputStream is = Files.newInputStream(mappingFile)) {
        Map<?, ?> data = (Map<?, ?>) new Yaml().load(is);
        if (data != null) {
          data.forEach((gl, sonar) -> glToSonar.put(((String) gl).trim(), ((String) sonar).trim()));
        }
      }
    } catch (IOException e) {
      err.println("Erreur lecture mapping : " + e.getMessage());
    }
    return new NamespaceMapping(glToSonar);
  }

  /** Le préfixe Sonar d'un namespace GitLab, ou le namespace normalisé s'il n'est pas mappé. */
  String mapNamespace(String gitlabNs) {
    String mapped = glToSonar.get(gitlabNs.toUpperCase(Locale.ROOT));
    if (mapped != null) return mapped;
    return Keys.normalise(gitlabNs);
  }

  /**
   * Variantes du mapping : pour matcher plus de clés Sonar, on produit plusieurs variantes à partir
   * du chemin GitLab.
   *
   * <p>Variante 1 : namespace mappé + tous les segments (sans ticket numbers) Variante 2 :
   * namespace mappé + nom court uniquement (sans segments intermédiaires) Variante 3 : nom court
   * seul (sans namespace)
   */
  public List<String> mappedGitlabPaths(@Nullable String gitlabPath) {
    List<String> result = new ArrayList<>();
    if (gitlabPath == null || gitlabPath.isBlank()) return result;

    String[] parts = Keys.segments(gitlabPath);
    if (parts.length < 2) {
      result.add(Keys.normalise(gitlabPath));
      return result;
    }

    String mappedPrefix = mapNamespace(parts[0]);
    List<String> cleanedSegments = new ArrayList<>();
    for (int i = 1; i < parts.length; i++) {
      String clean = Keys.stripTicketNumber(parts[i]);
      if (!clean.isEmpty()) cleanedSegments.add(Keys.normalise(clean));
    }

    // Variante 1 : namespace + tous les segments nettoyés
    StringBuilder full = new StringBuilder(mappedPrefix);
    for (String seg : cleanedSegments) {
      full.append('-').append(seg);
    }
    result.add(Keys.trimDashes(full.toString()));

    // Variante 2 : namespace + nom court uniquement (sans segments intermédiaires)
    if (cleanedSegments.size() > 1) {
      result.add(
          Keys.trimDashes(mappedPrefix + "-" + cleanedSegments.get(cleanedSegments.size() - 1)));
    }

    // Variante 3 : nom court seul (pour matcher les clés Sonar sans namespace)
    if (!cleanedSegments.isEmpty()) {
      result.add(String.join("-", cleanedSegments));
    }

    return result;
  }
}
