package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** --exclusions : le fichier de ce qu'un audit laisse de côté. */
public final class ExclusionsOption {

  @Option(
      names = "--exclusions",
      order = 6,
      description =
          "fichier de clés ou chemins à exclure, un par ligne, "
              + "* et ** permis (sinon : exclusions.txt s'il existe)")
  @Nullable Path file;

  /** Le fichier demandé, ou null : alors {@code exclusions.txt} s'il existe. */
  public @Nullable Path file() {
    return file;
  }
}
