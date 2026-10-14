package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** The pure helpers of {@link GitLabProjectReport}. */
class GitLabProjectReportUnitTest {

  private Locale locale;
  private TimeZone zone;

  @BeforeEach
  void pin() {
    locale = Locale.getDefault();
    zone = TimeZone.getDefault();
    Locale.setDefault(Locale.ROOT);
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"));
  }

  @AfterEach
  void restore() {
    Locale.setDefault(locale);
    TimeZone.setDefault(zone);
    ConsoleOut.colorMode("never");
  }

  @Test
  void paths_are_taken_in_any_of_the_forms_a_user_pastes() {
    assertThat(GitLabProjectReport.normalizePath("team-a/alpha")).isEqualTo("team-a/alpha");
    assertThat(GitLabProjectReport.normalizePath("  /team-a/alpha/  ")).isEqualTo("team-a/alpha");
    assertThat(GitLabProjectReport.normalizePath("https://gitlab.example/team-a/sub/alpha.git"))
        .isEqualTo("team-a/sub/alpha");
    assertThat(GitLabProjectReport.normalizePath("https://gitlab.example/team-a/alpha/-/issues/3"))
        .isEqualTo("team-a/alpha");
    assertThat(GitLabProjectReport.normalizePath("https://gitlab.example")).isEmpty();
    assertThat(GitLabProjectReport.normalizePath("http://h//a//")).isEqualTo("a");
    assertThat(GitLabProjectReport.normalizePath("123")).isEqualTo("123");
    assertThat(GitLabProjectReport.normalizePath("://host/a/b")).isEqualTo("a/b");
    assertThat(GitLabProjectReport.normalizePath("a.git")).isEqualTo("a");
  }

  @Test
  void hours_pick_their_unit() {
    assertThat(GitLabProjectReport.hours(0)).isEqualTo("< 1 min");
    assertThat(GitLabProjectReport.hours(0.016)).isEqualTo("< 1 min");
    assertThat(GitLabProjectReport.hours(0.02)).isEqualTo("1 min");
    assertThat(GitLabProjectReport.hours(0.5)).isEqualTo("30 min");
    assertThat(GitLabProjectReport.hours(0.999)).isEqualTo("60 min");
    assertThat(GitLabProjectReport.hours(1)).isEqualTo("1.0 h");
    assertThat(GitLabProjectReport.hours(47.9)).isEqualTo("47.9 h");
    assertThat(GitLabProjectReport.hours(48)).isEqualTo("2.0 j");
    assertThat(GitLabProjectReport.hours(60)).isEqualTo("2.5 j");
  }

