package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** What the command line refuses: methods and name specs it cannot read. */
class ProjectMapperSpecTest {

  @Test
  void key_method_parse_refuses_what_it_cannot_read() {
    for (String bad : List.of("bad", "x:a", "id :a=b", "x:a=b?col", "x:a=b=c", ":a=b", "x y:a=b")) {
      assertThatThrownBy(() -> ProjectMapper.method(bad, Kind.EXACT))
          .isInstanceOf(CommandLine.ParameterException.class)
          .hasMessage(
              "méthode illisible : « " + bad + " » (attendu id:gauche=droite[?colonne=valeur])");
    }
  }

  @Test
  void name_spec_parse_refuses_what_it_cannot_read() {
    for (String bad : List.of("=libelle", "nolabel")) {
      assertThatThrownBy(() -> ProjectMapper.nameSpec(bad, true))
          .isInstanceOf(CommandLine.ParameterException.class)
          .hasMessage("--names illisible : « " + bad + " » (attendu gauche=droite1,droite2)");
    }
  }
}
