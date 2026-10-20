package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.matching.Kind;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** What the command line refuses: methods and name specs it cannot read. */
class ProjectMapperSpecTest {

  private static MethodOptions mapper() {
    Host host = Cli.host();
    ProjectMapper mapper = new ProjectMapper(host);
    host.commandLine(mapper);
    return mapper.methods;
  }

  @Test
  void keyMethodParseRefusesWhatItCannotRead() {
    for (String bad : List.of("bad", "x:a", "id :a=b", "x:a=b?col", "x:a=b=c", ":a=b", "x y:a=b")) {
      assertThatThrownBy(() -> mapper().method(bad, Kind.EXACT))
          .isInstanceOf(CommandLine.ParameterException.class)
          .hasMessage(
              "méthode illisible : « " + bad + " » (attendu id:gauche=droite[?colonne=valeur])");
    }
  }

  @Test
  void nameSpecParseRefusesWhatItCannotRead() {
    for (String bad : List.of("=libelle", "nolabel")) {
      assertThatThrownBy(() -> mapper().nameSpec(bad, true))
          .isInstanceOf(CommandLine.ParameterException.class)
          .hasMessage("--names illisible : « " + bad + " » (attendu gauche=droite1,droite2)");
    }
  }
}
