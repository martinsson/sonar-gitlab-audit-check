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
  void entriesAreTrimmedAndCommentsAndBlankLinesDropped() throws IOException {
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
  void matchReturnsTheEntryThatExcludesTheFirstOneWins() throws IOException {
    Exclusions e = load("team-a/*\nteam-a/sandbox\n**/legacy\n");
    assertThat(e.match("team-a/sandbox")).isEqualTo("team-a/*");
    assertThat(e.match("team-b/x/legacy")).isEqualTo("**/legacy");
    assertThat(e.match("team-b/alpha-service")).isNull();
    assertThat(e.match(null)).isNull();
  }

  @Test
  void aFileOfCommentsOnlyIsEmptyAndMatchesNothing() throws IOException {
    Exclusions e = load("# nothing yet\n\n");
    assertThat(e.isEmpty()).isTrue();
    assertThat(e.entries()).isEmpty();
    assertThat(e.match("team-a/alpha")).isNull();
    assertThat(e.file()).isNotNull();
  }

  @Test
  void noneIsEmptyWithoutAFile() {
    Exclusions e = Exclusions.none();
    assertThat(e.file()).isNull();
    assertThat(e.entries()).isEmpty();
    assertThat(e.isEmpty()).isTrue();
    assertThat(e.match("anything")).isNull();
  }

  @Test
  void withoutAnExplicitFileAndNoDefaultFileNothingIsExcluded() throws IOException {
    assertThat(Exclusions.DEFAULT_FILE).isEqualTo("exclusions.txt");
    assertThat(Exclusions.file(null, dir)).isNull();
    Exclusions e = Exclusions.load(null);
    assertThat(e.file()).isNull();
    assertThat(e.isEmpty()).isTrue();
  }

  @Test
  void withoutAnExplicitFileExclusionsTxtOfTheWorkingDirIsRead() throws IOException {
    Path dflt = dir.resolve("exclusions.txt");
    Files.writeString(dflt, "team-a/sandbox # synthetic\n", StandardCharsets.UTF_8);
    Exclusions e = Exclusions.load(Exclusions.file(null, dir));
    assertThat(e.file()).isEqualTo(dflt);
    assertThat(e.entries()).containsExactly("team-a/sandbox");
  }

  @Test
  void anExplicitFileIsResolvedAgainstTheWorkingDirEvenWhenAbsent() {
    assertThat(Exclusions.file(Path.of("mine.txt"), dir)).isEqualTo(dir.resolve("mine.txt"));
    assertThat(Exclusions.file(dir.resolve("abs.txt"), Path.of("elsewhere")))
        .isEqualTo(dir.resolve("abs.txt"));
  }

  @Test
  void anExplicitFileThatDoesNotExistFails() {
    assertThatThrownBy(() -> Exclusions.load(dir.resolve("absent.txt")))
        .isInstanceOf(NoSuchFileException.class);
  }

  // ---------------------------------------------------------------- glob

  @Test
  void singleStarCoversOneSegmentOnly() {
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/alpha").matches()).isTrue();
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/").matches()).isTrue();
    assertThat(Exclusions.glob("team-a/*").matcher("team-a/x/alpha").matches()).isFalse();
    assertThat(Exclusions.glob("*-archive").matcher("alpha-archive").matches()).isTrue();
    assertThat(Exclusions.glob("*-archive").matcher("g/alpha-archive").matches()).isFalse();
  }

  @Test
  void doubleStarCoversEverythingIncludingSlashes() {
    assertThat(Exclusions.glob("training/**").matcher("training/a/b/c").matches()).isTrue();
    assertThat(Exclusions.glob("training/**").matcher("training/").matches()).isTrue();
    assertThat(Exclusions.glob("training/**").matcher("training").matches()).isFalse();
    assertThat(Exclusions.glob("**").matcher("a/b").matches()).isTrue();
    assertThat(Exclusions.glob("a/***").matcher("a/b/c").matches()).isTrue();
  }

  @Test
  void aTrailingSingleStarIsOneSegment() {
    assertThat(Exclusions.glob("a*").pattern()).isEqualTo("\\Qa\\E[^/]*");
    assertThat(Exclusions.glob("**x").pattern()).isEqualTo(".*\\Qx\\E");
  }

  @Test
  void questionMarkIsExactlyOneNonSlashCharacter() {
    assertThat(Exclusions.glob("app?").matcher("app1").matches()).isTrue();
    assertThat(Exclusions.glob("app?").matcher("app").matches()).isFalse();
    assertThat(Exclusions.glob("app?").matcher("app12").matches()).isFalse();
    assertThat(Exclusions.glob("a?b").matcher("a/b").matches()).isFalse();
  }

  @Test
  void regexMetacharactersAreLiteral() {
    assertThat(Exclusions.glob("a.b+(c)").matcher("a.b+(c)").matches()).isTrue();
    assertThat(Exclusions.glob("a.b").matcher("axb").matches()).isFalse();
    assertThat(Exclusions.glob("[x]").matcher("x").matches()).isFalse();
  }

  @Test
  void matchingIgnoresCaseIncludingAccents() {
    assertThat(Exclusions.glob("Team-A/Équipe").matcher("team-a/équipe").matches()).isTrue();
    assertThat(Exclusions.glob("équipe").matcher("ÉQUIPE").matches()).isTrue();
  }

  @Test
  void theWholeIdentifierMustMatchNotAPart() {
    assertThat(Exclusions.glob("alpha").matcher("alpha-service").matches()).isFalse();
    assertThat(Exclusions.glob("alpha").matcher("x/alpha").matches()).isFalse();
  }
}
