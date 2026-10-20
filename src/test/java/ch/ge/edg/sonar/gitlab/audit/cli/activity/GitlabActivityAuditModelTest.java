package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitlabActivityAuditModelTest {

  @TempDir Path tmp;

  private static AuditedProject audited(String json) throws Exception {
    return AuditedProject.entering(GitlabProject.of(Gitlab.MAPPER.readTree(json)));
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
    f.keySource = "ci/lint";
    assertThat(f.shownKeySource()).isEqualTo("ci/lint");
    f.unresolvedKey = "$K";
    assertThat(f.shownKeySource()).isEqualTo("variable non résolue : $K");
  }

  @Test
  void cacheKeyIsTheHeadOrTheLastActivity() throws Exception {
    SonarCache c = new SonarCache(tmp.resolve("c.json"), true, System.err);
    AuditedProject p = audited("{\"id\":3,\"last_activity_at\":\"2099-01-01T00:00:00Z\"}");
    assertThat(c.keyFor(p)).isEqualTo("3@act:2099-01-01T00:00Z");
    assertThat(c.keyFor(p.with(new CommitMeasure.Head(null, " "))))
        .isEqualTo("3@act:2099-01-01T00:00Z");
    assertThat(c.keyFor(p.with(new CommitMeasure.Head(null, "abc")))).isEqualTo("3@abc");
    assertThat(c.keyFor(audited("{\"id\":4}"))).isEqualTo("4@act:");
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
