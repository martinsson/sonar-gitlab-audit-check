package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.PRESET_LEFT;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.PRESET_RIGHT;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.excelCsv;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.stable;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.write;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Replaying a mapping written by an earlier run: kept, manual, rejected, stale, orphans. */
class ProjectMapperReuseCliTest {

  /** An earlier mapping, edited by hand. Comma-separated: the reader detects it. */
  static final String EDITED =
      """
      gauche_path,droite_key,methode,confiance
      team-a/alpha-service,team-a:alpha,clé lue dans la CI,exact
      team-a/beta,org.example:gone,clé Maven du pom,exact
      team-b/gamma,org.example:gamma,a label nobody knows,derived
      team-c/101-delta,grp.delta:delta,,rejete
      team-f/common-ui,org.example:common-b,,manuel
      team-g/inventory-tracker,org.example:vanished,,manuel
      team-h/lonely-thing,,,suggestion
      team-j/report-engine,,,manuel
      team-k/report-engine, org.example:reporting-engine-svc ,,Confirmé
      team-m/epsilon-api,org.example:epsilon-api,noms ressemblants (TF-IDF),derived
      old/removed-project,org.example:x,saisi à la main,manuel
      ,org.example:ignored,,manuel
      """;

  @TempDir Path dir;
  Path left;
  Path right;
  Path out;

  @BeforeEach
  void inputs() throws Exception {
    left = write(dir, "pratiques.csv", PRESET_LEFT);
    right = write(dir, "inventaire.csv", PRESET_RIGHT);
    out = dir.resolve("correspondances.csv");
  }

  private Cli.Result map(String... extra) {
    String[] base = {
      "--left", left.toString(), "--right", right.toString(), "--out", out.toString()
    };
    String[] all = new String[base.length + extra.length];
    System.arraycopy(base, 0, all, 0, base.length);
    System.arraycopy(extra, 0, all, base.length, extra.length);
    return run(all);
  }

  @Test
  void an_edited_mapping_wins_where_it_decided_and_is_kept_in_a_bak() throws Exception {
    write(dir, "correspondances.csv", EDITED);
    Path report = dir.resolve("rapport.json");

    Cli.Result r = map("--report", report.toString());

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(excelCsv(out)).toMatchDisk("mapping");
    expectSelfie(Files.readString(report)).toMatchDisk("report");
    assertThat(Files.readString(dir.resolve("correspondances.csv.bak"))).isEqualTo(EDITED);
  }

  @Test
  void reapparier_recomputes_exact_and_derived_but_not_manual_or_rejected() throws Exception {
    write(dir, "correspondances.csv", EDITED);

    Cli.Result r = map("--reapparier", "--comma");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(out)).toMatchDisk("mapping");
  }

  @Test
  void a_second_run_on_its_own_output_changes_nothing() throws Exception {
    assertThat(map().exitCode()).isZero();
    String first = Files.readString(out);

    Cli.Result again = map();

    assertThat(again.exitCode()).isZero();
    assertThat(Files.readString(out)).isEqualTo(first);
    assertThat(Files.readString(dir.resolve("correspondances.csv.bak"))).isEqualTo(first);
    expectSelfie(stable(again.out(), dir)).toMatchDisk();
  }

  @Test
  void an_existing_file_that_is_not_a_mapping_is_refused() throws Exception {
    write(dir, "correspondances.csv", "gauche_path,droite_key\na,b\n");

    Cli.Result r = map();

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(stable(r.err(), dir))
        .contains(
            "colonne « confiance » absente de <tmp>/correspondances.csv"
                + " — ce fichier vient-il bien de un appariement déjà écrit ?");
    assertThat(dir.resolve("correspondances.csv.bak")).doesNotExist();
  }

  @Test
  void an_empty_existing_file_is_refused_too() throws Exception {
    write(dir, "correspondances.csv", "");

    Cli.Result r = map();

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("colonne « gauche_path » absente");
  }

  @Test
  void more_than_ten_stale_pairs_print_only_the_first_ten() throws Exception {
    StringBuilder l = new StringBuilder("nom\n");
    StringBuilder prev = new StringBuilder("gauche_nom,droite_code,methode,confiance\n");
    for (int i = 0; i < 12; i++) {
      l.append("p").append(i).append('\n');
      prev.append("p").append(i).append(",gone").append(i).append(",ref,exact\n");
    }
    left = write(dir, "l.csv", l.toString());
    right = write(dir, "r.csv", "code\np0\n");
    write(dir, "correspondances.csv", prev.toString());

    Cli.Result r = map("--left-key", "nom", "--right-key", "code", "--exact", "ref:nom=code");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir)).toMatchDisk("stdout");
    expectSelfie(Files.readString(out).replace("\r\n", "⏎\n")).toMatchDisk("mapping");
  }
}
