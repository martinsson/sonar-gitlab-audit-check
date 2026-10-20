package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.error.YAMLException;

/** The namespace mapping read from a YAML file, and what it says on the error stream. */
class NamespaceMappingTest {

  @TempDir Path dir;

  @Test
  void a_missing_file_only_warns() {
    NamespaceMapping m = NamespaceMapping.load(dir, Path.of("absent.yaml"));
    assertThat(m.warnings())
        .containsExactly(
            "Avertissement : fichier de mapping absent.yaml"
                + " introuvable — le matching namespace sera désactivé");
    assertThat(m.mappedGitlabPaths("a/b"))
        .isEqualTo(NamespaceMapping.empty().mappedGitlabPaths("a/b"));
  }

  @Test
  void an_unreadable_file_reports_the_error() throws Exception {
    Path f = Files.writeString(dir.resolve("locked.yaml"), "ZZMAP-LOCKED: x\n");
    Files.setPosixFilePermissions(f, Set.of());
    assertThat(NamespaceMapping.load(dir, f).warnings())
        .containsExactly("Erreur lecture mapping : " + f);
  }

  @Test
  @SuspectedBug(
      "Only IOException is caught, so a directory escapes the loader; a fix would report it as a warning")
  void a_directory_fails_inside_the_yaml_reader_uncaught() {
    assertThatThrownBy(() -> NamespaceMapping.load(dir, Path.of("")))
        .isInstanceOf(YAMLException.class);
  }

  @Test
  void an_empty_file_maps_nothing() throws Exception {
    Path f = Files.writeString(dir.resolve("empty.yaml"), "");
    assertThat(NamespaceMapping.load(dir, f).warnings()).isEmpty();
  }

  @Test
  void an_uppercase_namespace_maps_to_its_prefix() throws Exception {
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
  @SuspectedBug(
      "Lookup is upper-cased but file keys are kept as written; a fix would match lowercase keys too")
  void a_lowercase_namespace_in_the_file_never_matches() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "zzmap-low: zz_low_prefix\n");
    NamespaceMapping m = NamespaceMapping.load(dir, f);
    assertThat(m.mappedGitlabPaths("zzmap-low/alpha")).containsExactly("zzmap-low-alpha", "alpha");
  }

  @Test
  @SuspectedBug(
      "A non-string value escapes the loader as ClassCastException; a fix would report it as a warning")
  void a_non_string_value_fails_with_a_class_cast() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "ZZMAP-NUM: 42\n");
    assertThatThrownBy(() -> NamespaceMapping.load(dir, f)).isInstanceOf(ClassCastException.class);
  }
}
