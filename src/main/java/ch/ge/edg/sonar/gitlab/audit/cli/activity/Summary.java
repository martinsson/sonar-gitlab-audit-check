package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Fmt.plural;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Screen.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.OutputOptions.Outputs;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** La synthèse : l'entonnoir en effectifs, ses réserves, et les fichiers écrits. */
final class Summary {

  private final Gitlab gl;
  private final Screen con;
  private final Counters counters;
  private final Funnel funnel;
  private final Outputs outputs;

  Summary(Gitlab gl, Screen con, Counters counters, Funnel funnel, Outputs outputs) {
    this.gl = gl;
    this.con = con;
    this.counters = counters;
    this.funnel = funnel;
    this.outputs = outputs;
  }

  void print(List<Proj> all) {
    con.title("Synthèse");
    funnel();
    reserves(all);
    files();
    con.say(
        """
                  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
                  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
                  chaque projet, et la sélection porte son motif d'entrée.
                """,
        DIM);
  }

  private void funnel() {
    Console out = con.out();
    Counters n = counters;
    int calls = gl.calls();
    out.out()
        .printf(
            "  %d appels API (%d GET, %d GraphQL, %d 429 réessayés).%n",
            calls, calls - n.graphqlCalls, n.graphqlCalls, gl.throttled());
    out.out()
        .printf(
            "  Parc %d → retenus %d → frais %d → éligibles %d → sélectionnés %d.%n",
            n.inScope, n.kept, n.fresh, n.eligible, n.selected);
  }

  private void reserves(List<Proj> all) {
    Console out = con.out();
    Counters n = counters;
    if (n.notCounted > 0) {
      // La coupe d'activité n'est pas une exclusion comme les autres : les
      // projets qu'elle écarte n'ont pas été mesurés, ils ont été laissés
      // de côté. Le dénominateur du rapport le dit, sinon le total se lit
      // comme un parc.
      out.out()
          .printf(
              "  %d projets laissés de côté par --active-within %d, non mesurés.%n",
              n.notCounted, funnel.options().activeWithinDays);
    }
    if (n.sampleSize > 0) {
      out.out()
          .printf(
              "  Filtre de fraîcheur validé sur %d projets : %d faux négatif%s.%n",
              n.sampleSize, n.sampleLeaks, plural(n.sampleLeaks));
    }
    long truncated = all.stream().filter(p -> p.commits.truncated()).count();
    if (truncated > 0) {
      con.say(
          "  %d projets tronqués à %d pages de commits : leur volume est"
              .formatted(truncated, funnel.options().maxCommitPages),
          YELLOW);
      con.say("  un plancher, pas un compte. Ils sont marqués dans le CSV.", YELLOW);
    }
  }

  private void files() {
    Path inventory = outputs.inventory();
    Path pratiques = outputs.pratiques();
    Path shown = inventory != null ? inventory : pratiques;
    if (shown == null) {
      con.say(
          "  Aucun fichier écrit : ajoute --out-dir <rép> pour " + "conserver l'inventaire",
          YELLOW);
      con.say(
          "  et les pratiques. Le rapport ci-dessus ne se rejoue " + "pas sans les CSV.", YELLOW);
      return;
    }
    Console out = con.out();
    out.println("  Fichiers écrits :");
    if (inventory != null) out.out().printf("    inventaire : %s%n", inventory.toAbsolutePath());
    // Annoncer un fichier qu'on n'a pas écrit — aucun projet sélectionné,
    // donc pas de passe profonde — envoie chercher un chemin qui n'existe
    // pas. Le rapport ne doit pas mentir sur ses propres sorties.
    if (pratiques != null && Files.exists(pratiques)) {
      out.out().printf("    pratiques  : %s%n", pratiques.toAbsolutePath());
      con.say("    Les deux se lisent ensemble : pratiques.csv ne contient " + "que les", DIM);
      con.say(
          "    sélectionnés, l'inventaire porte le parc dont ils sortent. "
              + "Colonnes : COLUMNS.md.",
          DIM);
    } else if (pratiques != null) {
      con.say("    pratiques  : non écrit — aucun projet sélectionné.", YELLOW);
    }
    con.say("  " + Csv.openingHint(shown, outputs.comma()), DIM);
  }
}
