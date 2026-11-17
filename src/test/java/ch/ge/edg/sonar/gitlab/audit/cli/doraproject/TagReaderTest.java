package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.ReleaseHistory.at;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Which tags are deploys: the release tags of the period on main, after the last one before. */
class TagReaderTest {

  private static final Pattern RELEASE = Pattern.compile(DoraProject.DEFAULT_TAG_PATTERN);

  private static final List<TagReader.TagRef> TAGS =
      List.of(
          new TagReader.TagRef("v20261113.1.2", "b", at("11-13T12:02")),
          new TagReader.TagRef("v20261110.1.1", "p", at("11-10T08:00")),
          new TagReader.TagRef("v20261109.1.1", "off", at("11-09T08:00")),
          new TagReader.TagRef("nightly-20261113", "n", at("11-13T10:00")),
          new TagReader.TagRef("v20261114.9.9", "elsewhere", at("11-14T10:00")),
          new TagReader.TagRef("v20261118.1.1", "later", at("11-18T10:00")),
          new TagReader.TagRef("v20261113.1.1", "a", at("11-13T08:12")));

  private static List<String> names(List<TagReader.TagRef> tags) {
    return tags.stream().map(TagReader.TagRef::name).toList();
  }

  @Test
  void theReleaseTagsOfThePeriodOnMainComeAfterTheLastOneBeforeIt() {
    List<TagReader.TagRef> picked =
        TagReader.releases(
            TAGS, RELEASE, at("11-11T12:00"), ReleaseHistory.NOW, Set.of("a", "b", "n", "p"));
    assertThat(names(picked)).containsExactly("v20261110.1.1", "v20261113.1.1", "v20261113.1.2");
  }

  @Test
  void aTagBeforeThePeriodIsCheckedOnMainOnlyWithinTheMargin() {
    // Within the margin, a tag off main is no bound: neither p nor the older « off » is taken.
    assertThat(
            names(
                TagReader.releases(
                    TAGS, RELEASE, at("11-11T12:00"), ReleaseHistory.NOW, Set.of("a", "b"))))
        .containsExactly("v20261113.1.1", "v20261113.1.2");
    assertThat(
            names(
                TagReader.releases(
                    TAGS, RELEASE, at("12-20T12:00"), at("12-21T12:00"), Set.of("a", "b"))))
        // Past the margin, the last tag before the period is the bound, unchecked.
        .containsExactly("v20261118.1.1");
  }
}
