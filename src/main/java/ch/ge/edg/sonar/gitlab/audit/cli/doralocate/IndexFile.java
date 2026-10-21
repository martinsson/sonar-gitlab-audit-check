package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.Times;
import ch.ge.edg.sonar.gitlab.audit.gocd.GocdApi;
import ch.ge.edg.sonar.gitlab.audit.rest.Stop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

/**
 * L'index GoCD sur disque. Construire l'index coûte un appel par pipeline visible : on garde le
 * résultat un jour, et {@code --refresh-index} force sa reconstruction. Un fichier illisible se
 * reconstruit ; un fichier qu'on ne peut pas écrire n'empêche pas la recherche en cours.
 */
final class IndexFile {

  static final Duration FRESH = Duration.ofDays(1);

  private final Path file;
  private final Context ctx;

  IndexFile(Path file, Context ctx) {
    this.file = file;
    this.ctx = ctx;
  }

  GocdIndex loadOrBuild(GocdApi gocd, boolean refresh) throws Stop {
    Instant now = ctx.clock().instant();
    try {
      if (!refresh && fresh(now)) return GocdIndex.fromJson(GocdIndex.JSON.readTree(file.toFile()));
    } catch (IOException e) {
      ctx.err().println("Index illisible, reconstruit : " + e.getMessage());
    }
    GocdIndex index = GocdIndex.build(gocd, Times.format(now, ctx.zone()), ctx.zone(), ctx.err());
    try {
      Path parent = file.toAbsolutePath().getParent();
      if (parent != null) Files.createDirectories(parent);
      GocdIndex.JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), index.toJson());
    } catch (IOException e) {
      ctx.err().println("Index non écrit : " + e.getMessage());
    }
    return index;
  }

  private boolean fresh(Instant now) throws IOException {
    return Files.exists(file)
        && Files.getLastModifiedTime(file).toInstant().isAfter(now.minus(FRESH));
  }
}
