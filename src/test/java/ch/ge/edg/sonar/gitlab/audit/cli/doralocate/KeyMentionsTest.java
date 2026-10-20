package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class KeyMentionsTest {

  @Test
  void prefixes_rank_by_commits_plus_merge_requests_then_alphabetically() {
    Map<String, Mentions> by = new TreeMap<>();
    KeyMentions.tally(by, "AAA-1", Mentions::commit);
    KeyMentions.tally(by, "ZZZ-1 ZZZ-2", Mentions::commit);
    KeyMentions.tally(by, "ZZZ-3", Mentions::commit);
    KeyMentions.tally(by, "ZZZ-3 MMM-1", Mentions::mr);
    KeyMentions.tally(by, "MMM-2", Mentions::mr);
    KeyMentions.tally(by, "BBB-1", Mentions::branch);
    KeyMentions.tally(by, "BBB-2 CCC-1", Mentions::branch);
    KeyMentions.tally(by, "CCC-2", Mentions::commit);

    assertThat(new KeyMentions(by, 4, 2, 2).ranked())
        .extracting(Map.Entry::getKey)
        .containsExactly("ZZZ", "MMM", "AAA", "CCC", "BBB");
    Mentions zzz = Objects.requireNonNull(by.get("ZZZ"));
    assertThat(zzz.commits()).isEqualTo(2);
    assertThat(zzz.mrs()).isEqualTo(1);
    assertThat(zzz.weight()).isEqualTo(3);
    assertThat(zzz.tickets()).containsExactly("ZZZ-1", "ZZZ-2", "ZZZ-3");
    assertThat(Objects.requireNonNull(by.get("BBB")).branches()).isEqualTo(2);
    assertThat(Objects.requireNonNull(by.get("BBB")).weight()).isZero();
  }

  @Test
  void a_merge_request_text_is_its_title_branch_and_description() {
    assertThat(
            KeyMentions.mrText(
                GocdIndexTest.json(
                    "{\"title\":\"t\",\"source_branch\":\"b\",\"description\":\"d\"}")))
        .isEqualTo("t b d");
  }
}
