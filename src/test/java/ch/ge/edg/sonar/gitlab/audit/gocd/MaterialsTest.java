package ch.ge.edg.sonar.gitlab.audit.gocd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MaterialsTest {

  private static JsonNode material(String type, String name, String description) throws Exception {
    return new ObjectMapper()
        .readTree(
            "{\"type\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"}"
                .formatted(type, name, description));
  }

  @Test
  void git_material_url_comes_from_its_description() throws Exception {
    JsonNode git = material("Git", "m", "URL: https://h/g/p.git, Branch: main");
    assertThat(Materials.isGit(git)).isTrue();
    assertThat(Materials.isPipeline(git)).isFalse();
    assertThat(Materials.gitUrl(git)).isEqualTo("https://h/g/p.git");
    assertThat(Materials.gitUrl(material("git", "m", "URL: git@h:g/p.git")))
        .isEqualTo("git@h:g/p.git");
    assertThat(Materials.gitUrl(material("git", "m", "clone git@h:g/p.git now")))
        .isEqualTo("git@h:g/p.git");
    assertThat(Materials.gitUrl(material("git", "m", "no url here"))).isEmpty();
  }

  @Test
  void pipeline_material_names_its_upstream() throws Exception {
    JsonNode dep = material("Pipeline", "alias", "build [ compile ]");
    assertThat(Materials.isPipeline(dep)).isTrue();
    assertThat(Materials.upstreamName(dep)).isEqualTo("build");
    assertThat(Materials.upstreamName(material("Pipeline", "alias", "odd"))).isEqualTo("alias");
    assertThat(Materials.upstreamName(material("Pipeline", "alias", " [ x ]"))).isEqualTo("alias");
  }
}
