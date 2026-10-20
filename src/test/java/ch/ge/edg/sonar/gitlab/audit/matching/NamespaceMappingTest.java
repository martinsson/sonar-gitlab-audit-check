package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The namespace mapping read from a YAML file, and what it says on the error stream. */
class NamespaceMappingTest {

  @TempDir Path dir;

  @Test
  void aMissingFileOnlyWarns() {
    NamespaceMapping m = NamespaceMapping.load(dir, Path.of("absent.yaml"));
    assertThat(m.warnings())
        .containsExactly(
            "Avertissement : fichier de mapping absent.yaml"
                + " introuvable — le matching namespace sera désactivé");
    assertThat(m.mappedGitlabPaths("a/b"))
        .isEqualTo(NamespaceMapping.empty().mappedGitlabPaths("a/b"));
  }

  @Test
  void anUnreadableFileReportsTheError() throws Exception {
    Path f = Files.writeString(dir.resolve("locked.yaml"), "ZZMAP-LOCKED: x\n");
    Files.setPosixFilePermissions(f, Set.of());
    assertThat(NamespaceMapping.load(dir, f).warnings())
        .containsExactly("Erreur lecture mapping : " + f);
  }

  @Test
  void aDirectoryIsReportedAsUnreadable() {
    assertThat(NamespaceMapping.load(dir, Path.of("")).warnings())
        .containsExactly("Erreur lecture mapping : " + dir);
  }

  @Test
  void anEmptyFileMapsNothing() throws Exception {
    Path f = Files.writeString(dir.resolve("empty.yaml"), "");
    assertThat(NamespaceMapping.load(dir, f).warnings()).isEmpty();
  }

  @Test
  void anUppercaseNamespaceMapsToItsPrefix() throws Exception {
    Files.writeString(dir.resolve("m.yaml"), " ZZMAP-UP :  zz_up_prefix \n");
    // A relative name is read from the working directory.
    NamespaceMapping m = NamespaceMapping.load(dir, Path.of("m.yaml"));
    assertThat(m.warnings()).isEmpty();
    // Looked up uppercased, and the prefix is not normalised.
    assertThat(m.mappedGitlabPaths("zzmap-up/12-alpha"))
        .containsExactly("zz_up_prefix-alpha", "alpha");
    assertThat(m.mappedGitlabPaths("ZZMAP-UP/sub/beta"))
        .containsExactly("zz_up_prefix-sub-beta", "zz_up_prefix-beta", "sub-beta");
  }

  @Test
  void aLowercaseNamespaceInTheFileMatchesToo() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "zzmap-low: zz_low_prefix\n");
    NamespaceMapping m = NamespaceMapping.load(dir, f);
    assertThat(m.mappedGitlabPaths("zzmap-low/alpha"))
        .containsExactly("zz_low_prefix-alpha", "alpha");
  }

  @Test
  // Intended (documented on load): a malformed mapping fails loudly rather than mapping half.
  void aNonStringValueFailsTheLoad() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "ZZMAP-NUM: 42\n");
    assertThatThrownBy(() -> NamespaceMapping.load(dir, f)).isInstanceOf(ClassCastException.class);
  }
}
