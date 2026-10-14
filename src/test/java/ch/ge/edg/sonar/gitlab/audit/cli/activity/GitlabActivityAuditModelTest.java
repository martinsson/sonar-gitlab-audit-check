package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitlabActivityAuditModelTest {

  @TempDir Path tmp;
  private Locale saved;

  @BeforeEach
  void fixLocale() {
    saved = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
  }

  @AfterEach
  void restoreLocale() {
    Locale.setDefault(saved);
  }

  private static Proj proj(String json) throws Exception {
    return Proj.of(Gitlab.MAPPER.readTree(json));
  }

  @Test
  void projReadsEveryField() throws Exception {
    Proj p =
        proj(
            "{\"id\":7,\"path_with_namespace\":\"team-a/alpha\",\"name\":\"Alpha\","
                + "\"namespace\":{\"full_path\":\"team-a\"},\"default_branch\":\"main\","
                + "\"visibility\":\"internal\",\"created_at\":\"2020-01-01T00:00:00Z\","
                + "\"last_activity_at\":\"2099-01-01T00:00:00Z\",\"archived\":true,"
                + "\"empty_repo\":true,\"mirror\":true,"
                + "\"forked_from_project\":{\"path_with_namespace\":\"up/alpha\"},"
                + "\"statistics\":{\"commit_count\":12,\"repository_size\":3400}}");
    assertThat(p.id).isEqualTo(7);
    assertThat(p.path).isEqualTo("team-a/alpha");
    assertThat(p.name).isEqualTo("Alpha");
    assertThat(p.namespace).isEqualTo("team-a");
    assertThat(p.defaultBranch).isEqualTo("main");
    assertThat(p.visibility).isEqualTo("internal");
    assertThat(p.created).isEqualTo(OffsetDateTime.parse("2020-01-01T00:00:00Z"));
    assertThat(p.lastActivity).isEqualTo(OffsetDateTime.parse("2099-01-01T00:00:00Z"));
    assertThat(p.archived).isTrue();
    assertThat(p.emptyRepo).isTrue();
    assertThat(p.mirror).isTrue();
    assertThat(p.forkedFrom).isEqualTo("up/alpha");
    assertThat(p.totalCommits).isEqualTo(12L);
    assertThat(p.repoSize).isEqualTo(3400L);
  }

  @Test
  void projToleratesMissingFields() throws Exception {
    Proj p = proj("{\"forked_from_project\":null,\"statistics\":{\"commit_count\":null}}");
    assertThat(p.id).isZero();
    assertThat(p.path).isEmpty();
    assertThat(p.namespace).isEmpty();
    assertThat(p.defaultBranch).isEmpty();
    assertThat(p.created).isNull();
    assertThat(p.archived).isFalse();
    assertThat(p.forkedFrom).isNull();
    assertThat(p.totalCommits).isNull();
    assertThat(p.repoSize).isNull();
    assertThat(proj("{\"forked_from_project\":{}}").forkedFrom).isNull();
  }

  @Test
  void perWeekNeedsCommitsAndAWindow() throws Exception {
    Proj p = proj("{}");
    assertThat(p.perWeek(7)).isZero();
    p.commits = 14;
    assertThat(p.perWeek(7)).isEqualTo(14.0);
    assertThat(p.perWeek(28)).isEqualTo(3.5);
    assertThat(p.perWeek(0)).isZero();
    assertThat(p.perWeek(-1)).isZero();
  }

  @Test
  void anEmptyProjRowKeepsUnknownsBlank() throws Exception {
    assertThat(String.join("|", proj("{}").row(90)))
        .isEqualTo("0||||||||false|false|false||||||||||||false||false|false|");
  }

  @Test
  void anEmptyPratRowKeepsUnknownsBlank() throws Exception {
    assertThat(String.join("|", proj("{}").pratRow(30)))
        .isEqualTo("|||||false|false|0||0||||0|0|||||||false|false|false||||0|30|||false||");
  }

  @Test
  void pomGivesGroupAndArtifactOfTheProjectItself() {
    Pom p =
        Pom.parse(
            "<?xml version=\"1.0\"?><project><modelVersion>4.0.0</modelVersion>"
                + "<groupId> ch.example </groupId><artifactId>alpha</artifactId>"
                + "<name>Alpha</name><build><plugins><plugin><groupId>x</groupId>"
                + "</plugin></plugins></build></project>");
    assertThat(p).isEqualTo(new Pom("ch.example:alpha", "ch.example:alpha", "Alpha"));
  }

  @Test
  void pomInheritsTheGroupFromItsParent() {
    assertThat(
            Pom.parse(
                "<project><parent><groupId>ch.parent</groupId><artifactId>p</artifactId>"
                    + "</parent><artifactId>child</artifactId></project>"))
        .isEqualTo(new Pom("ch.parent:child", "ch.parent:child", null));
  }

  @Test
  void pomWithPrefixedElementsIsRead() {
    assertThat(
            Pom.parse(
                "<m:project xmlns:m=\"urn:x\"><m:groupId>g</m:groupId>"
                    + "<m:artifactId>a</m:artifactId></m:project>"))
        .isEqualTo(new Pom("g:a", "g:a", null));
  }

  @Test
  void aPomPlaceholderIsNotAKey() {
    assertThat(
            Pom.parse(
                "<project><groupId>${org}</groupId><artifactId>a</artifactId>"
                    + "<name></name></project>"))
        .isEqualTo(new Pom(null, "${org}:a", null));
  }

  @Test
  void pomWithoutEnoughIsNothing() {
    assertThat(Pom.parse("<project><artifactId>a</artifactId></project>")).isNull();
    assertThat(Pom.parse("<project><parent/><artifactId>a</artifactId></project>")).isNull();
    assertThat(Pom.parse("<project><groupId>g</groupId><artifactId> </artifactId></project>"))
        .isNull();
    assertThat(Pom.parse("<settings><groupId>g</groupId><artifactId>a</artifactId></settings>"))
        .isNull();
    assertThat(Pom.parse("not xml")).isNull();
  }

  @Test
  void pomRefusesADoctype() {
    assertThat(
            Pom.parse(
                "<!DOCTYPE project [<!ENTITY g \"evil\">]><project><groupId>&g;</groupId>"
                    + "<artifactId>a</artifactId></project>"))
        .isNull();
  }

  @Test
  void ciFactsPreferTheUnresolvedKeyAsSource() {
    CiFacts f = new CiFacts();
    f.ciSonar = true;
    f.ciSecurity = true;
    f.sonarKey = "k";
    f.keySource = "ci/lint";
    f.pomKey = "g:a";
    f.pomName = "A";
    f.pomUnresolved = "${x}:a";
    Prat r = new Prat();
    f.applyTo(r);
    assertThat(r.ciSonar).isTrue();
    assertThat(r.ciSecurity).isTrue();
    assertThat(r.sonarKey).isEqualTo("k");
    assertThat(r.sonarKeySource).isEqualTo("ci/lint");
    assertThat(r.pomKey).isEqualTo("g:a");
    assertThat(r.pomName).isEqualTo("A");
    assertThat(r.pomUnresolved).isEqualTo("${x}:a");
    f.unresolvedKey = "$K";
    f.applyTo(r);
    assertThat(r.sonarKeySource).isEqualTo("variable non résolue : $K");
  }

  @Test
  void cacheKeyIsTheHeadOrTheLastActivity() throws Exception {
    SonarCache c = new SonarCache(tmp.resolve("c.json"), true, System.err);
    Proj p = proj("{\"id\":3,\"last_activity_at\":\"2099-01-01T00:00:00Z\"}");
    assertThat(c.keyFor(p)).isEqualTo("3@act:2099-01-01T00:00Z");
    p.headSha = " ";
    assertThat(c.keyFor(p)).isEqualTo("3@act:2099-01-01T00:00Z");
    p.headSha = "abc";
    assertThat(c.keyFor(p)).isEqualTo("3@abc");
    assertThat(c.keyFor(proj("{\"id\":4}"))).isEqualTo("4@act:");
  }

  @Test
  void aDisabledCacheKeepsNothing() {
    for (SonarCache c :
        new SonarCache[] {
          new SonarCache(tmp.resolve("c.json"), false, System.err),
          new SonarCache(null, true, System.err)
        }) {
      c.put("k", new CiFacts());
      assertThat(c.get("k")).isNull();
      assertThat(c.size()).isZero();
      c.load();
      c.save();
    }
    assertThat(tmp.resolve("c.json")).doesNotExist();
  }

  private static CiFacts everyFact() {
    CiFacts f = new CiFacts();
    f.ciSonar = true;
    f.sonarKey = "k";
    f.keySource = "ci/lint";
    f.ciRoute = "ci/lint";
    f.unresolvedKey = "$U";
    f.pomKey = "g:a";
    f.pomName = "A";
    f.pomUnresolved = "${x}";
    return f;
  }

  @Test
  void anEmptyCacheWritesNoFile() {
    Path file = tmp.resolve("sub/c.json");
    new SonarCache(file, true, System.err).save();
    assertThat(file).doesNotExist();
  }

  @Test
  void theCacheRoundTripsThroughItsFile() {
    Path file = tmp.resolve("sub/c.json");
    SonarCache c = new SonarCache(file, true, System.err);
    CiFacts f = everyFact();
    c.put("1@abc", f);
    assertThat(c.get("1@abc")).isSameAs(f);
    assertThat(c.size()).isEqualTo(1);
    c.save();

    SonarCache back = new SonarCache(file, true, System.err);
    back.load();
    assertThat(back.loadedCount()).isEqualTo(1);
    CiFacts r = Objects.requireNonNull(back.get("1@abc"));
    assertThat(r.ciSonar).isTrue();
    assertThat(r.ciSecurity).isFalse();
    assertThat(r.sonarKey).isEqualTo("k");
    assertThat(r.keySource).isEqualTo("ci/lint");
    assertThat(r.ciRoute).isEqualTo("ci/lint");
    assertThat(r.unresolvedKey).isEqualTo("$U");
    assertThat(r.pomKey).isEqualTo("g:a");
    assertThat(r.pomName).isEqualTo("A");
    assertThat(r.pomUnresolved).isEqualTo("${x}");
  }

  @Test
  void aMissingWrongVersionOrBrokenCacheLoadsNothing() throws Exception {
    SonarCache missing = new SonarCache(tmp.resolve("none.json"), true, System.err);
    missing.load();
    assertThat(missing.loadedCount()).isZero();
    Path v1 = Files.writeString(tmp.resolve("v1.json"), "{\"version\":1,\"entries\":{\"a\":{}}}");
    SonarCache old = new SonarCache(v1, true, System.err);
    old.load();
    assertThat(old.size()).isZero();
    Path broken = Files.writeString(tmp.resolve("b.json"), "{\"version\":2,\"entries\":{\"a\":{}");
    SonarCache bad = new SonarCache(broken, true, System.err);
    bad.put("kept?", new CiFacts());
    bad.load();
    assertThat(bad.size()).isZero();
    assertThat(bad.loadedCount()).isZero();
  }

  @Test
  void loadedEntriesDefaultToFalseAndNull() throws Exception {
    Path f = Files.writeString(tmp.resolve("c.json"), "{\"version\":2,\"entries\":{\"a\":{}}}");
    SonarCache c = new SonarCache(f, true, System.err);
    c.load();
    CiFacts e = Objects.requireNonNull(c.get("a"));
    assertThat(e.ciSonar).isFalse();
    assertThat(e.sonarKey).isNull();
    assertThat(e.pomUnresolved).isNull();
    assertThat(c.loadedCount()).isEqualTo(1);
  }
}
