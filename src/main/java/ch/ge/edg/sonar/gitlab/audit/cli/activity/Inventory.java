package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.enc;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.pct;

import ch.ge.edg.sonar.gitlab.audit.exclusions.Exclusions;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Étape 0 : inventaire et exclusions dures. */
final class Inventory {

  private final AuditContext ctx;
  private final ScopeOptions scope;

  Inventory(AuditContext ctx, ScopeOptions scope) {
    this.ctx = ctx;
    this.scope = scope;
  }

  /** Tout le périmètre, exclus compris : ils portent leur motif jusque dans le CSV. */
  List<Proj> run() throws IOException {
    ctx.con().title("1. Inventaire (étape 0)");
    PrintStream out = ctx.out();

    List<Proj> all = fetch().stream().map(Proj::of).collect(Collectors.toList());
    out.printf("  Projets retournés               : %d%n", all.size());

    // Les exclusions sont comptées, jamais escamotées. Un parc où 30 % des
    // dépôts sont des miroirs n'est pas le même parc que 30 % d'archivés,
    // et un total sans dénominateur se lit comme complet alors qu'il ne l'est pas.
    Exclusions exclusions = Exclusions.load(scope.exclusionsFile);
    if (!exclusions.isEmpty()) {
      out.printf(
          "  Liste d'exclusion               : %s (%d entrées)%n",
          exclusions.file(), exclusions.entries().size());
    }
    for (Proj p : all) p.excluded = exclusionReason(p, exclusions);
    Map<String, Long> byReason = new TreeMap<>();
    for (Proj p : all) {
      if (p.excluded != null) byReason.merge(p.excluded, 1L, Long::sum);
    }
    byReason.forEach((r, n) -> out.printf("    exclu — %-22s : %d%n", r, n));

    long kept = all.stream().filter(p -> p.excluded == null).count();
    ctx.counters().inScope = all.size();
    ctx.counters().kept = (int) kept;
    out.printf(
        "  Retenus pour analyse            : %d (%s du parc)%n", kept, pct(kept, all.size()));
    int forbidden = ctx.gl().forbidden();
    if (forbidden > 0) {
      ctx.con()
          .say(
              "    %d réponses 403 : le jeton ne voit pas tout le parc.".formatted(forbidden),
              YELLOW);
      ctx.con().say("    Un trou de permission raccourcit la liste sans lever d'erreur.", DIM);
    }
    return all;
  }

  private List<JsonNode> fetch() throws IOException {
    Gitlab gl = ctx.gl();
    String archived = scope.includeArchived ? null : "false";
    Path file = scope.projectsFile;
    if (file != null) return fromFile(file);
    String group = scope.group;
    if (group != null && !group.isBlank()) {
      return gl.paged(
          "groups/" + enc(group) + "/projects",
          params(
              "include_subgroups",
              String.valueOf(scope.includeSubgroups),
              "with_shared",
              "false",
              "statistics",
              "true",
              "archived",
              archived));
    }
    return gl.paged(
        "projects",
        params("statistics", "true", "archived", archived, "order_by", "id", "sort", "asc"));
  }

  private List<JsonNode> fromFile(Path file) throws IOException {
    List<String> paths =
        Files.readAllLines(file, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
            .toList();
    Gitlab gl = ctx.gl();
    List<Gitlab.Response> rs =
        gl.map(
            paths,
            ctx.concurrency(),
            path -> gl.get("projects/" + enc(path), Map.of("statistics", "true")));
    List<JsonNode> found = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      Gitlab.Response r = rs.get(i);
      if (r.status() == 200) found.add(r.json());
      else ctx.out().printf("    %-40s HTTP %d — ignoré%n", paths.get(i), r.status());
    }
    return found;
  }

  private @Nullable String exclusionReason(Proj p, Exclusions exclusions) {
    if (exclusions.match(p.path) != null) return "liste d'exclusion";
    if (p.archived && !scope.includeArchived) return "archivé";
    if (p.emptyRepo) return "dépôt vide";
    if (p.mirror) return "miroir";
    if (isUndivergedFork(p)) return "fork non divergé";
    if (isBlank(p.defaultBranch)) return "sans branche par défaut";
    return null;
  }

  /** Un fork sans activité depuis sa création n'est qu'une copie. */
  private static boolean isUndivergedFork(Proj p) {
    OffsetDateTime last = p.lastActivity;
    OffsetDateTime created = p.created;
    return p.forkedFrom != null
        && last != null
        && created != null
        && last.isBefore(created.plusDays(1));
  }
}
