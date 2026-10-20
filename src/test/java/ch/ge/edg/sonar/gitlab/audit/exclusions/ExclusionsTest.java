package ch.ge.edg.sonar.gitlab.audit.exclusions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExclusionsTest {

  @TempDir Path dir;

  private Exclusions load(String content) throws IOException {
    Path f = dir.resolve("exclusions.txt");
    Files.writeString(f, content, StandardCharsets.UTF_8);
    return Exclusions.load(f);
  }

  @Test
  void entries_are_trimmed_and_comments_and_blank_lines_dropped() throws IOException {
    Exclusions e =
        load(
            """
            team-a/sandbox        # throwaway prototype

               # only a comment
              training/** \s
            *-archive#no space before the comment
            \t
            """);
    assertThat(e.entries()).containsExactly("team-a/sandbox", "training/**", "*-archive");
    assertThat(e.patterns()).hasSize(3);
    assertThat(e.file()).isEqualTo(dir.resolve("exclusions.txt"));
    assertThat(e.isEmpty()).isFalse();
  }

  @Test
  void match_returns_the_entry_that_excludes_the_first_one_wins() throws IOException {
    Exclusions e = load("team-a/*\nteam-a/sandbox\n**/legacy\n");
    assertThat(e.match("team-a/sandbox")).isEqualTo("team-a/*");
    assertThat(e.match("team-b/x/legacy")).isEqualTo("**/legacy");
    assertThat(e.match("team-b/alpha-service")).isNull();
    assertThat(e.match(null)).isNull();
  }

  @Test
  void a_file_of_comments_only_is_empty_and_matches_nothing() throws IOException {
    Exclusions e = load("# nothing yet\n\n");
    assertThat(e.isEmpty()).isTrue();
    assertThat(e.entries()).isEmpty();
    assertThat(e.match("team-a/alpha")).isNull();
    assertThat(e.file()).isNotNull();
  }

  @Test
  void none_is_empty_without_a_file() {
    Exclusions e = Exclusions.none();
    assertThat(e.file()).isNull();
    assertThat(e.entries()).isEmpty();
    assertThat(e.isEmpty()).isTrue();
    assertThat(e.match("anything")).isNull();
  }

  @Test
  void without_an_explicit_file_and_no_default_file_nothing_is_excluded() throws IOException {
    assertThat(Exclusions.DEFAULT_FILE).isEqualTo("exclusions.txt");
    assertThat(Exclusions.file(null, dir)).isNull();
    Exclusions e = Exclusions.load(null);
    assertThat(e.file()).isNull();
    assertThat(e.isEmpty()).isTrue();
  }

  @Test
  void without_an_explicit_file_exclusions_txt_of_the_working_dir_is_read() throws IOException {
    Path dflt = dir.resolve("exclusions.txt");
    Files.writeString(dflt, "team-a/sandbox # synthetic\n", StandardCharsets.UTF_8);
    Exclusions e = Exclusions.load(Exclusions.file(null, dir));
    assertThat(e.file()).isEqualTo(dflt);
    assertThat(e.entries()).containsExactly("team-a/sandbox");
  }

  @Test
  void an_explicit_file_is_resolved_against_the_working_dir_even_when_absent() {
    assertThat(Exclusions.file(Path.of("mine.txt"), dir)).isEqualTo(dir.resolve("mine.txt"));
    assertThat(Exclusions.file(dir.resolve("abs.txt"), Path.of("elsewhere")))
        .isEqualTo(dir.resolve("abs.txt"));
  }

  @Test
  void an_explicit_file_that_does_not_exist_fails() {
    assertThatThrownBy(() -> Exclusions.load(dir.resolve("absent.txt")))
        .isInstanceOf(NoSuchFileException.class);
  }

  // ---------------------------------------------------------------- glob

  @Test
  void single_star_covers_one_segment_only() {
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/alpha").matches()).isTrue();
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/").matches()).isTrue();
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/x/alpha").matches()).isFalse();
    assertThat(Exclusions.glob("*-archive").matcher("alpha-archive").matches()).isTrue();
    assertThat(Exclusions.glob("*-archive").matcher("g/alpha-archive").matches()).isFalse();
  }

  @Test
  void double_star_covers_everything_including_slashes() {
    assertThat(Exclusions.glob("training/**").matcher("training/a/b/c").matches()).isTrue();
    assertThat(Exclusions.glob("training/**").matcher("training/").matches()).isTrue();
    assertThat(Exclusions.glob("training/**").matcher("training").matches()).isFalse();
    assertThat(Exclusions.glob("**").matcher("a/b").matches()).isTrue();
    assertThat(Exclusions.glob("a/***").matcher("a/b/c").matches()).isTrue();
  }

  @Test
  void a_trailing_single_star_is_one_segment() {
    assertThat(Exclusions.glob("a*").pattern()).isEqualTo("\\Qa\\E[^/]*");
    assertThat(Exclusions.glob("**x").pattern()).isEqualTo(".*\\Qx\\E");
  }

  @Test
  void question_mark_is_exactly_one_non_slash_character() {
    assertThat(Exclusions.glob("app?").matcher("app1").matches()).isTrue();
    assertThat(Exclusions.glob("app?").matcher("app").matches()).isFalse();
    assertThat(Exclusions.glob("app?").matcher("app12").matches()).isFalse();
    assertThat(Exclusions.glob("a?b").matcher("a/b").matches()).isFalse();
  }

  @Test
  void regex_metacharacters_are_literal() {
    assertThat(Exclusions.glob("a.b+(c)").matcher("a.b+(c)").matches()).isTrue();
    assertThat(Exclusions.glob("a.b").matcher("axb").matches()).isFalse();
    assertThat(Exclusions.glob("[x]").matcher("x").matches()).isFalse();
  }

  @Test
  void matching_ignores_case_including_accents() {
    assertThat(Exclusions.glob("Team-A/Équipe").matcher("team-a/équipe").matches()).isTrue();
    assertThat(Exclusions.glob("équipe").matcher("ÉQUIPE").matches()).isTrue();
  }

  @Test
  void the_whole_identifier_must_match_not_a_part() {
    assertThat(Exclusions.glob("alpha").matcher("alpha-service").matches()).isFalse();
    assertThat(Exclusions.glob("alpha").matcher("x/alpha").matches()).isFalse();
  }
}
