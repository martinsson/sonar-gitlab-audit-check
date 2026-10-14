package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.c;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/** 1. Identité : ce qu'est le projet, son âge, sa taille. */
final class IdentitySection {

  private final Report report;
  private final LocalDateTime now;

  IdentitySection(Report report, LocalDateTime now) {
    this.report = report;
    this.now = now;
  }

  void print(Project p) {
    report.title("1. Le projet");
    report.println(
        "  Chemin                          : %s"
            .formatted(c(Format.orEmpty(p.pathWithNamespace()), BOLD)));
    report.println(
        "  Id / branche par défaut         : %s / %s"
            .formatted(p.id(), Format.orNa(p.defaultBranch())));
    report.println(
        "  Visibilité                      : %s%s"
            .formatted(
                Format.orNa(p.visibility()),
                Boolean.TRUE.equals(p.archived()) ? c("   ARCHIVÉ", YELLOW) : ""));
    report.println(
        "  Créé le                         : %s%s"
            .formatted(Dates.dateOnly(p.createdAt()), ago(p.createdAt())));
    report.println(
        "  Dernière activité               : %s%s"
            .formatted(Dates.dateOnly(p.lastActivityAt()), ago(p.lastActivityAt())));
    statistics(p.statistics());
    if (Format.isBlank(p.defaultBranch())) {
      report.println("  → dépôt sans branche par défaut : vide, ou jamais poussé.", YELLOW);
    }
  }

  private String ago(@Nullable String date) {
    LocalDateTime d = Dates.parseLocal(date);
    return d == null ? "" : c("   (il y a %d j)".formatted(ChronoUnit.DAYS.between(d, now)), DIM);
  }

  private void statistics(Project.@Nullable Statistics s) {
    if (s != null) {
      report.println(
          "  Commits (toute l'histoire)      : %s"
              .formatted(Format.orNa(Format.str(s.commitCount()))));
      report.println(
          "  Taille du dépôt                 : %s"
              .formatted(Format.humanBytes(s.repositorySize())));
    } else {
      // statistics=true est ignoré silencieusement en dessous de Reporter.
      report.println("  Statistiques du dépôt           : indisponibles (rôle < Reporter ?)", DIM);
    }
  }
}
