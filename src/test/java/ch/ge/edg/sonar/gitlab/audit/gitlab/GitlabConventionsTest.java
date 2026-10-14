package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GitlabConventionsTest {

  private static final Pattern BOTS = Pattern.compile(Gitlab.BOT_PATTERN);

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
    assertThat(Gitlab.isBot(BOTS, email, name)).isTrue();
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
    assertThat(Gitlab.isBot(BOTS, email, name)).isFalse();
  }

  @Test
  void isBotToleratesNulls() {
    assertThat(Gitlab.isBot(BOTS, null, null)).isFalse();
    assertThat(Gitlab.isBot(BOTS, null, "renovate")).isTrue();
    assertThat(Gitlab.isBot(BOTS, "renovate@x", null)).isTrue();
  }

  @Test
  void isBotJoinsEmailAndNameWithASpace() {
    Pattern spaceJoin = Pattern.compile("a b");
    assertThat(Gitlab.isBot(spaceJoin, "a", "b")).isTrue();
    assertThat(Gitlab.isBot(Pattern.compile("^ b$"), null, "b")).isTrue();
    assertThat(Gitlab.isBot(Pattern.compile("^a $"), "a", null)).isTrue();
  }

  @Test
  void identityUsesTheLocalPartOfTheEmail() {
    assertThat(Gitlab.identity("Alice.Smith@example.org", "Whoever")).isEqualTo("alicesmith");
    assertThat(Gitlab.identity("dev0@example.org", null)).isEqualTo("dev0");
  }

  @Test
  void identityFallsBackToTheNameInTheSameNamespace() {
    assertThat(Gitlab.identity(null, "Dev 0")).isEqualTo("dev0");
    assertThat(Gitlab.identity("", "Dev 0")).isEqualTo("dev0");
    assertThat(Gitlab.identity("no-at-sign", "Bob B")).isEqualTo("bobb");
    assertThat(Gitlab.identity("  ", "Bob")).isEqualTo("bob");
  }

  @Test
  void identityIsUnknownWhenNothingUsableRemains() {
    assertThat(Gitlab.identity(null, null)).isEqualTo("?");
    assertThat(Gitlab.identity(null, "  ")).isEqualTo("?");
    assertThat(Gitlab.identity("@example.org", "Alice")).isEqualTo("?");
    assertThat(Gitlab.identity("éé@example.org", "Alice")).isEqualTo("?");
    assertThat(Gitlab.identity(null, "---")).isEqualTo("?");
  }

  @Test
  void identityKeepsTheFirstAtSign() {
    assertThat(Gitlab.identity("a@b@c", "x")).isEqualTo("a");
  }

  @Test
  void aMergeHasMoreThanOneParent() throws Exception {
    assertThat(Gitlab.isMerge(json("{\"parent_ids\":[\"a\",\"b\"]}"))).isTrue();
    assertThat(Gitlab.isMerge(json("{\"parent_ids\":[\"a\"]}"))).isFalse();
    assertThat(Gitlab.isMerge(json("{\"parent_ids\":[]}"))).isFalse();
    assertThat(Gitlab.isMerge(json("{}"))).isFalse();
    assertThat(Gitlab.isMerge((JsonNode) null)).isFalse();
  }

  @Test
  void aMergeFromAListOfParents() {
    assertThat(Gitlab.isMerge(Arrays.asList("a", "b", "c"))).isTrue();
    assertThat(Gitlab.isMerge(List.of("a", "b"))).isTrue();
    assertThat(Gitlab.isMerge(List.of("a"))).isFalse();
    assertThat(Gitlab.isMerge((List<String>) null)).isFalse();
  }

  @Test
  void parseKeepsTheOffset() {
    OffsetDateTime t = Gitlab.parse("2026-08-18T09:12:33.000+02:00");
    assertThat(t).isNotNull();
    assertThat(t.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
    assertThat(t.getHour()).isEqualTo(9);
    assertThat(Gitlab.parse("2026-08-18T09:12:33Z").getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void parseGivesNullOnBlankOrGarbage() {
    assertThat(Gitlab.parse(null)).isNull();
    assertThat(Gitlab.parse(" ")).isNull();
    assertThat(Gitlab.parse("2026-08-18")).isNull();
    assertThat(Gitlab.parse("yesterday")).isNull();
  }

  @Test
  void encIsFormUrlEncoding() {
    assertThat(Gitlab.enc("team-a/alpha service")).isEqualTo("team-a%2Falpha+service");
    assertThat(Gitlab.enc("é")).isEqualTo("%C3%A9");
  }

  @Test
  void mapperIgnoresUnknownProperties() throws Exception {
    record One(int a) {}
    assertThat(Gitlab.MAPPER.readValue("{\"a\":1,\"b\":2}", One.class)).isEqualTo(new One(1));
  }

  private static JsonNode json(String s) throws Exception {
    return Gitlab.MAPPER.readTree(s);
  }
}
