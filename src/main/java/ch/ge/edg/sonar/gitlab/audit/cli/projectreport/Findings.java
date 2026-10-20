package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.cli.projectreport.Report.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 5. Constats. Rien ici n'est calculé : la section relit ce que les précédentes ont mesuré et n'en
 * retient que ce qui sort de l'ordinaire. Les seuils sont grossiers et assumés comme tels — ils
 * servent à orienter un entretien, pas à noter une équipe.
 */
final class Findings {

  private final Report report;
  private final Dates dates;
  private final List<String> found = new ArrayList<>();

  Findings(Report report, Dates dates) {
    this.report = report;
    this.dates = dates;
  }

  void print(Measured m) {
    report.title("5. Constats");
    activity(m.project(), m.cadence());
    review(m.cadence(), m.reviews());
    team(m.people());
    habits(m.commits(), m.cadence(), m.reviews());

    if (found.isEmpty()) {
      report.println("  Rien de saillant sur les seuils retenus.", GREEN);
      return;
    }
    for (String s : found) report.println("  → " + s, YELLOW);
    report.println();
    report.println(
        """
          Ces constats décrivent un procédé, pas une qualité de code. Le croisement
          avec Sonar — fréquence de modification contre complexité, quality gate
          contre MR fusionnées — reste à faire.\
        """,
        DIM);
  }

  private void activity(Project p, Cadence cadence) {
    if (Boolean.TRUE.equals(p.archived())) {
      found.add("Projet archivé : lecture historique seulement.");
    }
    LocalDateTime lastCommit = cadence.lastCommit();
    if (lastCommit == null) return;
    long idle = ChronoUnit.DAYS.between(lastCommit, dates.now());
    if (idle > 90) found.add(Text.format("Dernier commit il y a %d j : dépôt dormant.", idle));
    LocalDateTime activity = dates.parseLocal(p.lastActivityAt());
    if (activity != null && idle > 180 && ChronoUnit.DAYS.between(activity, dates.now()) < 90) {
      found.add(
          "Le dépôt bouge encore (MR, tickets) mais plus la branche par défaut :"
              + " le travail vit ailleurs, ou personne ne fusionne plus.");
    }
  }

  private void review(Cadence cadence, Reviews reviews) {
    if (cadence.commits() > 0 && reviews.noMrs()) {
      found.add(
          Text.format(
              "%d commits, aucune MR : le code arrive sans revue sur la branche par défaut.",
              cadence.commits()));
    }
    if (reviews.selfMergedShare() > 50) {
      found.add(
          Text.format(
              "%.0f %% des MR sont fusionnées par leur auteur : la revue est facultative.",
              reviews.selfMergedShare()));
    }
    if (reviews.mrs() > 0 && reviews.silentShare() > 60) {
      found.add(
          Text.format(
              "%.0f %% des MR n'ont aucun commentaire : la revue est un tampon.",
              reviews.silentShare()));
    }
    if (reviews.leadP90() > 30 * 24) {
      found.add(
          Text.format(
              "9e décile du délai de fusion à %s : une MR sur dix traîne un mois.",
              Format.hours(reviews.leadP90())));
    }
  }

  private void team(People people) {
    if (people.authors() > 0 && people.busFactor() <= 1) {
      found.add(
          Text.format(
              "Une seule personne signe 80 %% des commits (%.0f %% pour la première).",
              people.topShare()));
    }
    if (people.authors() > 2 && people.activeRecently() <= 1) {
      found.add(
          Text.format(
              "%d auteurs sur la fenêtre, %d actif sur 90 j : l'équipe s'est vidée.",
              people.authors(), people.activeRecently()));
    }
  }

  private void habits(Commits commits, Cadence cadence, Reviews reviews) {
    if (cadence.bigCommitShare() > 10) {
      found.add(
          Text.format(
              "%.0f %% des commits dépassent %d lignes : imports, code généré ou gros lots.",
              cadence.bigCommitShare(), CadenceSection.BIG_COMMIT));
    }
    if (cadence.weekendShare() > 25) {
      found.add(Text.format("%.0f %% des commits le week-end.", cadence.weekendShare()));
    }
    if (reviews.squashShare() > 80) {
      found.add(
          "Squash quasi systématique : l'historique par commit n'existe plus,"
              + " la cadence ci-dessus mesure le bouton « fusionner ».");
    }
    if (!commits.bots().isEmpty() && commits.bots().size() > commits.humans().size()) {
      found.add("Plus de commits de bots que d'humains : dépôt surtout maintenu par l'outillage.");
    }
  }
}
