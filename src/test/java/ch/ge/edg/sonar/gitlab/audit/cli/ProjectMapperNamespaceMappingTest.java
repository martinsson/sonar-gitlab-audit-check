package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * The namespace mapping is a static, process-wide table loaded from a YAML file. Tests here add
 * entries under namespaces no other fixture uses ({@code ZZMAP-*}), since nothing can remove them.
 */
class ProjectMapperNamespaceMappingTest {

  @TempDir Path dir;

  @BeforeAll
  static void loadTheClassFirst() {
    // Its static initialiser warns about the default mapping file: not part of what we capture.
    stderrOf(() -> ProjectMapper.normalise(""));
  }

  private static String stderrOf(Runnable r) {
    PrintStream old = System.err;
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      r.run();
    } finally {
      System.setErr(old);
    }
    return err.toString(StandardCharsets.UTF_8);
  }

  @Test
  void a_missing_file_only_warns() {
    String missing = dir.resolve("absent.yaml").toString();
    assertThat(stderrOf(() -> ProjectMapper.loadNamespaceMapping(missing)))
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
    assertThat(stderrOf(() -> ProjectMapper.loadNamespaceMapping(f.toString())))
        .isEqualTo("Erreur lecture mapping : " + f + System.lineSeparator());
  }

  @Test
  void a_directory_fails_inside_the_yaml_reader_uncaught() {
    String asDir = dir.toString();
    assertThatThrownBy(() -> ProjectMapper.loadNamespaceMapping(asDir))
        .isInstanceOf(YAMLException.class);
  }

  @Test
  void an_empty_file_maps_nothing() throws Exception {
    Path f = Files.writeString(dir.resolve("empty.yaml"), "");
    assertThat(stderrOf(() -> ProjectMapper.loadNamespaceMapping(f.toString()))).isEmpty();
  }

  @Test
  void an_uppercase_namespace_maps_to_its_prefix() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), " ZZMAP-UP :  zz_up_prefix \n");
    assertThat(stderrOf(() -> ProjectMapper.loadNamespaceMapping(f.toString()))).isEmpty();
    // Looked up uppercased, and the prefix is not normalised.
    assertThat(ProjectMapper.mappedGitlabPaths("zzmap-up/12-alpha"))
        .containsExactly("zz_up_prefix-alpha", "alpha");
    assertThat(ProjectMapper.mappedGitlabPaths("ZZMAP-UP/sub/beta"))
        .containsExactly("zz_up_prefix-sub-beta", "zz_up_prefix-beta", "sub-beta");
  }

  @Test
  void a_lowercase_namespace_in_the_file_never_matches() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "zzmap-low: zz_low_prefix\n");
    ProjectMapper.loadNamespaceMapping(f.toString());
    assertThat(ProjectMapper.mappedGitlabPaths("zzmap-low/alpha"))
        .containsExactly("zzmap-low-alpha", "alpha");
  }

  @Test
  void a_non_string_value_fails_with_a_class_cast() throws Exception {
    Path f = Files.writeString(dir.resolve("m.yaml"), "ZZMAP-NUM: 42\n");
    assertThatThrownBy(() -> ProjectMapper.loadNamespaceMapping(f.toString()))
        .isInstanceOf(ClassCastException.class);
  }
}
