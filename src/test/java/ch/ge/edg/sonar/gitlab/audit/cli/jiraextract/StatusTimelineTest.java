package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.testing.JiraFixtures.json;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.cli.jiraextract.StatusTimeline.Transition;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StatusTimelineTest {

  private static final Map<String, String> CATEGORIES =
      Map.of("1", "new", "3", "indeterminate", "5", "indeterminate", "6", "done");

  private static String history(String created, String from, String to) {
    return "{\"created\":\"%s\",\"items\":[{\"field\":\"status\",\"fromString\":\"s%s\",\"to\":\"%s\",\"toString\":\"s%s\"}]}"
        .formatted(created, from, to, to);
  }

  private static StatusTimeline of(String... histories) {
    return StatusTimeline.of(
        json("{\"histories\":[" + String.join(",", histories) + "]}"), CATEGORIES);
  }

  private static Instant at(String day) {
    return Instant.parse("2026-03-" + day + "T00:00:00Z");
  }

  private static String day(String d) {
    return "2026-03-" + d + "T01:00:00.000+0100";
  }

  @Test
  void noChangelogNoTransitionNoDate() {
    StatusTimeline t = StatusTimeline.of(MissingNode.getInstance(), CATEGORIES);
    assertThat(t.transitions()).isEmpty();
    assertThat(t.inProgress()).isNull();
    assertThat(t.done()).isNull();
  }

  @Test
  void transitionsComeInDateOrderWithTheTargetCategory() {
    StatusTimeline t = of(history(day("05"), "3", "6"), history(day("02"), "1", "3"));
    assertThat(t.transitions())
        .containsExactly(
            new Transition(at("02"), "s1", "s3", "indeterminate"),
            new Transition(at("05"), "s3", "s6", "done"));
    assertThat(t.inProgress()).isEqualTo(at("02"));
    assertThat(t.done()).isEqualTo(at("05"));
  }

  @Test
  void theFirstEntryInProgressCounts() {
    StatusTimeline t = of(history(day("02"), "1", "3"), history(day("03"), "3", "5"));
    assertThat(t.inProgress()).isEqualTo(at("02"));
    assertThat(t.done()).isNull();
  }

  @Test
  void theLastDoneWinsAndReopeningClearsIt() {
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "6")).done())
        .isEqualTo(at("04"));
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "1")).done()).isNull();
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "3")).done()).isNull();
  }

  @Test
  void aStatusOfUnknownCategoryKeepsTheDoneDate() {
    StatusTimeline t = of(history(day("02"), "3", "6"), history(day("04"), "6", "999"));
    assertThat(t.done()).isEqualTo(at("02"));
    assertThat(t.transitions().get(1).category()).isEmpty();
  }

  @Test
  void otherFieldsAreNotTransitionsAndMissingNamesAreEmpty() {
    StatusTimeline t =
        StatusTimeline.of(
            json(
                "{\"histories\":[{\"created\":\"bad\",\"items\":["
                    + "{\"field\":\"assignee\",\"to\":\"3\"},{\"field\":\"status\",\"to\":\"3\"}]}]}"),
            CATEGORIES);
    assertThat(t.transitions()).containsExactly(new Transition(null, "", "", "indeterminate"));
  }
}
