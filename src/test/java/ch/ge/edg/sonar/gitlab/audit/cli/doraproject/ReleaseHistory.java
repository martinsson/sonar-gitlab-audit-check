package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The release history of issue #51, an anonymised real project: CI tags every release on a {@code
 * ci(release):} commit, several times a day, and follows it with a {@code set next SNAPSHOT
 * version} commit.
 *
 * <pre>
 * 62ad7fb (HEAD -> main) ci(release): set next SNAPSHOT version 20261117.1.2-SNAPSHOT
 * 0f54f65 (tag: v20261117.1.1) ci(release): set release version 20261117.1.1
 * 65574b3 ci(release): set next SNAPSHOT version 20261116.1.3-SNAPSHOT
 * 39f2138 (tag: v20261116.1.2) ci(release): set release version 20261116.1.2   [lightweight]
 * 4a6ef8c Merge branch 'feature/ABC-483_xxx' into 'main'        parents a390797, 3a9ec64
 * a390797 chore: delete implemented/outdated ai scratches/specs  straight on main
 * 3a9ec64 (origin/feature/ABC-483_xxx) feat: [ABC-483] fix keys
 * a36c9cc feat: [ABC-483] dedup keys
 * 4c2215e (tag: v20261113.1.2) ci(release): set release version 20261113.1.2
 * 366100a feat: [ABC-483] sonar                                   fast-forwarded onto main
 * 21c3b99 Merge branch 'refs/heads/main' into feature/ABC-483_xxx parents 1a1a1a1, c9384fb
 * c9384fb Merge branch 'bugfix/ABC-585_xxx' into 'main'           parents 35b142d, b702d84
 * b702d84 (origin/bugfix/ABC-585_xxx) fix: [ABC-585] use none as initial ...
 * 35b142d (tag: v20261113.1.1) ci(release): set release version 20261113.1.1
 * 321b295 Merge branch 'feature/ABC-343_xxx' into 'main'          parents p000000, fefda95
 * ab33886 fix: income touched and openapi refresh ...             no key, on the branch
 * fefda95 (origin/feature/ABC-343_xxx) test: [ABC-343] ux: new baseline images ...
 * </pre>
 *
 * <p>Not in the issue's log, added so that every commit has its parents: {@code p000000}, the
 * release {@code v20261110.1.1} before the period, and {@code 1a1a1a1}, ABC-483's first commit on
 * its branch. Dates are invented, in the log's order.
 */
final class ReleaseHistory {

  static final Instant NOW = at("11-17T12:00");

  static final String BASE = "p000000";

  private ReleaseHistory() {}

  /** A time in November 2026, UTC: « 11-13T08:12 ». */
  static Instant at(String monthDayTime) {
    return Instant.parse("2026-" + monthDayTime + ":00Z");
  }

  static Delivery.Commit commit(
      String sha, String title, String authored, @Nullable String committed, String... parents) {
    return new Delivery.Commit(
        sha, List.of(parents), title, at(authored), at(committed == null ? authored : committed));
  }

  /** v20261113.1.1: ABC-343, whose keyless fix arrives through its branch's merge. */
  static List<Delivery.Commit> first() {
    return List.of(
        commit("ab33886", "fix: income touched and openapi refresh", "11-12T09:00", null, BASE),
        commit(
            "fefda95",
            "test: [ABC-343] ux: new baseline images",
            "11-12T15:00",
            "11-13T07:30",
            "ab33886"),
        commit(
            "321b295",
            "Merge branch 'feature/ABC-343_xxx' into 'main'",
            "11-13T08:00",
            null,
            BASE,
            "fefda95"),
        commit(
            "35b142d",
            "ci(release): set release version 20261113.1.1",
            "11-13T08:10",
            null,
            "321b295"));
  }

