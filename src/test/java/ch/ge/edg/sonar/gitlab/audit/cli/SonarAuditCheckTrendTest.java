package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.HistoryMeasure;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.HistoryPoint;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.SearchHistory;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Trend;
import ch.ge.edg.sonar.gitlab.audit.cli.SonarAuditCheck.Velocity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** The arithmetic behind the trend columns and the debt velocity. Dates carry no offset. */
class SonarAuditCheckTrendTest {

  private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 12, 0);

  private static TreeMap<LocalDateTime, Double> series(double... dayValuePairs) {
    TreeMap<LocalDateTime, Double> s = new TreeMap<>();
    for (int i = 0; i + 1 < dayValuePairs.length; i += 2) {
      s.put(T0.plusMinutes((long) (dayValuePairs[i] * 1440)), dayValuePairs[i + 1]);
    }
    return s;
  }

  private static String day(int d) {
    return T0.plusDays(d).toString() + ":00";
  }

  private static List<HistoryPoint> pts(Object... dayValue) {
    return IntStream.range(0, dayValue.length / 2)
        .mapToObj(
            i -> new HistoryPoint(day((Integer) dayValue[2 * i]), (String) dayValue[2 * i + 1]))
        .toList();
  }

  private static SearchHistory history(
      List<HistoryPoint> v, List<HistoryPoint> n, List<HistoryPoint> r) {
    return new SearchHistory(
        null,
        List.of(
            new HistoryMeasure("violations", v),
            new HistoryMeasure("ncloc", n),
            new HistoryMeasure("sqale_debt_ratio", r)));
  }

  @Test
  void slopeIsLeastSquaresPerDay() {
    assertThat(SonarAuditCheck.slopePerDay(series(0, 10, 7, 17, 14, 24)))
        .isCloseTo(1.0, within(1e-9));
    assertThat(SonarAuditCheck.slopePerDay(series(0, 10, 10, 0, 20, 20)))
        .isCloseTo(0.5, within(1e-9));
  }

  @Test
  void slopeNeedsThreePointsOverFourteenDays() {
    assertThat(SonarAuditCheck.slopePerDay(series(0, 1, 30, 2))).isNull();
    assertThat(SonarAuditCheck.slopePerDay(series(0, 1, 6, 2, 13.99, 3))).isNull();
    assertThat(SonarAuditCheck.slopePerDay(series(0, 1, 7, 2, 14, 3)))
        .isCloseTo(1.0 / 7, within(1e-9));
  }

  @Test
  void slopeUsesMinutesNotWholeDays() {
    assertThat(SonarAuditCheck.slopePerDay(series(0, 0, 0.5, 0, 14.5, 14)))
        .isCloseTo(399 / 406.5, within(1e-12));
  }

  @Test
  void relativeSlopeIsPercentOfTheMeanPerThirtyDays() {
    // slope 1/day, mean 17 -> 30/17*100
    assertThat(SonarAuditCheck.relativePerMonth(series(0, 10, 7, 17, 14, 24)))
        .isCloseTo(3000.0 / 17, within(1e-9));
  }

  @Test
  void relativeSlopeIsNullWithoutSlopeOrWithANonPositiveMean() {
    assertThat(SonarAuditCheck.relativePerMonth(series(0, 1))).isNull();
    assertThat(SonarAuditCheck.relativePerMonth(series(0, 0, 7, 0, 14, 0))).isNull();
    assertThat(SonarAuditCheck.relativePerMonth(series(0, -10, 7, 0, 14, -2))).isNull();
  }

  @Test
  void trendCombinesTheThreeSeries() {
    Trend t =
        SonarAuditCheck.trend(
            history(
                pts(0, "100", 15, "110", 30, "120"),
                pts(0, "1000", 15, "1000", 30, "0", 40, "1000"),
                pts(0, "5", 15, "5", 30, "5")));
    assertThat(t.points()).isEqualTo(4);
    assertThat(t.spanDays()).isEqualTo(40);
    assertThat(t.violationsPerMonth()).isCloseTo(20.0, within(1e-9));
    assertThat(t.violationsPerDay()).isCloseTo(20.0 / 30, within(1e-9));
    // density only where ncloc > 0: two dates, too few for a slope.
    assertThat(t.densityPctPerMonth()).isNull();
    assertThat(t.debtRatioPctPerMonth()).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void trendOfNothingIsZeroPointsAndNulls() {
    Trend t = SonarAuditCheck.trend(new SearchHistory(null, null));
    assertThat(t).isEqualTo(new Trend(0, 0, null, null, null));
    assertThat(t.violationsPerDay()).isNull();
  }

  @Test
  void trendOfASinglePointHasNoSpan() {
    Trend t = SonarAuditCheck.trend(history(pts(3, "1"), List.of(), List.of()));
    assertThat(t.points()).isEqualTo(1);
    assertThat(t.spanDays()).isZero();
  }

  @Test
  void trendSkipsUnparsableDatesAndValues() {
    List<HistoryPoint> v =
        List.of(
            new HistoryPoint("garbage", "1"),
            new HistoryPoint(null, "1"),
            new HistoryPoint(day(0), "abc"),
            new HistoryPoint(day(5), "2"));
    Trend t = SonarAuditCheck.trend(history(v, List.of(), List.of()));
    assertThat(t.points()).isEqualTo(1);
  }

  @Test
  void densityIsViolationsPerThousandLines() {
    Trend t =
        SonarAuditCheck.trend(
            history(
                pts(0, "10", 15, "20", 30, "30"),
                pts(0, "1000", 15, "1000", 30, "1000"),
                List.of()));
    // density 10, 20, 30 per kLOC: slope 2/3 per day, mean 20 -> 100 % per month
    assertThat(t.densityPctPerMonth()).isCloseTo(100.0, within(1e-9));
    assertThat(t.debtRatioPctPerMonth()).isNull();
  }

  @Test
  void numericReadsDecimalsAndRejectsTheRest() {
    assertThat(SonarAuditCheck.numeric(" 12.5 ")).isEqualTo(12.5);
    assertThat(SonarAuditCheck.numeric("-3")).isEqualTo(-3.0);
    assertThat(SonarAuditCheck.numeric(null)).isNull();
    assertThat(SonarAuditCheck.numeric("  ")).isNull();
    assertThat(SonarAuditCheck.numeric("12,5")).isNull();
  }

  @Test
  void deltaIsLastMinusFirstTruncated() {
    assertThat(SonarAuditCheck.delta(pts(0, "10.9", 5, "x", 9, "20.5"))).isEqualTo(9L);
    assertThat(SonarAuditCheck.delta(pts(0, "20", 9, "10.5"))).isEqualTo(-9L);
  }

  @Test
  void deltaNeedsTwoReadableEnds() {
    assertThat(SonarAuditCheck.delta(null)).isNull();
    assertThat(SonarAuditCheck.delta(pts(0, "1"))).isNull();
    assertThat(SonarAuditCheck.delta(pts(0, "x", 9, "1"))).isNull();
    assertThat(SonarAuditCheck.delta(pts(0, "1", 9, "x"))).isNull();
  }

  @Test
  void velocityTakesTheSpanFromTheLineSeries() {
    Velocity v = SonarAuditCheck.velocity(pts(2, "100", 12, "150"), pts(0, "10", 30, "35"));
    assertThat(v).isEqualTo(new Velocity(50, 25, 10, day(2), day(12)));
    assertThat(v.debtPerLine()).isEqualTo(0.5);
  }

  @Test
  void velocityIsNullWhenEitherDeltaIsMissing() {
    assertThat(SonarAuditCheck.velocity(pts(0, "1"), pts(0, "1", 5, "2"))).isNull();
    assertThat(SonarAuditCheck.velocity(pts(0, "1", 5, "2"), pts(0, "1"))).isNull();
  }

  @Test
  void velocityWithUnreadableDatesHasNoSpan() {
    List<HistoryPoint> lines = List.of(new HistoryPoint("bad", "1"), new HistoryPoint(day(4), "3"));
    assertThat(SonarAuditCheck.velocity(lines, pts(0, "1", 5, "2")).spanDays()).isZero();
    List<HistoryPoint> lines2 = List.of(new HistoryPoint(day(4), "1"), new HistoryPoint(null, "3"));
    Velocity v = SonarAuditCheck.velocity(lines2, pts(0, "1", 5, "2"));
    assertThat(v.spanDays()).isZero();
    assertThat(v.to()).isEmpty();
  }

  @Test
  void pointsForKeepsOnlyNonBlankValuesOfTheFirstMatchingMetric() {
    SearchHistory h =
        new SearchHistory(
            null,
            List.of(
                new HistoryMeasure(
                    "ncloc", List.of(new HistoryPoint("d1", "1"), new HistoryPoint("d2", " "))),
                new HistoryMeasure("ncloc", List.of(new HistoryPoint("d3", "3"))),
                new HistoryMeasure("violations", null)));
    assertThat(h.pointsFor("ncloc")).containsExactly(new HistoryPoint("d1", "1"));
    assertThat(h.pointsFor("violations")).isEmpty();
    assertThat(h.pointsFor("coverage")).isEmpty();
  }

  @Test
  void thresholdsAreThePublishedOnes() {
    assertThat(SonarAuditCheck.TREND_MIN_POINTS).isEqualTo(3);
    assertThat(SonarAuditCheck.TREND_MIN_SPAN_DAYS).isEqualTo(14);
    assertThat(SonarAuditCheck.TREND_FLAT_PCT).isEqualTo(2.0);
  }
}
