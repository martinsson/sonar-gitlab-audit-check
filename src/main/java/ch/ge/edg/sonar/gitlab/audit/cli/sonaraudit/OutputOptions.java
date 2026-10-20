package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.CommaOption;
import ch.ge.edg.sonar.gitlab.audit.cli.shared.ExclusionsOption;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/** Ce qui est écrit sur disque : l'inventaire, et les captures brutes. */
final class OutputOptions {

  @Option(names = "--csv", description = "chemin du CSV d'inventaire à écrire")
  @Nullable Path csv;

  @Mixin ExclusionsOption exclusions = new ExclusionsOption();

  @Mixin CommaOption comma = new CommaOption();

  @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
  @Nullable Path dumpDir;

  @Option(
      names = "--replay-dir",
      description = "rejouer un --dump-dir au lieu d'appeler l'instance (hors ligne)")
  @Nullable Path replayDir;
}
