package ch.ge.edg.sonar.gitlab.audit.dora;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformsTest {

  @Test
  void words_in_names_suggest_platforms_in_order() {
    assertThat(Platforms.guess("appli-deploy-PRD", "stage_rec")).isEqualTo("prod|recette");
    assertThat(Platforms.guess("x-preprod", "uat")).isEqualTo("preprod|recette");
    assertThat(Platforms.guess("prod-production")).isEqualTo("prod");
    assertThat(Platforms.guess("product-build")).isEmpty();
    assertThat(Platforms.guess()).isEmpty();
  }

  @Test
  void words_keeps_the_spelling_used() {
    assertThat(Platforms.words("deploy-int-dev-Int")).containsExactly("int", "dev");
    assertThat(Platforms.words("build")).isEmpty();
  }
}