  /** v20261113.1.2: ABC-585 by its merge, ABC-483 fast-forwarded after merging main in. */
  static List<Delivery.Commit> second() {
    return List.of(
        commit("b702d84", "fix: [ABC-585] use none as initial", "11-13T09:00", null, "35b142d"),
        commit(
            "c9384fb",
            "Merge branch 'bugfix/ABC-585_xxx' into 'main'",
            "11-13T10:00",
            null,
            "35b142d",
            "b702d84"),
        commit("1a1a1a1", "feat: [ABC-483] first draft", "11-10T14:00", null, BASE),
        commit(
            "21c3b99",
            "Merge branch 'refs/heads/main' into feature/ABC-483_xxx",
            "11-13T11:00",
            null,
            "1a1a1a1",
            "c9384fb"),
        commit("366100a", "feat: [ABC-483] sonar", "11-13T11:30", null, "21c3b99"),
        commit(
            "4c2215e",
            "ci(release): set release version 20261113.1.2",
            "11-13T12:00",
            null,
            "366100a"));
  }

  /** v20261116.1.2: ABC-483's last piece by its merge, and a chore straight on main. */
  static List<Delivery.Commit> third() {
    return List.of(
        commit("a36c9cc", "feat: [ABC-483] dedup keys", "11-14T10:00", null, "366100a"),
        commit("3a9ec64", "feat: [ABC-483] fix keys", "11-14T16:00", "11-15T08:00", "a36c9cc"),
        commit(
            "a390797",
            "chore: delete implemented/outdated ai scratches/specs",
            "11-15T09:00",
            null,
            "4c2215e"),
        commit(
            "4a6ef8c",
            "Merge branch 'feature/ABC-483_xxx' into 'main'",
            "11-16T08:00",
            null,
            "a390797",
            "3a9ec64"),
        commit(
            "39f2138",
            "ci(release): set release version 20261116.1.2",
            "11-16T08:05",
            null,
            "4a6ef8c"));
  }

  /** v20261117.1.1: only the CI's own commits. */
  static List<Delivery.Commit> fourth() {
    return List.of(
        commit(
            "65574b3",
            "ci(release): set next SNAPSHOT version 20261116.1.3-SNAPSHOT",
            "11-16T08:06",
            null,
            "39f2138"),
        commit(
            "0f54f65",
            "ci(release): set release version 20261117.1.1",
            "11-17T08:00",
            null,
            "65574b3"));
  }

  /** The tag before the period, then the period's four, oldest first; the third is lightweight. */
  static List<Tag> tags() {
    return List.of(
        new Tag("v20261110.1.1", BASE, at("11-10T08:01"), true),
        new Tag("v20261113.1.1", "35b142d", at("11-13T08:12"), true),
        new Tag("v20261113.1.2", "4c2215e", at("11-13T12:02"), true),
        new Tag("v20261116.1.2", "39f2138", at("11-16T08:05"), false),
        new Tag("v20261117.1.1", "0f54f65", at("11-17T08:01"), true));
  }

  /**
   * A tag as GitLab lists it.
   *
   * @param at the tagger's date of an annotated tag; a lightweight one's commit is committed then
   */
  record Tag(String name, String sha, Instant at, boolean annotated) {

    Deploy deploy() {
      return new Deploy(
          name, Deploy.PRODUCTION, at, sha, "octocat/Hello-World", Deploy.Kind.RELEASE_TAG);
    }
  }

  /** The period's four tags, each with what it brings since the previous one. */
  static List<Delivery> deliveries() {
    List<Tag> tags = tags();
    List<List<Delivery.Commit>> commits = List.of(first(), second(), third(), fourth());
    List<Delivery> out = new ArrayList<>();
    for (int i = 1; i < tags.size(); i++) {
      out.add(
          new Delivery(tags.get(i).deploy(), tags.get(i - 1).deploy(), commits.get(i - 1), false));
    }
    return out;
  }

  /** Every commit of main, newest first as GitLab lists them, down to the base. */
  static List<Delivery.Commit> main() {
    List<Delivery.Commit> out = new ArrayList<>();
    out.add(commit(BASE, "chore: base", "11-10T08:00", null));
    out.addAll(first());
    out.addAll(second());
    out.addAll(third());
    out.addAll(fourth());
    out.add(
        commit(
            "62ad7fb",
            "ci(release): set next SNAPSHOT version 20261117.1.2-SNAPSHOT",
            "11-17T08:02",
            null,
            "0f54f65"));
    return out.reversed();
  }
}
