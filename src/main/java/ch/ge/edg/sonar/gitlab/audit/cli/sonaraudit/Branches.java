package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.c;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.orEmptyList;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Branch;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.BranchList;
import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.Component;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * search_projects et measures/search ne connaissent que la branche principale. Un projet dont seule
 * une autre branche (develop, …) est analysée y apparaît sans date ni mesure, exactement comme un
 * projet jamais analysé. On demande donc ses branches à chaque projet et on retient celle dont
 * l'analyse est la plus récente — c'est le dernier scan qui intéresse l'audit, pas la branche.
 */
final class Branches {

  private final Session session;

  Branches(Session session) {
    this.session = session;
  }

  List<Component> resolveLatest(List<Component> projects) {
    // Un appel par projet : c'est la passe la plus chère de l'outil, et la
    // seule dont le coût grandit avec le parc. Elle se recouvre, sinon un
    // parc de 500 projets attend 500 fois le réseau, l'un après l'autre.
    List<Component> out = session.eachProject("  Branches lues", projects, this::onLatestBranch);
    report(projects, out);
    return out;
  }

  private Component onLatestBranch(Component p) {
    Branch b = latestBranch(p.key());
    return b == null ? p : p.onBranch(b.name(), Boolean.TRUE.equals(b.isMain()), b.analysisDate());
  }

  private void report(List<Component> before, List<Component> after) {
    int nonMain = 0;
    int rescued = 0;
    for (int i = 0; i < after.size(); i++) {
      if (!after.get(i).onOtherBranch()) continue;
      nonMain++;
      if (before.get(i).analysisDate() == null) rescued++;
    }
    PrintStream out = session.out();
    out.println(
        "  Dernier scan sur une branche non principale : " + c(String.valueOf(nonMain), BOLD));
    if (rescued > 0) {
      out.println(
          c("    dont %d sans aucune analyse de la branche principale".formatted(rescued), DIM));
      out.println(c("    (invisibles dans search_projects et measures/search)", DIM));
    }
  }

  /** Branche dont la dernière analyse est la plus récente, ou null si indisponible. */
  private @Nullable Branch latestBranch(String key) {
    BranchList bl =
        session.read("api/project_branches/list", params("project", key), BranchList.class);
    if (bl == null) return null;
    return orEmptyList(bl.branches()).stream()
        .filter(b -> parseDate(b.analysisDate()) != null)
        .max(Comparator.comparing(Branches::analysed))
        .orElse(null);
  }

  private static LocalDateTime analysed(Branch b) {
    return Objects.requireNonNull(parseDate(b.analysisDate()), "filtrée plus haut");
  }
}
