package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.PRESET_LEFT;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.PRESET_RIGHT;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.run;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.stable;
import static ch.ge.edg.sonar.gitlab.audit.cli.mapper.ProjectMapperFixtures.write;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Help, colours and the ways a command line or an input file can be refused. */
class ProjectMapperOptionsCliTest {

  @TempDir Path dir;

  @Test
  void helpListsEveryOptionAndTheFooter() {
    Cli.Result r = Cli.run(ProjectMapper::new, "--help");

    assertThat(r.exitCode()).isZero();
    expectSelfie(r.out()).toMatchDisk();
  }

  @Test
  void coloursAlwaysWrapsTitlesAndHintsInAnsiCodes() throws Exception {
    Path left = write(dir, "pratiques.csv", PRESET_LEFT);
    Path right = write(dir, "inventaire.csv", PRESET_RIGHT);
    Path out = dir.resolve("m.csv");
    write(dir, "m.csv", "gauche_path,droite_key,methode,confiance\nteam-a/beta,gone,x,exact\n");

    Cli.Result r =
        Cli.run(
            ProjectMapper::new,
            "--color",
            "always",
            "--left",
            left.toString(),
            "--right",
            right.toString(),
            "--out",
            out.toString(),
            "--reapparier");

    assertThat(r.exitCode()).isZero();
    expectSelfie(stable(r.out(), dir).replace("\033", "<ESC>")).toMatchDisk();
  }

  @Test
  void missingRequiredOptionsAreAUsageError() {
    Cli.Result r = run("--right", "x.csv");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.out()).isEmpty();
    assertThat(r.err()).startsWith("Missing required option: '--left=<leftCsv>'");
  }

  @Test
  void anUnreadableMethodIsAUsageError() throws Exception {
    Path left = write(dir, "a.csv", "path\nx\n");
    Cli.Result r = run("--left", left.toString(), "--right", left.toString(), "--exact", "nope");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err())
        .startsWith("méthode illisible : « nope » (attendu id:gauche=droite[?colonne=valeur])");
  }

  @Test
  void anUnreadableNamesSpecIsAUsageError() throws Exception {
    Path left = write(dir, "a.csv", "path\nx\n");
    Cli.Result r = run("--left", left.toString(), "--right", left.toString(), "--names", "nope");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).startsWith("--names illisible : « nope » (attendu gauche=droite1,droite2)");
  }

  @Test
  void aLeftFileWithoutItsKeyColumnIsRefused() throws Exception {
    Path left = write(dir, "a.csv", "nom\nx\n");
    Path right = write(dir, "b.csv", "key\nx\n");
    Cli.Result r = run("--left", left.toString(), "--right", right.toString());

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.out()).isEmpty();
    assertThat(stable(r.err(), dir))
        .contains("colonne « path » absente de <tmp>/a.csv — ce fichier vient-il bien de --left ?");
  }

  @Test
  void aRightFileWithoutItsKeyColumnIsRefused() throws Exception {
    Path left = write(dir, "a.csv", "path\nx\n");
    Path right = write(dir, "b.csv", "nom\nx\n");
    Cli.Result r = run("--left", left.toString(), "--right", right.toString());

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(stable(r.err(), dir))
        .contains("colonne « key » absente de <tmp>/b.csv — ce fichier vient-il bien de --right ?");
  }

  @Test
  void aMissingInputFileFails() {
    Cli.Result r = run("--left", dir.resolve("none.csv").toString(), "--right", "x.csv");

    assertThat(r.exitCode()).isEqualTo(1);
    assertThat(r.err()).contains("NoSuchFileException");
  }
}
