package ch.ge.edg.sonar.gitlab.audit.rest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Capture et rejeu, pour tous les clients : {@code --dump-dir} enregistre chaque réponse brute,
 * {@code --replay-dir} les relit, et l'outil tourne alors sans instance.
 *
 * <p>Le corps va dans {@code <nom>.json}, tel quel : les captures servent aussi à générer des
 * records. Un statut autre que 200 va à côté, dans {@code <nom>.json.status} ; une capture sans ce
 * fichier — toutes celles d'avant lui — se relit comme un 200.
 */
public final class Capture {

  private final @Nullable Path dumpDir;
  private final @Nullable Path replayDir;
  private final Consumer<String> notWritten;
  private final AtomicInteger missingCount = new AtomicInteger();

  /**
   * @param notWritten reçoit la raison quand une capture n'a pas pu s'écrire
   */
  public Capture(@Nullable Path dumpDir, @Nullable Path replayDir, Consumer<String> notWritten) {
    this.dumpDir = dumpDir;
    this.replayDir = replayDir;
    this.notWritten = notWritten;
  }

  /** Ni capture ni rejeu : l'instance est appelée, rien n'est écrit. */
  public static Capture none() {
    return new Capture(null, null, reason -> {});
  }

  /** Le répertoire de capture existe avant le premier appel. */
  void prepare() throws IOException {
    if (dumpDir != null) Files.createDirectories(dumpDir);
  }

  public boolean replaying() {
    return replayDir != null;
  }

  /** Captures réclamées et absentes : en rejeu, c'est la seule panne possible. */
  public int missing() {
    return missingCount.get();
  }

  /** {@link #name(String, String, String)} d'un GET. */
  public static String name(String request) {
    return name("GET", request, null);
  }

  /**
   * Le nom d'une capture se déduit de la requête, pas de son rang d'appel.
   *
   * <p>Numérotées dans l'ordre, les captures ne se relisent que par un outil qui refait exactement
   * les mêmes appels dans exactement le même ordre — ce que la concurrence a rendu faux. Nommées
   * par (chemin, paramètres), elles se relisent, se complètent à la main, et se versionnent comme
   * des fixtures.
   *
   * <p>Le condensé porte les paramètres, triés, pour que deux requêtes du même endpoint ne
   * s'écrasent pas ; un appel autre que GET y ajoute sa méthode et son corps. Le chemin reste en
   * clair pour qu'un humain retrouve le fichier qu'il cherche. L'ordre des appels n'est pas perdu :
   * il est consigné dans index.txt.
   *
   * @param request chemin relatif à la racine de l'instance, requête comprise
   */
  public static String name(String method, String request, @Nullable String body) {
    int q = request.indexOf('?');
    String path = q < 0 ? request : request.substring(0, q);
    String query = q < 0 ? "" : sortedQuery(request.substring(q + 1));
    String slug = path.replaceAll("^api/", "").replaceAll("[^A-Za-z0-9]+", "_");
    String key = path + query;
    if (!"GET".equals(method)) key += " " + method + " " + (body == null ? "" : body);
    return (slug.length() <= 60 ? slug : slug.substring(0, 60)) + "-" + shortHash(key) + ".json";
  }

  private static String sortedQuery(String query) {
    if (query.isEmpty()) return "";
    return Arrays.stream(query.split("&"))
        .sorted(Comparator.comparing((String pair) -> pair.split("=", 2)[0]))
        .collect(Collectors.joining("&", "?", ""));
  }

  /** La réponse capturée, ou rien hors rejeu : l'instance doit alors être appelée. */
  Optional<RestResponse> replay(String capture, String request) {
    Path dir = replayDir;
    if (dir == null) return Optional.empty();
    Path f = dir.resolve(capture);
    if (!Files.isRegularFile(f)) {
      missingCount.incrementAndGet();
      return Optional.of(
          RestResponse.unreachable(
              "aucune capture pour " + request + " (attendu : " + capture + ")"));
    }
    try {
      String body = Files.readString(f, StandardCharsets.UTF_8);
      Path statusFile = dir.resolve(capture + ".status");
      int status =
          Files.isRegularFile(statusFile)
              ? Integer.parseInt(Files.readString(statusFile).trim())
              : 200;
      return Optional.of(
          status == 0
              ? RestResponse.unreachable(body)
              : new RestResponse(status, body, Map.of(), ""));
    } catch (IOException | NumberFormatException e) {
      missingCount.incrementAndGet();
      return Optional.of(RestResponse.unreachable("capture illisible : " + e.getMessage()));
    }
  }

  /**
   * Les captures brutes ne sont pas de l'échafaudage : elles servent à générer les records, à
   * diffuser les dérives de version d'une instance à l'autre, et — depuis --replay-dir — à rejouer
   * un parc entier sans instance. Une réponse absente garde sa raison à la place du corps.
   */
  void dump(String capture, String request, RestResponse r) {
    Path dir = dumpDir;
    if (dir == null) return;
    try {
      Files.writeString(dir.resolve(capture), r.status() == 0 ? r.error() : r.body());
      Path statusFile = dir.resolve(capture + ".status");
      if (r.status() == 200) Files.deleteIfExists(statusFile);
      else Files.writeString(statusFile, String.valueOf(r.status()));
      synchronized (this) {
        Files.writeString(
            dir.resolve("index.txt"),
            capture + "  " + request + System.lineSeparator(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      notWritten.accept(String.valueOf(e.getMessage()));
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
