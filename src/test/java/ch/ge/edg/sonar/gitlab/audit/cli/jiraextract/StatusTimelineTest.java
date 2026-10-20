package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static ch.ge.edg.sonar.gitlab.audit.cli.jiraextract.JiraFixtures.json;
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
  void no_changelog_no_transition_no_date() {
    StatusTimeline t = StatusTimeline.of(MissingNode.getInstance(), CATEGORIES);
    assertThat(t.transitions()).isEmpty();
    assertThat(t.inProgress()).isNull();
    assertThat(t.done()).isNull();
  }

  @Test
  void transitions_come_in_date_order_with_the_target_category() {
    StatusTimeline t = of(history(day("05"), "3", "6"), history(day("02"), "1", "3"));
    assertThat(t.transitions())
        .containsExactly(
            new Transition(at("02"), "s1", "s3", "indeterminate"),
            new Transition(at("05"), "s3", "s6", "done"));
    assertThat(t.inProgress()).isEqualTo(at("02"));
    assertThat(t.done()).isEqualTo(at("05"));
  }

  @Test
  void the_first_entry_in_progress_counts() {
    StatusTimeline t = of(history(day("02"), "1", "3"), history(day("03"), "3", "5"));
    assertThat(t.inProgress()).isEqualTo(at("02"));
    assertThat(t.done()).isNull();
  }

  @Test
  void the_last_done_wins_and_reopening_clears_it() {
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "6")).done())
        .isEqualTo(at("04"));
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "1")).done()).isNull();
    assertThat(of(history(day("02"), "3", "6"), history(day("04"), "6", "3")).done()).isNull();
  }

  @Test
  void a_status_of_unknown_category_keeps_the_done_date() {
    StatusTimeline t = of(history(day("02"), "3", "6"), history(day("04"), "6", "999"));
    assertThat(t.done()).isEqualTo(at("02"));
    assertThat(t.transitions().get(1).category()).isEmpty();
  }

  @Test
  void other_fields_are_not_transitions_and_missing_names_are_empty() {
    StatusTimeline t =
        StatusTimeline.of(
            json(
                "{\"histories\":[{\"created\":\"bad\",\"items\":["
                    + "{\"field\":\"assignee\",\"to\":\"3\"},{\"field\":\"status\",\"to\":\"3\"}]}]}"),
            CATEGORIES);
    assertThat(t.transitions()).containsExactly(new Transition(null, "", "", "indeterminate"));
  }
}
