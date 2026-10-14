package ch.ge.edg.sonar.gitlab.audit.sonar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;

/**
 * Capture et rejeu : {@code --dump-dir} enregistre chaque réponse brute, {@code --replay-dir} les
 * relit, et l'outil tourne alors sans instance.
 */
public final class Captures {

  private final @Nullable Path dumpDir;
  private final @Nullable Path replayDir;
  private final PrintStream warnings;
  private final AtomicInteger missingCount = new AtomicInteger();

  /**
   * @param warnings où signaler une capture qui n'a pas pu être écrite
   */
  public Captures(@Nullable Path dumpDir, @Nullable Path replayDir, PrintStream warnings) {
    this.dumpDir = dumpDir;
    this.replayDir = replayDir;
    this.warnings = warnings;
  }

  /** Ni capture ni rejeu : l'instance est appelée, rien n'est écrit. */
  public static Captures none() {
    return new Captures(null, null, new PrintStream(OutputStream.nullOutputStream()));
  }

  /** Le répertoire de capture existe avant le premier appel. */
  void prepare() throws IOException {
    if (dumpDir != null) Files.createDirectories(dumpDir);
  }

  boolean replaying() {
    return replayDir != null;
  }

  /** Captures réclamées et absentes : en rejeu, c'est la seule panne possible. */
  int missing() {
    return missingCount.get();
  }

  /**
   * Le nom d'une capture se déduit de la requête, pas de son rang d'appel.
   *
   * <p>Numérotées dans l'ordre, les captures ne se relisent que par un outil qui refait exactement
   * les mêmes appels dans exactement le même ordre — ce que la concurrence a rendu faux. Nommées
   * par (chemin, paramètres), elles se relisent, se complètent à la main, et se versionnent comme
   * des fixtures.
   *
   * <p>Le condensé porte les paramètres pour que deux requêtes du même endpoint ne s'écrasent pas ;
   * le chemin reste en clair pour qu'un humain retrouve le fichier qu'il cherche. L'ordre des
   * appels n'est pas perdu : il est consigné dans index.txt.
   */
  static String name(String path, Map<String, String> params) {
    String slug = path.replaceAll("^api/", "").replaceAll("[^A-Za-z0-9]+", "_");
    return Sonar.truncate(slug, 60)
        + "-"
        + shortHash(path + Sonar.queryString(new TreeMap<>(params)))
        + ".json";
  }

  /** La réponse capturée, ou rien hors rejeu : l'instance doit alors être appelée. */
  Optional<Sonar.Response> replay(String capture, String path, Map<String, String> params) {
    Path dir = replayDir;
    if (dir == null) return Optional.empty();
    Path f = dir.resolve(capture);
    if (!Files.isRegularFile(f)) {
      missingCount.incrementAndGet();
      String request = path + Sonar.queryString(params);
      return Optional.of(
          new Sonar.Response(0, "aucune capture pour " + request + " (attendu : " + capture + ")"));
    }
    try {
      return Optional.of(new Sonar.Response(200, Files.readString(f, StandardCharsets.UTF_8)));
    } catch (IOException e) {
      missingCount.incrementAndGet();
      return Optional.of(new Sonar.Response(0, "capture illisible : " + e.getMessage()));
    }
  }

  /**
   * Les captures brutes ne sont pas de l'échafaudage : elles servent à générer les records, à
   * diffuser les dérives de version d'une instance à l'autre, et — depuis --replay-dir — à rejouer
   * un parc entier sans instance.
   */
  void dump(String capture, String request, @Nullable String body) {
    Path dir = dumpDir;
    if (dir == null) return;
    try {
      Files.writeString(dir.resolve(capture), body == null ? "" : body);
      synchronized (this) {
        Files.writeString(
            dir.resolve("index.txt"),
            capture + "  " + request + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      warnings.println("  (capture non écrite : " + e.getMessage() + ")");
    }
  }

  private static String shortHash(String s) {
    try {
      byte[] d = MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < 4; i++) sb.append("%02x".formatted(d[i]));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return "%08x".formatted(s.hashCode());
    }
  }
}
