package ch.ge.edg.sonar.gitlab.audit.contributors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContributorsTest {

  private static final Pattern BOTS = Pattern.compile(Contributors.BOT_PATTERN);

  @ParameterizedTest
  @CsvSource({
    "renovate@example.org, Someone",
    "x@example.org, Dependabot",
    "x@example.org, semantic-release-bot",
    "x@example.org, ci[bot]",
    "bot@example.org, Alice",
    "gitlab-ci-token@example.org, Alice",
    "x@example.org, Jenkins",
    "x@example.org, SonarQube",
    "x@example.org, Automation",
    "x@example.org, bot",
    "x@example.org, Bots",
    "x@example.org, the bot user",
    "x@example.org, release-bot",
    "project_42_bot@example.org, Alice",
    "x@example.org, BOT",
  })
  void botsAreRecognised(String email, String name) {
    assertThat(Contributors.isBot(BOTS, email, name)).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "alice@example.org, Alice",
    "x@example.org, Abbot",
    "x@example.org, Talbot",
    "x@example.org, robotics",
    "x@example.org, botanist",
    "x@example.org, project_x_botte",
    "x@example.org, Éabot",
  })
  void humansAreNotBots(String email, String name) {
    assertThat(Contributors.isBot(BOTS, email, name)).isFalse();
  }

  @Test
  void isBotToleratesNulls() {
    assertThat(Contributors.isBot(BOTS, null, null)).isFalse();
    assertThat(Contributors.isBot(BOTS, null, "renovate")).isTrue();
    assertThat(Contributors.isBot(BOTS, "renovate@x", null)).isTrue();
  }

  @Test
  void isBotJoinsEmailAndNameWithASpace() {
    Pattern spaceJoin = Pattern.compile("a b");
    assertThat(Contributors.isBot(spaceJoin, "a", "b")).isTrue();
    assertThat(Contributors.isBot(Pattern.compile("^ b$"), null, "b")).isTrue();
    assertThat(Contributors.isBot(Pattern.compile("^a $"), "a", null)).isTrue();
  }

  @Test
  void identityUsesTheLocalPartOfTheEmail() {
    assertThat(Contributors.identity("Alice.Smith@example.org", "Whoever")).isEqualTo("alicesmith");
    assertThat(Contributors.identity("dev0@example.org", null)).isEqualTo("dev0");
  }

  @Test
  void identityFallsBackToTheNameInTheSameNamespace() {
    assertThat(Contributors.identity(null, "Dev 0")).isEqualTo("dev0");
    assertThat(Contributors.identity("", "Dev 0")).isEqualTo("dev0");
    assertThat(Contributors.identity("no-at-sign", "Bob B")).isEqualTo("bobb");
    assertThat(Contributors.identity("  ", "Bob")).isEqualTo("bob");
  }

  @Test
  void identityIsUnknownWhenNothingUsableRemains() {
    assertThat(Contributors.identity(null, null)).isEqualTo("?");
    assertThat(Contributors.identity(null, "  ")).isEqualTo("?");
    assertThat(Contributors.identity("@example.org", "Alice")).isEqualTo("?");
    assertThat(Contributors.identity("éé@example.org", "Alice")).isEqualTo("?");
    assertThat(Contributors.identity(null, "---")).isEqualTo("?");
  }

  @Test
  void identityKeepsTheFirstAtSign() {
    assertThat(Contributors.identity("a@b@c", "x")).isEqualTo("a");
  }

  @Test
  void aMergeHasMoreThanOneParent() {
    assertThat(Contributors.isMerge(List.of("a", "b"))).isTrue();
    assertThat(Contributors.isMerge(Arrays.asList("a", "b", "c"))).isTrue();
    assertThat(Contributors.isMerge(List.of("a"))).isFalse();
    assertThat(Contributors.isMerge(List.of())).isFalse();
    assertThat(Contributors.isMerge((List<String>) null)).isFalse();
  }
}
