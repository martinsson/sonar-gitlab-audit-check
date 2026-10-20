package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/** 1. Identité : ce qu'est le projet, son âge, sa taille. */
final class IdentitySection {

  private final Report report;
  private final Dates dates;

  IdentitySection(Report report, Dates dates) {
    this.report = report;
    this.dates = dates;
  }

  void print(Project p) {
    report.title("1. Le projet");
    report.println(
        "  Chemin                          : %s"
            .formatted(report.c(Text.orEmpty(p.pathWithNamespace()), BOLD)));
    report.println(
        "  Id / branche par défaut         : %s / %s"
            .formatted(p.id(), Text.orNa(p.defaultBranch())));
    report.println(
        "  Visibilité                      : %s%s"
            .formatted(
                Text.orNa(p.visibility()),
                Boolean.TRUE.equals(p.archived()) ? report.c("   ARCHIVÉ", YELLOW) : ""));
    report.println(
        "  Créé le                         : %s%s"
            .formatted(dates.dateOnly(p.createdAt()), ago(p.createdAt())));
    report.println(
        "  Dernière activité               : %s%s"
            .formatted(dates.dateOnly(p.lastActivityAt()), ago(p.lastActivityAt())));
    statistics(p.statistics());
    if (Text.isBlank(p.defaultBranch())) {
      report.println("  → dépôt sans branche par défaut : vide, ou jamais poussé.", YELLOW);
    }
  }

  private String ago(@Nullable String date) {
    LocalDateTime d = dates.parseLocal(date);
    return d == null
        ? ""
        : report.c("   (il y a %d j)".formatted(ChronoUnit.DAYS.between(d, dates.now())), DIM);
  }

  private void statistics(Project.@Nullable Statistics s) {
    if (s != null) {
      report.println(
          "  Commits (toute l'histoire)      : %s"
              .formatted(Text.orNa(Format.str(s.commitCount()))));
      report.println(
          "  Taille du dépôt                 : %s"
              .formatted(Format.humanBytes(s.repositorySize())));
    } else {
      // statistics=true est ignoré silencieusement en dessous de Reporter.
      report.println("  Statistiques du dépôt           : indisponibles (rôle < Reporter ?)", DIM);
    }
  }
}
