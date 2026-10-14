package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** Le périmètre : quels projets l'inventaire lit, et lesquels il écarte d'office. */
public final class ScopeOptions {

  @Option(
      names = "--group",
      order = 3,
      description = "chemin complet d'un groupe (sinon : toute l'instance)")
  @Nullable String group;

  @Option(
      names = "--include-subgroups",
      order = 4,
      defaultValue = "true",
      description = "descendre dans les sous-groupes (défaut : ${DEFAULT-VALUE})")
  boolean includeSubgroups = true;

  @Option(
      names = "--projects",
      order = 5,
      description = "fichier de chemins de projets, un par ligne")
  @Nullable Path projectsFile;

  @Option(
      names = "--exclusions",
      order = 6,
      description =
          "fichier de chemins à exclure, un par ligne, "
              + "* et ** permis (sinon : exclusions.txt s'il existe)")
  @Nullable Path exclusionsFile;

  @Option(names = "--include-archived", order = 13, description = "inclure les projets archivés")
  boolean includeArchived;
}
