package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The pure helpers of {@link ProjectReport}. */
class ProjectReportUnitTest {

  private Locale locale = Locale.ROOT;
  private TimeZone zone = TimeZone.getDefault();

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
  }

  @Test
  void hours_pick_their_unit() {
    assertThat(Format.hours(0)).isEqualTo("< 1 min");
    assertThat(Format.hours(0.016)).isEqualTo("< 1 min");
    assertThat(Format.hours(0.02)).isEqualTo("1 min");
    assertThat(Format.hours(0.5)).isEqualTo("30 min");
    assertThat(Format.hours(0.999)).isEqualTo("60 min");
    assertThat(Format.hours(1)).isEqualTo("1.0 h");
    assertThat(Format.hours(47.9)).isEqualTo("47.9 h");
    assertThat(Format.hours(48)).isEqualTo("2.0 j");
    assertThat(Format.hours(60)).isEqualTo("2.5 j");
  }

  @Test
  void bytes_are_human() {
    assertThat(Format.humanBytes(null)).isEqualTo("n/a");
    assertThat(Format.humanBytes(0L)).isEqualTo("0.0 o");
    assertThat(Format.humanBytes(1023L)).isEqualTo("1023.0 o");
    assertThat(Format.humanBytes(1024L)).isEqualTo("1.0 Ko");
    assertThat(Format.humanBytes(1536L * 1024)).isEqualTo("1.5 Mo");
    assertThat(Format.humanBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 Go");
    assertThat(Format.humanBytes(5000L * 1024 * 1024 * 1024)).isEqualTo("5000.0 Go");
  }

  @Test
  void percentile_interpolates_on_a_sorted_list() {
    assertThat(Format.percentile(List.of(), 50)).isZero();
    assertThat(Format.percentile(List.of(7L), 90)).isEqualTo(7.0);
    assertThat(Format.percentile(List.of(1L, 3L), 50)).isEqualTo(2.0);
    assertThat(Format.percentile(List.of(0L, 10L, 20L, 30L, 40L), 90)).isEqualTo(36.0);
    assertThat(Format.percentile(List.of(0L, 10L, 20L), 0)).isZero();
    assertThat(Format.percentile(List.of(0L, 10L, 20L), 100)).isEqualTo(20.0);
  }

  @Test
  void bars_are_bounded() {
    assertThat(Format.bar(0)).isEmpty();
    assertThat(Format.bar(2.4)).isEmpty();
    assertThat(Format.bar(2.5)).isEqualTo("#");
    assertThat(Format.bar(50)).isEqualTo("#".repeat(10));
    assertThat(Format.bar(100)).isEqualTo("#".repeat(20));
    assertThat(Format.bar(250)).isEqualTo("#".repeat(20));
    assertThat(Format.bar(-50)).isEmpty();
  }

  @Test
  void the_week_bar_scales_to_the_busiest_day() {
    assertThat(Format.dowBar(new int[] {0, 0, 0, 0, 0, 0, 0}))
        .isEqualTo("L:▁ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁");
    assertThat(Format.dowBar(new int[] {10, 1, 2, 3, 5, 7, 9}))
        .isEqualTo("L:█ M:▂ M:▂ J:▃ V:▅ S:▆ D:█");
  }

  @Test
  void dates_keep_their_offset_or_move_to_local_time() {
    OffsetDateTime offset = Dates.parseOffset("2024-03-04T10:00:00+02:00");
    assertThat(Objects.requireNonNull(offset).getHour()).isEqualTo(10);
    assertThat(Dates.parseOffset(null)).isNull();
    Dates zurich = new Dates(Clock.system(ZoneId.of("Europe/Zurich")));
    assertThat(zurich.parseLocal("2024-03-04T10:00:00Z"))
        .isEqualTo(LocalDateTime.of(2024, 3, 4, 11, 0));
    assertThat(zurich.parseLocal("nope")).isNull();
    assertThat(zurich.dateOnly("2024-03-04T23:30:00Z")).isEqualTo("2024-03-05");
    assertThat(zurich.dateOnly("")).isEqualTo("n/a");
  }

  @Test
  @SuspectedBug("the day of a commit should not depend on the time zone of the machine")
  void the_same_instant_falls_on_another_day_in_another_system_zone() {
    String lateEvening = "2024-03-04T23:30:00Z";
    String inUtc = new Dates(Clock.systemUTC()).dateOnly(lateEvening);
    String inZurich = new Dates(Clock.system(ZoneId.of("Europe/Zurich"))).dateOnly(lateEvening);
    assertThat(List.of(inUtc, inZurich)).containsExactly("2024-03-04", "2024-03-05");
  }

  @Test
  @SuspectedBug("decimals should not follow the default locale: a French machine writes 1,5")
  void hours_use_a_decimal_comma_under_a_french_default_locale() {
    Locale.setDefault(Locale.FRANCE);
    assertThat(Format.hours(1.5)).isEqualTo("1,5 h");
  }

  @Test
  void small_utilities() {
    assertThat(ProjectApi.params("a", "1", "b", "2", "odd"))
        .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    assertThat(ProjectApi.params()).isEmpty();
    assertThat(ProjectApi.enc("a/b c")).isEqualTo("a%2Fb+c");
    assertThat(Format.orZero(null)).isZero();
    assertThat(Format.orZero(4)).isEqualTo(4);
    assertThat(Format.str(null)).isNull();
    assertThat(Format.str(12L)).isEqualTo("12");
    assertThat(Format.isBlank(" ")).isTrue();
    assertThat(Format.isBlank(null)).isTrue();
    assertThat(Format.isBlank("x")).isFalse();
    assertThat(Format.orEmpty(null)).isEmpty();
    assertThat(Format.orEmpty("x")).isEqualTo("x");
    assertThat(Format.orNa(" ")).isEqualTo("n/a");
    assertThat(Format.orNa("x")).isEqualTo("x");
    assertThat(Format.truncate("abcdef", 3)).isEqualTo("abc");
    assertThat(Format.truncate("abc", 3)).isEqualTo("abc");
    assertThat(Format.label(Map.of("id1", "Name"), "id1")).isEqualTo("Name");
    assertThat(Format.label(Map.of(), "x".repeat(40))).isEqualTo("x".repeat(32));
  }

  @Test
  void merge_requests_read_the_merger_from_either_field() {
    User alice = new User(1L, "alice", "Alice");
    User bob = new User(2L, "bob", "Bob");
    User nameless = new User(3L, null, null);
    assertThat(mr(alice, bob, alice).mergedByUsername()).isEqualTo("bob");
    assertThat(mr(alice, nameless, alice).mergedByUsername()).isEqualTo("alice");
    assertThat(mr(alice, null, bob).mergedByUsername()).isEqualTo("bob");
    assertThat(mr(alice, null, null).mergedByUsername()).isNull();
    assertThat(mr(alice, null, null).authorUsername()).isEqualTo("alice");
    assertThat(mr(null, null, null).authorUsername()).isNull();
    assertThat(new Note(1L, false, null, bob).authorUsername()).isEqualTo("bob");
    assertThat(new Note(1L, false, null, null).authorUsername()).isNull();
  }

  static MergeRequest mr(@Nullable User author, @Nullable User mergeUser, @Nullable User mergedBy) {
    return new MergeRequest(
        new MergeRequest.Facts(1L, "merged", null, null, false, 0),
        new MergeRequest.Participants(author, mergeUser, mergedBy));
  }

  @Test
  void merge_requests_are_read_in_two_parts_from_one_flat_object() throws Exception {
    MergeRequest m =
        Gitlab.MAPPER.readValue(
            """
            {"iid": 7, "state": "merged", "squash": true, "user_notes_count": 2,
             "author": {"username": "alice"}, "merge_user": {"username": "bob"}}""",
            MergeRequest.class);

    assertThat(m.iid()).isEqualTo(7L);
    assertThat(m.state()).isEqualTo("merged");
    assertThat(m.facts().squash()).isTrue();
    assertThat(m.facts().userNotesCount()).isEqualTo(2);
    assertThat(m.authorUsername()).isEqualTo("alice");
    assertThat(m.mergedByUsername()).isEqualTo("bob");
  }

  @Test
  void verdict_tags_and_line_layout() {
    assertThat(Verdict.OK.tag).isEqualTo("OK      ");
    assertThat(Verdict.MISSING.tag).isEqualTo("ABSENT  ");
    assertThat(Verdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(Verdict.DENIED.color).isEqualTo(Report.RED);
    assertThat(Report.RESET).isEqualTo("\033[0m");
    assertThat(new Report(Printed.colored().console()).c("x", Report.GREEN))
        .isEqualTo("\033[32mx\033[0m");
  }
}
