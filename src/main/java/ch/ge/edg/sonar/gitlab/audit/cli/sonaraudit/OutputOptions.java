package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** Ce qui est écrit sur disque : l'inventaire, et les captures brutes. */
final class OutputOptions {

  @Option(names = "--csv", description = "chemin du CSV d'inventaire à écrire")
  @Nullable Path csv;

  @Option(
      names = "--exclusions",
      description =
          "fichier de clés à exclure, une par ligne, "
              + "* et ** permis (sinon : exclusions.txt s'il existe)")
  @Nullable Path exclusionsFile;

  @Option(
      names = "--comma",
      description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
  boolean comma;

  @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
  @Nullable Path dumpDir;

  @Option(
      names = "--replay-dir",
      description = "rejouer un --dump-dir au lieu d'appeler l'instance (hors ligne)")
  @Nullable Path replayDir;
}
