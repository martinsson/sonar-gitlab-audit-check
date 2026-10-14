package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.error.YAMLException;

/** The namespace mapping read from a YAML file, and what it says on the error stream. */
class NamespaceMappingTest {

  @TempDir Path dir;

  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void a_missing_file_only_warns() {
    String missing = dir.resolve("absent.yaml").toString();
    NamespaceMapping.load(missing, err);
    assertThat(stderr())
        .isEqualTo(
            "Avertissement : fichier de mapping "
                + missing
                + " introuvable — le matching namespace sera désactivé"
                + System.lineSeparator());
  }

  @Test
  void an_unreadable_file_reports_the_error() throws Exception {
    Path f = Files.writeString(dir.resolve("locked.yaml"), "ZZMAP-LOCKED: x\n");
    Files.setPosixFilePermissions(f, Set.of());
    NamespaceMapping.load(f.toString(), err);
    assertThat(stderr()).isEqualTo("Erreur lecture mapping : " + f + System.lineSeparator());
  }

  @Test
  void a_directory_fails_inside_the_yaml_reader_uncaught() {
    String asDir = dir.toString();
    assertThatThrownBy(() -> NamespaceMapping.load(asDir, err)).isInstanceOf(YAMLException.class);
  }

  @Test
  void an_empty_file_maps_nothing() throws Exception {
    Path f = Files.writeString(dir.resolve("empty.yaml"), "");
    NamespaceMapping.load(f.toString(), err);
    assertThat(stderr()).isEmpty();
  }

  @Test
  void an_uppercase_namespace_maps_to_its_prefix() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), " ZZMAP-UP :  zz_up_prefix \n");
    NamespaceMapping m = NamespaceMapping.load(f.toString(), err);
    assertThat(stderr()).isEmpty();
    // Looked up uppercased, and the prefix is not normalised.
    assertThat(m.mappedGitlabPaths("zzmap-up/12-alpha"))
        .containsExactly("zz_up_prefix-alpha", "alpha");
    assertThat(m.mappedGitlabPaths("ZZMAP-UP/sub/beta"))
        .containsExactly("zz_up_prefix-sub-beta", "zz_up_prefix-beta", "sub-beta");
  }

  @Test
  void a_lowercase_namespace_in_the_file_never_matches() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "zzmap-low: zz_low_prefix\n");
    NamespaceMapping m = NamespaceMapping.load(f.toString(), err);
    assertThat(m.mappedGitlabPaths("zzmap-low/alpha")).containsExactly("zzmap-low-alpha", "alpha");
  }

  @Test
  void a_non_string_value_fails_with_a_class_cast() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "ZZMAP-NUM: 42\n");
    assertThatThrownBy(() -> NamespaceMapping.load(f.toString(), err))
        .isInstanceOf(ClassCastException.class);
  }
}
