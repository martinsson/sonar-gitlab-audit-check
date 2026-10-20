package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.RED;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.totalOf;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ProjectSearch;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.exclusions.Exclusions;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Les projets visibles, ce qui échappe au jeton, et la fraîcheur de leurs analyses. */
final class Projects {

  private static final int PAGE_SIZE = 500;
  private static final int MAX_PAGES = 40;

  private final Session session;
  private final Console out;

  Projects(Session session) {
    this.session = session;
    this.out = session.out();
  }

  List<Component> fetchAll() {
    List<Component> all = new ArrayList<>();
    int page = 1;
    while (page <= MAX_PAGES) {
      Sonar.Response r =
          session
              .sq()
              .get(
                  "api/components/search_projects",
                  params(
                      "ps",
                      String.valueOf(PAGE_SIZE),
                      "p",
                      String.valueOf(page),
                      "f",
                      "analysisDate,leakPeriodDate"));
      ProjectSearch p = r.as(ProjectSearch.class);
      if (p == null) {
        out.println(
            out.color(
                Text.format("  Échec de la pagination page %d : HTTP %d", page, r.status()), RED));
        break;
      }
      List<Component> batch = orEmptyList(p.components());
      all.addAll(batch);
      if (batch.isEmpty() || all.size() >= totalOf(p.paging())) break;
      page++;
    }
    if (page > MAX_PAGES)
      out.println(out.color("  Pagination interrompue après " + MAX_PAGES + " pages.", YELLOW));
    return all;
  }

  /**
   * La liste d'exclusion retire les projets de l'inventaire, mais le dit : un total amputé sans le
   * dire se lit comme complet.
   */
  List<Component> exclude(List<Component> projects, @Nullable Path exclusionsFile)
      throws IOException {
    Exclusions ex = Exclusions.load(exclusionsFile);
    if (ex.isEmpty()) return projects;
    List<Component> kept = projects.stream().filter(p -> ex.match(p.key()) == null).toList();
    out.print(
        Text.format(
            "  Exclus par %s : %s (%d entrées)%n",
            ex.file(),
            out.color(String.valueOf(projects.size() - kept.size()), BOLD),
            ex.entries().size()));
    return kept;
  }

  /** L'écart entre ce que je vois et ce qui existe : le point aveugle de l'audit. */
  void reportScopeGap(int visible) {
    ProjectSearch p = session.read("api/projects/search", params("ps", "1"), ProjectSearch.class);
    // paging absent = réponse illisible, pas « zéro projet » : ne rien affirmer.
    Integer real = (p == null || p.paging() == null) ? null : p.paging().total();
    if (real == null) {
      out.println(
          out.color(
              // Le délimiteur fermant, deux colonnes à gauche, garde le retrait du texte.
              """
                  Total réel indisponible (nécessite 'Administer System').
                    Fais comparer ce chiffre à un admin : l'écart est le point aveugle
                    de ton audit, et il n'apparaît dans aucune réponse d'erreur.
                """
                  .stripTrailing(),
              YELLOW));
      return;
    }
    int gap = real - visible;
    out.println("  Projets réellement présents    : " + out.color(String.valueOf(real), BOLD));
    if (gap > 0 && real > 0) {
      out.println(
          out.color(
              Text.format(
                  "  → %d projet(s) hors de ton périmètre. Ton classement sera "
                      + "tronqué%n    de %.0f%% sans aucun message d'erreur.",
                  gap, 100.0 * gap / real),
              RED));
    } else {
      out.println(out.color("  → Périmètre complet.", GREEN));
    }
  }

  void reportFreshness(List<Component> projects, int staleDays) {
    LocalDateTime now = LocalDateTime.now(session.clock());
    int never = 0;
    int stale = 0;
    int fresh = 0;
    for (Component p : projects) {
      LocalDateTime d = parseDate(p.analysisDate(), session.zone());
      if (d == null) never++;
      else if (ChronoUnit.DAYS.between(d, now) > staleDays) stale++;
      else fresh++;
    }
    out.println();
    out.print(Text.format("  Analysés il y a < %d j        : %d%n", staleDays, fresh));
    out.print(
        Text.format(
            "  Analysés il y a > %d j        : %s%n",
            staleDays, out.color(String.valueOf(stale), YELLOW)));
    out.print(
        Text.format(
            "  Jamais analysés                : %s%n", out.color(String.valueOf(never), YELLOW)));
    if (never > 0) {
      out.println(
          out.color("    (projets créés puis abandonnés, " + "ou analyse jamais configurée)", DIM));
    }
  }
}
