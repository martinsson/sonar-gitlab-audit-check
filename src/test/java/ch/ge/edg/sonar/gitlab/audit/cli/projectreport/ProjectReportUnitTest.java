package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Printed;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
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
  void hoursPickTheirUnit() {
    assertThat(ReportFormat.hours(0)).isEqualTo("< 1 min");
    assertThat(ReportFormat.hours(0.016)).isEqualTo("< 1 min");
    assertThat(ReportFormat.hours(0.02)).isEqualTo("1 min");
    assertThat(ReportFormat.hours(0.5)).isEqualTo("30 min");
    assertThat(ReportFormat.hours(0.999)).isEqualTo("60 min");
    assertThat(ReportFormat.hours(1)).isEqualTo("1.0 h");
    assertThat(ReportFormat.hours(47.9)).isEqualTo("47.9 h");
    assertThat(ReportFormat.hours(48)).isEqualTo("2.0 j");
    assertThat(ReportFormat.hours(60)).isEqualTo("2.5 j");
  }

  @Test
  void bytesAreHuman() {
    assertThat(ReportFormat.humanBytes(null)).isEqualTo("n/a");
    assertThat(ReportFormat.humanBytes(0L)).isEqualTo("0.0 o");
    assertThat(ReportFormat.humanBytes(1023L)).isEqualTo("1023.0 o");
    assertThat(ReportFormat.humanBytes(1024L)).isEqualTo("1.0 Ko");
    assertThat(ReportFormat.humanBytes(1536L * 1024)).isEqualTo("1.5 Mo");
    assertThat(ReportFormat.humanBytes(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 Go");
    assertThat(ReportFormat.humanBytes(5000L * 1024 * 1024 * 1024)).isEqualTo("5000.0 Go");
  }

  @Test
  void percentileInterpolatesOnASortedList() {
    assertThat(ReportFormat.percentile(List.of(), 50)).isZero();
    assertThat(ReportFormat.percentile(List.of(7L), 90)).isEqualTo(7.0);
    assertThat(ReportFormat.percentile(List.of(1L, 3L), 50)).isEqualTo(2.0);
    assertThat(ReportFormat.percentile(List.of(0L, 10L, 20L, 30L, 40L), 90)).isEqualTo(36.0);
    assertThat(ReportFormat.percentile(List.of(0L, 10L, 20L), 0)).isZero();
    assertThat(ReportFormat.percentile(List.of(0L, 10L, 20L), 100)).isEqualTo(20.0);
  }

  @Test
  void barsAreBounded() {
    assertThat(ReportFormat.bar(0)).isEmpty();
    assertThat(ReportFormat.bar(2.4)).isEmpty();
    assertThat(ReportFormat.bar(2.5)).isEqualTo("#");
    assertThat(ReportFormat.bar(50)).isEqualTo("#".repeat(10));
    assertThat(ReportFormat.bar(100)).isEqualTo("#".repeat(20));
    assertThat(ReportFormat.bar(250)).isEqualTo("#".repeat(20));
    assertThat(ReportFormat.bar(-50)).isEmpty();
  }

  @Test
  void theWeekBarScalesToTheBusiestDay() {
    assertThat(ReportFormat.dowBar(new int[] {0, 0, 0, 0, 0, 0, 0}))
        .isEqualTo("L:▁ M:▁ M:▁ J:▁ V:▁ S:▁ D:▁");
    assertThat(ReportFormat.dowBar(new int[] {10, 1, 2, 3, 5, 7, 9}))
        .isEqualTo("L:█ M:▂ M:▂ J:▃ V:▅ S:▆ D:█");
  }

  @Test
  void datesKeepTheirOffsetOrMoveToLocalTime() {
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
  // Intended: a commit's day is the day in the run's clock zone (Geneva), the reader's calendar.
  void aCommitDayIsTheDayInTheZoneOfTheRunClock() {
    String lateEvening = "2024-03-04T23:30:00Z";
    String inUtc = new Dates(Clock.systemUTC()).dateOnly(lateEvening);
    String inZurich = new Dates(Clock.system(ZoneId.of("Europe/Zurich"))).dateOnly(lateEvening);
    assertThat(List.of(inUtc, inZurich)).containsExactly("2024-03-04", "2024-03-05");
  }

  @Test
  void hoursKeepADecimalPointUnderAFrenchDefaultLocale() {
    Locale.setDefault(Locale.FRANCE);
    assertThat(ReportFormat.hours(1.5)).isEqualTo("1.5 h");
  }

  @Test
  void smallUtilities() {
    assertThat(ProjectApi.params("a", "1", "b", "2", "odd"))
        .containsExactly(Map.entry("a", "1"), Map.entry("b", "2"));
    assertThat(ProjectApi.params()).isEmpty();
    assertThat(ProjectApi.enc("a/b c")).isEqualTo("a%2Fb+c");
    assertThat(ReportFormat.orZero(null)).isZero();
    assertThat(ReportFormat.orZero(4)).isEqualTo(4);
    assertThat(ReportFormat.str(null)).isNull();
    assertThat(ReportFormat.str(12L)).isEqualTo("12");
    assertThat(Text.isBlank(" ")).isTrue();
    assertThat(Text.isBlank(null)).isTrue();
    assertThat(Text.isBlank("x")).isFalse();
    assertThat(Text.orEmpty(null)).isEmpty();
    assertThat(Text.orEmpty("x")).isEqualTo("x");
    assertThat(Text.orNa(" ")).isEqualTo("n/a");
    assertThat(Text.orNa("x")).isEqualTo("x");
    assertThat(Text.truncate("abcdef", 3)).isEqualTo("abc");
    assertThat(Text.truncate("abc", 3)).isEqualTo("abc");
    assertThat(ReportFormat.label(Map.of("id1", "Name"), "id1")).isEqualTo("Name");
    assertThat(ReportFormat.label(Map.of(), "x".repeat(40))).isEqualTo("x".repeat(32));
  }

  @Test
  void mergeRequestsReadTheMergerFromEitherField() {
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
  void mergeRequestsAreReadInTwoPartsFromOneFlatObject() throws Exception {
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
  void verdictTagsAndLineLayout() {
    assertThat(EndpointVerdict.OK.tag).isEqualTo("OK      ");
    assertThat(EndpointVerdict.MISSING.tag).isEqualTo("ABSENT  ");
    assertThat(EndpointVerdict.EMPTY.tag).isEqualTo("VIDE    ");
    assertThat(EndpointVerdict.DENIED.color).isEqualTo(Report.RED);
    assertThat(Report.RESET).isEqualTo("\033[0m");
    assertThat(new Report(Printed.colored().console()).paint("x", Report.GREEN))
        .isEqualTo("\033[32mx\033[0m");
  }
}