  @Test
  void bytes_are_human() {
    assertThat(GitLabProjectReport.humanBytes(null)).isEqualTo("n/a");
    assertThat(GitLabProjectReport.humanBytes(0L)).isEqualTo("0.0 o");
    assertThat(GitLabProjectReport.humanBytes(1023L)).isEqualTo("1023.0 o");
    assertThat(GitLabProjectReport.humanBytes(1024L)).isEqualTo("1.0 Ko");
    assertThat(GitLabProjectReport.humanBytes(1536L * 1024)).isEqualTo("1.5 Mo");
    assertThat(GitLabProjectReport.humanBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 Go");
    assertThat(GitLabProjectReport.humanBytes(5000L * 1024 * 1024 * 1024)).isEqualTo("5000.0 Go");
  }

  @Test
  void percentile_interpolates_on_a_sorted_list() {
    assertThat(GitLabProjectReport.percentile(List.of(), 50)).isZero();
    assertThat(GitLabProjectReport.percentile(List.of(7L), 90)).isEqualTo(7.0);
    assertThat(GitLabProjectReport.percentile(List.of(1L, 3L), 50)).isEqualTo(2.0);
    assertThat(GitLabProjectReport.percentile(List.of(0L, 10L, 20L, 30L, 40L), 90)).isEqualTo(36.0);
    assertThat(GitLabProjectReport.percentile(List.of(0L, 10L, 20L), 0)).isZero();
    assertThat(GitLabProjectReport.percentile(List.of(0L, 10L, 20L), 100)).isEqualTo(20.0);
  }

  @Test
  void bars_are_bounded() {
    assertThat(GitLabProjectReport.bar(0)).isEmpty();
    assertThat(GitLabProjectReport.bar(2.4)).isEmpty();
    assertThat(GitLabProjectReport.bar(2.5)).isEqualTo("#");
    assertThat(GitLabProjectReport.bar(50)).isEqualTo("#".repeat(10));
    assertThat(GitLabProjectReport.bar(100)).isEqualTo("#".repeat(20));
    assertThat(GitLabProjectReport.bar(250)).isEqualTo("#".repeat(20));
    assertThat(GitLabProjectReport.bar(-50)).isEmpty();
  }

  @Test
  void the_week_bar_scales_to_the_busiest_day() {
    assertThat(GitLabProjectReport.dowBar(new int[] {0, 0, 0, 0, 0, 0, 0}))
        .isEqualTo("L:▁ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁");
    assertThat(GitLabProjectReport.dowBar(new int[] {10, 1, 2, 3, 5, 7, 9}))
        .isEqualTo("L:█ M:▂ M:▂ J:▃ V:▅ S:▆ D:█");
  }

  @Test
  void dates_keep_their_offset_or_move_to_local_time() {
    assertThat(GitLabProjectReport.parseOffset("2024-03-04T10:00:00+02:00").getHour())
        .isEqualTo(10);
    assertThat(GitLabProjectReport.parseOffset(null)).isNull();
    assertThat(GitLabProjectReport.parseLocal("2024-03-04T10:00:00Z"))
        .isEqualTo(LocalDateTime.of(2024, 3, 4, 11, 0));
    assertThat(GitLabProjectReport.parseLocal("nope")).isNull();
    assertThat(GitLabProjectReport.dateOnly("2024-03-04T23:30:00Z")).isEqualTo("2024-03-05");
    assertThat(GitLabProjectReport.dateOnly("")).isEqualTo("n/a");
  }

  @Test
  void small_utilities() {
    assertThat(GitLabProjectReport.params("a", "1", "b", "2", "odd"))
        .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    assertThat(GitLabProjectReport.params()).isEmpty();
    assertThat(GitLabProjectReport.enc("a/b c")).isEqualTo("a%2Fb+c");
    assertThat(GitLabProjectReport.orZero(null)).isZero();
    assertThat(GitLabProjectReport.orZero(4)).isEqualTo(4);
    assertThat(GitLabProjectReport.str(null)).isNull();
    assertThat(GitLabProjectReport.str(12L)).isEqualTo("12");
    assertThat(GitLabProjectReport.isBlank(" ")).isTrue();
    assertThat(GitLabProjectReport.isBlank(null)).isTrue();
    assertThat(GitLabProjectReport.isBlank("x")).isFalse();
    assertThat(GitLabProjectReport.orEmpty(null)).isEmpty();
    assertThat(GitLabProjectReport.orEmpty("x")).isEqualTo("x");
    assertThat(GitLabProjectReport.orNa(" ")).isEqualTo("n/a");
    assertThat(GitLabProjectReport.orNa("x")).isEqualTo("x");
    assertThat(GitLabProjectReport.truncate("abcdef", 3)).isEqualTo("abc");
    assertThat(GitLabProjectReport.truncate("abc", 3)).isEqualTo("abc");
    assertThat(GitLabProjectReport.label(Map.of("id1", "Name"), "id1")).isEqualTo("Name");
    assertThat(GitLabProjectReport.label(Map.of(), "x".repeat(40))).isEqualTo("x".repeat(32));
  }

  @Test
  void merge_requests_read_the_merger_from_either_field() {
    GitLabProjectReport.User alice = new GitLabProjectReport.User(1L, "alice", "Alice");
    GitLabProjectReport.User bob = new GitLabProjectReport.User(2L, "bob", "Bob");
    GitLabProjectReport.User nameless = new GitLabProjectReport.User(3L, null, null);
    assertThat(mr(alice, bob, alice).mergedByUsername()).isEqualTo("bob");
    assertThat(mr(alice, nameless, alice).mergedByUsername()).isEqualTo("alice");
    assertThat(mr(alice, null, bob).mergedByUsername()).isEqualTo("bob");
    assertThat(mr(alice, null, null).mergedByUsername()).isNull();
    assertThat(mr(alice, null, null).authorUsername()).isEqualTo("alice");
    assertThat(mr(null, null, null).authorUsername()).isNull();
    assertThat(new GitLabProjectReport.Note(1L, false, null, bob).authorUsername())
        .isEqualTo("bob");
    assertThat(new GitLabProjectReport.Note(1L, false, null, null).authorUsername()).isNull();
  }

  static GitLabProjectReport.MergeRequest mr(
      GitLabProjectReport.User author,
      GitLabProjectReport.User mergeUser,
      GitLabProjectReport.User mergedBy) {
    return new GitLabProjectReport.MergeRequest(
        1L, "merged", "t", null, null, null, "main", false, false, 0, author, mergeUser, mergedBy);
  }

  @Test
  void verdict_tags_and_line_layout() {
    assertThat(GitLabProjectReport.Verdict.OK.tag).isEqualTo("OK      ");
    assertThat(GitLabProjectReport.Verdict.MISSING.tag).isEqualTo("ABSENT  ");
    assertThat(GitLabProjectReport.Verdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(GitLabProjectReport.Verdict.DENIED.color).isEqualTo(GitLabProjectReport.RED);
    assertThat(GitLabProjectReport.RESET).isEqualTo("\033[0m");
    ConsoleOut.colorMode("always");
    assertThat(GitLabProjectReport.c("x", GitLabProjectReport.GREEN)).isEqualTo("\033[32mx\033[0m");
  }

  @Test
  void defaults_are_those_documented() {
    GitLabProjectReport cmd = new GitLabProjectReport();
    new CommandLine(cmd).parseArgs("--path", "a/b");
    assertThat(cmd.days).isEqualTo(180);
    assertThat(cmd.maxCommits).isEqualTo(5000);
    assertThat(cmd.maxMrs).isEqualTo(500);
    assertThat(cmd.sample).isEqualTo(30);
    assertThat(cmd.timeout).isEqualTo(30);
    assertThat(cmd.includeBots).isFalse();
    assertThat(cmd.insecure).isFalse();
    assertThat(cmd.botPattern).isNull();
    assertThat(cmd.dumpDir).isNull();
    assertThat(cmd.colorMode).isEqualTo("auto");
  }

  @Test
  void the_path_is_required() {
    Cli.Result r = Cli.run(new GitLabProjectReport(), "--url", "http://127.0.0.1:9");

    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("Missing required option: '--path=<path>'");
  }
}
