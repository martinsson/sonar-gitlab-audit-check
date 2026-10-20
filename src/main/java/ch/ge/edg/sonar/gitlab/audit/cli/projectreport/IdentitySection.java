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
        Text.format(
            "  Chemin                          : %s",
            report.paint(Text.orEmpty(p.pathWithNamespace()), BOLD)));
    report.println(
        Text.format(
            "  Id / branche par défaut         : %s / %s", p.id(), Text.orNa(p.defaultBranch())));
    report.println(
        Text.format(
            "  Visibilité                      : %s%s",
            Text.orNa(p.visibility()),
            Boolean.TRUE.equals(p.archived()) ? report.paint("   ARCHIVÉ", YELLOW) : ""));
    report.println(
        Text.format(
            "  Créé le                         : %s%s",
            dates.dateOnly(p.createdAt()), ago(p.createdAt())));
    report.println(
        Text.format(
            "  Dernière activité               : %s%s",
            dates.dateOnly(p.lastActivityAt()), ago(p.lastActivityAt())));
    statistics(p.statistics());
    if (Text.isBlank(p.defaultBranch())) {
      report.println("  → dépôt sans branche par défaut : vide, ou jamais poussé.", YELLOW);
    }
  }

  private String ago(@Nullable String date) {
    LocalDateTime d = dates.parseLocal(date);
    return d == null
        ? ""
        : report.paint(
            Text.format("   (il y a %d j)", ChronoUnit.DAYS.between(d, dates.now())), DIM);
  }

  private void statistics(Project.@Nullable Statistics s) {
    if (s != null) {
      report.println(
          Text.format(
              "  Commits (toute l'histoire)      : %s",
              Text.orNa(ReportFormat.str(s.commitCount()))));
      report.println(
          Text.format(
              "  Taille du dépôt                 : %s",
              ReportFormat.humanBytes(s.repositorySize())));
    } else {
      // statistics=true est ignoré silencieusement en dessous de Reporter.
      report.println("  Statistiques du dépôt           : indisponibles (rôle < Reporter ?)", DIM);
    }
  }
}
