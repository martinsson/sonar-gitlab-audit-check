package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** Ce que l'audit écrit et où, la passe profonde qui produit pratiques.csv, et l'affichage. */
public final class OutputOptions {

  @Option(
      names = "--deep",
      order = 11,
      description =
          "mesurer aussi les pratiques des projets " + "sélectionnés (plus long, plus d'appels)")
  boolean deep;

  @Option(
      names = "--out-dir",
      order = 12,
      description = "répertoire de sortie : inventaire.csv, plus pratiques.csv " + "avec --deep")
  @Nullable Path outDir;

  @Option(
      names = "--csv",
      order = 17,
      description = "chemin explicite du CSV d'inventaire " + "(sinon : --out-dir)")
  @Nullable Path csv;

  @Option(
      names = "--pratiques",
      order = 18,
      description =
          "chemin explicite du CSV de pratiques "
              + "(implique --deep ; l'inventaire est écrit à côté)")
  @Nullable Path pratiquesCsv;

  @Option(
      names = "--comma",
      order = 20,
      description = "CSV séparés par des virgules, sans BOM (pour un outil, pas Excel)")
  boolean comma;

  @Option(
      names = "--cache",
      order = 23,
      description =
          "fichier de cache des clés Sonar " + "(sinon : .sonar-keys.json dans --out-dir)")
  @Nullable Path cacheFile;

  @Option(
      names = "--no-cache",
      order = 24,
      description = "ignorer et ne pas écrire le cache des clés Sonar")
  boolean noCache;

  @Option(
      names = "--color",
      order = 28,
      defaultValue = "auto",
      description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
  String colorMode = "auto";

  /**
   * L'inventaire n'est pas une sortie facultative du mode --deep : c'est lui qui porte les
   * dénominateurs — projets exclus et pour quel motif, cohorte sous le plancher, témoin aléatoire.
   * Sans lui, pratiques.csv est une liste de projets sans le parc dont ils sont tirés, et les
   * pourcentages qu'on en tire n'ont pas de population.
   *
   * <p>Demander --pratiques sans --csv écrivait donc les pratiques seules. On écrit désormais
   * l'inventaire à côté, dans le même répertoire.
   */
  Outputs resolve() throws IOException {
    boolean deepPass = deep || pratiquesCsv != null;
    Path inventory = csv;
    Path pratiques = pratiquesCsv;
    Path cache = cacheFile;
    Path dir = outDir;
    if (dir != null) {
      Files.createDirectories(dir);
      if (inventory == null) inventory = dir.resolve("inventaire.csv");
      if (deepPass && pratiques == null) pratiques = dir.resolve("pratiques.csv");
      if (cache == null) cache = dir.resolve(".sonar-keys.json");
    }
    if (inventory == null && pratiques != null) {
      inventory = parentOf(pratiques).resolve("inventaire.csv");
    }
    // Un chemin explicite vers un répertoire inexistant échouait à l'écriture,
    // après la passe complète : tout le travail d'API perdu sur un mkdir.
    createParent(inventory);
    createParent(pratiques);
    return new Outputs(inventory, pratiques, cache, deepPass, comma, !noCache);
  }

  private static Path parentOf(Path file) {
    Path dir = file.toAbsolutePath().getParent();
    return dir == null ? Path.of("") : dir;
  }

  private static void createParent(@Nullable Path file) throws IOException {
    if (file == null) return;
    Path dir = file.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
  }

  /** Les sorties une fois résolues : chemins effectifs, et ce qu'on en fait. */
  record Outputs(
      @Nullable Path inventory,
      @Nullable Path pratiques,
      @Nullable Path cache,
      boolean deep,
      boolean comma,
      boolean useCache) {}
}
