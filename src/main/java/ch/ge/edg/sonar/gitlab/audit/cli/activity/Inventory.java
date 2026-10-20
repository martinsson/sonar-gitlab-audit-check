package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.enc;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.pct;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.exclusions.Exclusions;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
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

  private final Gitlab gl;
  private final Screen con;
  private final Counters counters;
  private final Funnel funnel;
  private final ScopeOptions scope;

  Inventory(Gitlab gl, Screen con, Counters counters, Funnel funnel, ScopeOptions scope) {
    this.gl = gl;
    this.con = con;
    this.counters = counters;
    this.funnel = funnel;
    this.scope = scope;
  }

  /**
   * Tout le périmètre, exclus compris : ils portent leur motif jusque dans le CSV.
   *
   * @param exclusionsFile la liste d'exclusion, ou null sans liste
   */
  List<Proj> run(@Nullable Path exclusionsFile) throws IOException {
    con.title("1. Inventaire (étape 0)");
    Console out = con.out();

    List<Proj> all = fetch().stream().map(Proj::of).collect(Collectors.toList());
    out.out().printf("  Projets retournés               : %d%n", all.size());

    // Les exclusions sont comptées, jamais escamotées. Un parc où 30 % des
    // dépôts sont des miroirs n'est pas le même parc que 30 % d'archivés,
    // et un total sans dénominateur se lit comme complet alors qu'il ne l'est pas.
    Exclusions exclusions = Exclusions.load(exclusionsFile);
    if (!exclusions.isEmpty()) {
      out.out()
          .printf(
              "  Liste d'exclusion               : %s (%d entrées)%n",
              exclusions.file(), exclusions.entries().size());
    }
    for (Proj p : all) {
      String reason = exclusionReason(p, exclusions);
      if (reason != null) p.fate = new Fate.Excluded(Fate.Stage.INVENTORY, reason);
    }
    Map<String, Long> byReason = new TreeMap<>();
    for (Proj p : all) {
      if (p.fate instanceof Fate.Excluded e) byReason.merge(e.reason(), 1L, Long::sum);
    }
    byReason.forEach((r, n) -> out.out().printf("    exclu — %-22s : %d%n", r, n));

    long kept = all.stream().filter(p -> p.fate.inPlay()).count();
    counters.inScope = all.size();
    counters.kept = (int) kept;
    out.out()
        .printf(
            "  Retenus pour analyse            : %d (%s du parc)%n", kept, pct(kept, all.size()));
    int forbidden = gl.forbidden();
    if (forbidden > 0) {
      con.say(
          "    %d réponses 403 : le jeton ne voit pas tout le parc.".formatted(forbidden), YELLOW);
      con.say("    Un trou de permission raccourcit la liste sans lever d'erreur.", DIM);
    }
    return all;
  }

  private List<JsonNode> fetch() throws IOException {
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
    List<Gitlab.Response> rs =
        gl.map(
            paths,
            funnel.concurrency(),
            path -> gl.get("projects/" + enc(path), Map.of("statistics", "true")));
    List<JsonNode> found = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      Gitlab.Response r = rs.get(i);
      if (r.status() == 200) found.add(r.json());
      else con.out().out().printf("    %-40s HTTP %d — ignoré%n", paths.get(i), r.status());
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
