package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.isBlank;
import static ch.ge.edg.sonar.gitlab.audit.text.Text.orEmpty;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.AlmBinding;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectLink;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectLinks;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Ce que SonarQube sait lui-même du dépôt : la liaison DevOps, posée quand le projet a été importé
 * depuis GitLab, et les liens saisis à la main.
 *
 * <p>La liaison donne l'identifiant du projet GitLab — la seule jointure qui ne dépend d'aucun nom.
 * Elle n'existe que pour les projets créés ou liés par l'intégration : un projet né d'un
 * `-Dsonar.projectKey` en CI n'en a pas, et c'est précisément ce que ce comptage mesure.
 *
 * <p>Les liens sont saisis par quelqu'un, pas vérifiés par l'instance : ils sortent dans le CSV
 * pour que CrossAudit mesure s'ils servent, pas pour faire une jointure.
 */
final class Bindings {

  private final Session session;

  Bindings(Session session) {
    this.session = session;
  }

  Map<String, Attachments> fetch(List<Component> projects) {
    if (projects.isEmpty()) return Map.of();
    List<Attachments> read =
        session.eachProject("  Liaisons et liens", projects, p -> attachments(p.key()));
    Map<String, Attachments> byKey = new HashMap<>();
    for (int i = 0; i < projects.size(); i++) byKey.put(projects.get(i).key(), read.get(i));
    report(projects.size(), read);
    return byKey;
  }

  private void report(int projects, List<Attachments> read) {
    Map<String, Long> byAlm = new TreeMap<>();
    long refused = 0;
    long withLinks = 0;
    for (Attachments a : read) {
      if (a.refused()) refused++;
      else if (!a.alm().isEmpty()) byAlm.merge(a.alm(), 1L, Long::sum);
      if (!a.links().isEmpty()) withLinks++;
    }
    Console out = session.out();
    out.println();
    long bound = byAlm.values().stream().mapToLong(Long::longValue).sum();
    out.out()
        .printf(
            "  Liés à une plateforme DevOps   : %s / %d%s%n",
            out.color(String.valueOf(bound), BOLD), projects, byAlm.isEmpty() ? "" : "  " + byAlm);
    if (refused > 0) {
      out.println(
          out.color(
              "    dont %d liaison(s) illisible(s) : lecture refusée, pas absente"
                  .formatted(refused),
              YELLOW));
    }
    out.out().printf("  Avec au moins un lien saisi    : %d / %d%n", withLinks, projects);
  }

  private Attachments attachments(String key) {
    Sonar.Response r = session.sq().get("api/alm_settings/get_binding", params("project", key));
    String alm;
    String repository = "";
    if (r.ok()) {
      AlmBinding b = r.as(AlmBinding.class);
      // En rejeu, un 404 capturé revient en 200 avec son corps d'erreur :
      // une liaison sans plateforme est une absence, pas une lecture ratée.
      alm = b == null ? "" : orEmpty(b.alm()).toLowerCase(Locale.ROOT);
      repository = b == null ? "" : orEmpty(b.repository());
    } else if (r.status() == 404) {
      alm = "";
    } else {
      alm = Attachments.REFUSED + " (HTTP " + r.status() + ")";
    }
    ProjectLinks pl =
        session.read("api/project_links/search", params("projectKey", key), ProjectLinks.class);
    String links =
        pl == null
            ? ""
            : orEmptyList(pl.links()).stream()
                .filter(l -> !isBlank(l.url()))
                .map(Bindings::describe)
                .collect(Collectors.joining(" | "));
    return new Attachments(alm, repository, links);
  }

  private static String describe(ProjectLink l) {
    return (isBlank(l.type()) ? "autre" : l.type()) + " " + orEmpty(l.url()).trim();
  }
}
