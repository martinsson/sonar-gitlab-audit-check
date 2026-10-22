package ch.ge.edg.sonar.gitlab.audit.gocd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MaterialTest {

  @Test
  void gitMaterialUrlComesFromItsDescription() {
    Material git = new Material("Git", "m", "URL: https://h/g/p.git, Branch: main");
    assertThat(git.isGit()).isTrue();
    assertThat(git.isPipeline()).isFalse();
    assertThat(git.gitUrl()).isEqualTo("https://h/g/p.git");
    assertThat(new Material("git", "m", "URL: git@h:g/p.git").gitUrl()).isEqualTo("git@h:g/p.git");
    assertThat(new Material("git", "m", "clone git@h:g/p.git now").gitUrl())
        .isEqualTo("git@h:g/p.git");
    assertThat(new Material("git", "m", "no url here").gitUrl()).isEmpty();
  }

  @Test
  void pipelineMaterialNamesItsUpstream() {
    Material dep = new Material("Pipeline", "alias", "build [ compile ]");
    assertThat(dep.isPipeline()).isTrue();
    assertThat(dep.upstreamName()).isEqualTo("build");
    assertThat(new Material("Pipeline", "alias", "odd").upstreamName()).isEqualTo("alias");
    assertThat(new Material("Pipeline", "alias", " [ x ]").upstreamName()).isEqualTo("alias");
  }
}
