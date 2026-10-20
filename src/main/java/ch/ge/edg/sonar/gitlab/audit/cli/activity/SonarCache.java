package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * Cache disque des faits de CI, indexé par la tête de la branche par défaut.
 *
 * <p>La clé Sonar d'un projet est la seule chose que cet audit mesure qui ne soit pas fenêtrée :
 * commits, MR, pipelines valent pour 90 jours et sont périmés le lendemain, tandis que la clé vaut
 * tant que le .gitlab-ci.yml n'a pas changé. La redemander à chaque passage, c'est repayer la
 * partie la plus chère de l'audit pour la seule réponse dont on sait d'avance qu'elle est
 * identique.
 *
 * <p>L'index est le SHA de la tête, pas la date : une date qui recule — import, réécriture
 * d'historique — laisserait servir une réponse périmée, alors qu'un SHA différent invalide toujours
 * dans le bon sens. À défaut de SHA on indexe sur last_activity_at, qui bouge pour des raisons qui
 * ne touchent pas au dépôt : le cache rate plus souvent, il ne ment pas.
 *
 * <p>Un cache illisible ou d'une version antérieure n'est pas une erreur : on repart de zéro. Le
 * pire qu'il puisse faire est de coûter le prix d'un premier passage.
 */
final class SonarCache {
  // 2 : le pom est lu. Un cache de version 1 dirait « pas de clé Maven »
  // pour tout le parc sans l'avoir cherchée.
  private static final int VERSION = 2;
  private final Map<String, CiFacts> entries = new ConcurrentHashMap<>();
  private final @Nullable Path file;
  private final PrintStream err;
  private int loaded;

  /** {@code err} reçoit l'avertissement d'un cache qui n'a pas pu s'écrire. */
  SonarCache(@Nullable Path file, boolean enabled, PrintStream err) {
    this.file = enabled ? file : null;
    this.err = err;
  }

  String keyFor(AuditedProject p) {
    String sha = p.headSha();
    String stamp =
        sha != null && !sha.isBlank() ? sha : "act:" + Fmt.iso(p.project().lastActivity());
    return p.project().id() + "@" + stamp;
  }

  @Nullable CiFacts get(String key) {
    return file != null ? entries.get(key) : null;
  }

  void put(String key, CiFacts f) {
    if (file != null) entries.put(key, f);
  }

  int size() {
    return entries.size();
  }

  int loadedCount() {
    return loaded;
  }

  void load() {
    Path f = file;
    if (f == null || !Files.exists(f)) return;
    try {
      JsonNode root = Gitlab.MAPPER.readTree(Files.readString(f, StandardCharsets.UTF_8));
      if (root.path("version").asInt(0) != VERSION) return;
      root.path("entries")
          .fields()
          .forEachRemaining(e -> entries.put(e.getKey(), read(e.getValue())));
      loaded = entries.size();
    } catch (IOException e) {
      entries.clear();
      loaded = 0;
    }
  }

  private static CiFacts read(JsonNode v) {
    CiFacts f = new CiFacts();
    f.ciSonar = v.path("ciSonar").asBoolean(false);
    f.ciSecurity = v.path("ciSecurity").asBoolean(false);
    f.sonarKey = v.path("sonarKey").asText(null);
    f.keySource = v.path("keySource").asText(null);
    f.ciRoute = v.path("ciRoute").asText(null);
    f.unresolvedKey = v.path("unresolvedKey").asText(null);
    f.pomKey = v.path("pomKey").asText(null);
    f.pomName = v.path("pomName").asText(null);
    f.pomUnresolved = v.path("pomUnresolved").asText(null);
    return f;
  }

  private static void write(ObjectNode o, CiFacts f) {
    o.put("ciSonar", f.ciSonar);
    o.put("ciSecurity", f.ciSecurity);
    o.put("sonarKey", f.sonarKey);
    o.put("keySource", f.keySource);
    o.put("ciRoute", f.ciRoute);
    o.put("unresolvedKey", f.unresolvedKey);
    o.put("pomKey", f.pomKey);
    o.put("pomName", f.pomName);
    o.put("pomUnresolved", f.pomUnresolved);
  }

  void save() {
    Path f = file;
    if (f == null || entries.isEmpty()) return;
    var root = Gitlab.MAPPER.createObjectNode();
    root.put("version", VERSION);
    var es = root.putObject("entries");
    entries.forEach((k, facts) -> write(es.putObject(k), facts));
    try {
      Path dir = f.toAbsolutePath().getParent();
      if (dir != null) Files.createDirectories(dir);
      Files.writeString(
          f,
          Gitlab.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
          StandardCharsets.UTF_8);
    } catch (IOException e) {
      err.println("  (cache non écrit : " + e.getMessage() + ")");
    }
  }
}
