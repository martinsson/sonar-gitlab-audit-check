package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.enc;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.CellFormat.isBlank;

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
import org.jspecify.annotations.Nullable;

/** Étape 0 : inventaire et exclusions dures. */
final class Inventory {

  private final Gitlab gitlab;
  private final Funnel funnel;
  private final ScopeOptions scope;

  Inventory(Gitlab gitlab, Funnel funnel, ScopeOptions scope) {
    this.gitlab = gitlab;
    this.funnel = funnel;
    this.scope = scope;
  }

  /**
   * Ce que l'inventaire a trouvé : tout le périmètre, exclus compris — ils portent leur motif
   * jusque dans le CSV.
   *
   * @param ignored les chemins de --projects-file que GitLab n'a pas rendus
   * @param exclusions la liste d'exclusion appliquée, vide sans liste
   * @param forbidden les réponses 403 reçues : le jeton ne voit pas tout le parc
   */
  record Census(
      List<AuditedProject> all, List<Ignored> ignored, Exclusions exclusions, int forbidden) {}

  /** Un chemin de --projects-file auquel GitLab a répondu autre chose que 200. */
  record Ignored(String path, int status) {}

  /**
   * Le périmètre, chaque projet exclu d'office portant son motif.
   *
   * @param exclusionsFile la liste d'exclusion, ou null sans liste
   */
  Census run(@Nullable Path exclusionsFile) throws IOException {
    List<Ignored> ignored = new ArrayList<>();
    List<JsonNode> found = fetch(ignored);
    // Les exclusions sont comptées, jamais escamotées. Un parc où 30 % des
    // dépôts sont des miroirs n'est pas le même parc que 30 % d'archivés,
    // et un total sans dénominateur se lit comme complet alors qu'il ne l'est pas.
    Exclusions exclusions = Exclusions.load(exclusionsFile);
    List<AuditedProject> all =
        found.stream()
            .map(GitlabProject::of)
            .map(p -> AuditedProject.entering(p).with(fate(p, exclusions)))
            .toList();
    return new Census(all, ignored, exclusions, gitlab.forbidden());
  }

  private Fate fate(GitlabProject p, Exclusions exclusions) {
    String reason = exclusionReason(p, exclusions);
    return reason == null ? new Fate.InPlay() : new Fate.Excluded(Fate.Stage.INVENTORY, reason);
  }

  private List<JsonNode> fetch(List<Ignored> ignored) throws IOException {
    String archived = scope.includeArchived ? null : "false";
    Path file = scope.projectsFile;
    if (file != null) return fromFile(file, ignored);
    String group = scope.group;
    if (group != null && !group.isBlank()) {
      return gitlab.paged(
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
    return gitlab.paged(
        "projects",
        params("statistics", "true", "archived", archived, "order_by", "id", "sort", "asc"));
  }

  private List<JsonNode> fromFile(Path file, List<Ignored> ignored) throws IOException {
    List<String> paths =
        Files.readAllLines(file, StandardCharsets.UTF_8).stream()
            .map(String::trim)
            .filter(l -> !l.isEmpty() && !l.startsWith("#"))
            .toList();
    List<Gitlab.Response> rs =
        gitlab.map(
            paths,
            funnel.concurrency(),
            path -> gitlab.get("projects/" + enc(path), Map.of("statistics", "true")));
    List<JsonNode> found = new ArrayList<>();
    for (int i = 0; i < paths.size(); i++) {
      Gitlab.Response r = rs.get(i);
      if (r.status() == 200) found.add(r.json());
      else ignored.add(new Ignored(paths.get(i), r.status()));
    }
    return found;
  }

  private @Nullable String exclusionReason(GitlabProject p, Exclusions exclusions) {
    GitlabProject.Repository repo = p.repository();
    if (exclusions.match(p.path()) != null) return "liste d'exclusion";
    if (repo.archived() && !scope.includeArchived) return "archivé";
    if (repo.empty()) return "dépôt vide";
    if (repo.mirror()) return "miroir";
    if (isUndivergedFork(p)) return "fork non divergé";
    if (isBlank(repo.defaultBranch())) return "sans branche par défaut";
    return null;
  }

  /** Un fork sans activité depuis sa création n'est qu'une copie. */
  private static boolean isUndivergedFork(GitlabProject p) {
    OffsetDateTime last = p.dates().lastActivity();
    OffsetDateTime created = p.dates().created();
    return p.repository().forkedFrom() != null
        && last != null
        && created != null
        && last.isBefore(created.plusDays(1));
  }
}
