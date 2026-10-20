package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.FUTURE;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.commit;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.GitlabAuditFake.project;
import static com.diffplug.selfie.Selfie.expectSelfie;
import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitlabActivityAuditSonarKeyTest extends GitlabActivityAuditTestBase {

  private final List<ObjectNode> projects = new ArrayList<>();

  /** A selected project: one commit, and these names at the root of its tree. */
  private void add(long id, String... root) {
    projects.add(project(id, "team-a/k" + id));
    fake.commits(id, commit("sha" + id, "dev" + id, FUTURE)).tree(id, root);
  }

  private void lint(long id, String yaml) {
    fake.json(
        "/api/v4/projects/" + id + "/ci/lint",
        Gitlab.MAPPER.createObjectNode().put("merged_yaml", yaml).toString());
  }

  private Cli.Result deep(String... extra) {
    fake.enterprise(false).group("team-a", projects.toArray(ObjectNode[]::new));
    List<String> args =
        new ArrayList<>(
            List.of(
                "--group",
                "team-a",
                "--deep",
                "--out-dir",
                tmp.toString(),
                "--graphql=false",
                "--floor",
                "0",
                "--concurrency",
                "1",
                "--validation-sample",
                "0"));
    args.addAll(List.of(extra));
    return fake.run(args.toArray(String[]::new));
  }

  private void everyKeySource() {
    add(1, "sonar-project.properties", ".gitlab-ci.yml");
    fake.raw(1, "sonar-project.properties", "sonar.host.url=x\n  sonar.projectKey = team-a_k1  \n");
    lint(1, "sonar:\n  script: scan -Dsonar.projectKey=ignored\nsecret-detection: {}\n");
    add(2, "sonar-project.properties", ".gitlab-ci.yml");
    fake.raw(2, "sonar-project.properties", "# no key here\n");
    lint(2, "build:\n  script: mvn -Dsonar.projectKey=${CI_PROJECT_PATH_SLUG} verify\n");
    add(3, ".gitlab-ci.yml");
    fake.json(
        "/api/v4/projects/3/ci/lint",
        "{\"merged_yaml\":\"\",\"content\":{\"merged_yaml\":"
            + "\"variables:\\n  SONAR_PROJECT_KEY: \\\"literal-key\\\"\\ndependency-scanning: {}\\n\"}}");
    add(4, ".gitlab-ci.yml");
    fake.json("/api/v4/projects/4/ci/lint", "{\"valid\":false}");
    fake.raw(
        4,
        ".gitlab-ci.yml",
        """
        include:
          - component: gitlab.example/sonar@1
            inputs:
              project_key: $CI_PROJECT_NAME
        """);
    add(5, ".gitlab-ci.yml");
    lint(5, "a: -Dsonar.projectKey=$CUSTOM_KEY\nsonar.projectKey: %OTHER%\n");
    add(6, ".gitlab-ci.yml");
    lint(6, "a: -Dsonar.projectKey=$SONAR_PROJECT_KEY\nv:\n  SONAR_PROJECT_KEY: real-key\n");
    add(7, ".gitlab-ci.yml");
    lint(
        7,
        "a: -Dsonar.projectKey=${CI_PROJECT_NAMESPACE}.$CI_PROJECT_TITLE.${CI_PROJECT_ID}"
            + ".$CI_PROJECT_PATH\n");
  }

  private void everyPom() {
    add(8, "pom.xml");
    fake.raw(
        8,
        "pom.xml",
        "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"><parent><groupId>ch.example"
            + "</groupId></parent><artifactId>k8-app</artifactId><name> K8 App </name>"
            + "<dependencies><dependency><groupId>x</groupId></dependency></dependencies>"
            + "</project>");
    add(9, "pom.xml", ".gitlab-ci.yml");
    fake.raw(
        9, "pom.xml", "<project><groupId>${org}</groupId><artifactId>k9</artifactId></project>");
    lint(9, "x: -Dsonar.projectKey=k9key\n");
    add(10, "pom.xml");
    fake.raw(10, "pom.xml", "<project><groupId>broken");
    add(11, "pom.xml", ".gitlab-ci.yml");
    fake.raw(11, "pom.xml", "<settings><groupId>a</groupId><artifactId>b</artifactId></settings>");
    fake.route("/api/v4/projects/11/ci/lint", r -> Response.status(500, "{}"));
    add(12, "pom.xml");
    fake.raw(12, "pom.xml", "<project><groupId>g</groupId><artifactId>a12</artifactId></project>");
    lint(12, "unused");
  }

  @Test
  void everyWayToFindTheSonarKey() {
    everyKeySource();
    everyPom();
    Cli.Result r = deep();
    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    expectSelfie(file(tmp.resolve(".sonar-keys.json"))).toMatchDisk("cache");
    Request lint = fake.server.requests("/api/v4/projects/1/ci/lint").getFirst();
    assertThat(lint.target())
        .isEqualTo("/api/v4/projects/1/ci/lint?ref=main&content_ref=main&include_merged_yaml=true");
    assertThat(fake.server.requests("/api/v4/projects/11/repository/files/.gitlab-ci.yml/raw"))
        .hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/12/ci/lint")).isEmpty();
  }

  @Test
  void aRefusedLintFallsBackToFollowingIncludes() {
    add(1, ".gitlab-ci.yml");
    add(2, ".gitlab-ci.yml");
    fake.route("/api/v4/projects/1/ci/lint", r -> Response.status(403, "{}"));
    fake.raw(
        1,
        ".gitlab-ci.yml",
        """
        include:
          - project: 'shared/ci-templates'
            ref: v1
            file:
              - '/templates/sonar.yml'
              - templates/build.yml
          - project: 42
            file: common.yml
          - project: team-a/k2
            file: "beta.yml"
          - project: shared/missing
            file: x.yml
          - local: foo.yml
        stages: [build]
        """);
    fake.raw(2, ".gitlab-ci.yml", "sonar.projectKey: k2-key\n");
    templates();

    Cli.Result r = deep();

    assertOk(r);
    expectSelfie(out(r)).toMatchDisk("stdout");
    expectSelfie(file(tmp.resolve("pratiques.csv"))).toMatchDisk("pratiques");
    assertThat(fake.server.requests()).filteredOn(q -> q.path().endsWith("/ci/lint")).hasSize(1);
    assertThat(fake.server.requests())
        .filteredOn(q -> q.path().contains("/repository/files/"))
        .extracting(Request::target)
        .containsExactly(
            "/api/v4/projects/1/repository/files/.gitlab-ci.yml/raw?ref=main",
            "/api/v4/projects/77/repository/files/%2Ftemplates%2Fsonar.yml/raw?ref=v1",
            "/api/v4/projects/77/repository/files/nested.yml/raw?ref=HEAD",
            "/api/v4/projects/77/repository/files/level3.yml/raw?ref=HEAD",
            "/api/v4/projects/77/repository/files/templates%2Fbuild.yml/raw?ref=v1",
            "/api/v4/projects/42/repository/files/common.yml/raw?ref=HEAD",
            "/api/v4/projects/2/repository/files/beta.yml/raw?ref=HEAD",
            "/api/v4/projects/2/repository/files/.gitlab-ci.yml/raw?ref=main");
    assertThat(fake.server.requests("/api/v4/projects/shared%2Fci-templates")).hasSize(1);
    assertThat(fake.server.requests("/api/v4/projects/shared%2Fmissing")).hasSize(1);
  }

  private void templates() {
    fake.json("/api/v4/projects/shared%2Fci-templates", "{\"id\":77}");
    fake.route(
        "/api/v4/projects/77/repository/files/%2Ftemplates%2Fsonar.yml/raw",
        r ->
            Response.status(
                200,
                """
                sonar:
                  script: scan -Dsonar.projectKey=${CI_PROJECT_NAMESPACE}_${CI_PROJECT_NAME}
                include:
                  - project: shared/ci-templates
                    file: nested.yml
                """));
    fake.raw(
        77,
        "nested.yml",
        """
        include:
          - project: shared/ci-templates
            ref: v1
            file: '/templates/sonar.yml'
          - project: shared/ci-templates
            file: level3.yml
        sast: {}
        """);
    fake.raw(
        77, "level3.yml", "include:\n  - project: shared/ci-templates\n    file: level4.yml\n");
    fake.raw(77, "level4.yml", "never: read\n");
    fake.raw(42, "common.yml", "renovate: {}\n");
    fake.raw(2, "beta.yml", "beta: {}\n");
  }

  @Test
  void aSecondRunReadsTheKeysFromTheCache() {
    add(1, ".gitlab-ci.yml", "pom.xml");
    lint(1, "x: -Dsonar.projectKey=k1key\n");
    fake.raw(1, "pom.xml", "<project><groupId>g</groupId><artifactId>a</artifactId></project>");
    deep();
    String cache = file(tmp.resolve(".sonar-keys.json"));
    String firstPratiques = file(tmp.resolve("pratiques.csv"));

    Cli.Result again = fake.run(argsAgain());

    assertOk(again);
    assertThat(again.out())
        .contains("\n  CI lue : 0 par ci/lint, 0 par repli, 1 depuis le cache.\n");
    assertThat(fake.server.requests("/api/v4/projects/1/ci/lint")).hasSize(1);
    assertThat(file(tmp.resolve("pratiques.csv"))).isEqualTo(firstPratiques);
    assertThat(file(tmp.resolve(".sonar-keys.json"))).isEqualTo(cache);
    expectSelfie(cache).toMatchDisk();
  }

  private String[] argsAgain() {
    return new String[] {
      "--group", "team-a", "--deep", "--out-dir", tmp.toString(), "--graphql=false", "--floor", "0"
    };
  }

  @Test
  void theCacheIsKeyedOnTheGraphqlHeadOrOnTheLastActivity() {
    projects.add(project(1, "team-a/k1"));
    projects.add(project(2, "team-a/k2"));
    ObjectNode anonymous = commit("x", "dev", FUTURE);
    anonymous.remove("id");
    fake.commits(1, commit("rest-sha", "dev", FUTURE)).commits(2, anonymous);
    fake.tree(1, ".gitlab-ci.yml").tree(2, ".gitlab-ci.yml");
    lint(1, "x: 1\n");
    lint(2, "y: 2\n");
    fake.json(
        "/api/graphql",
        "{\"data\":{\"p0\":{\"repository\":{\"tree\":{\"lastCommit\":{\"sha\":\"gql-sha\","
            + "\"committedDate\":\"2099-01-01T10:00:00Z\"}}}},\"p1\":null}}");
    fake.enterprise(false).group("team-a", projects.toArray(ObjectNode[]::new));
    fake.run("--group", "team-a", "--deep", "--out-dir", tmp.toString(), "--floor", "0");
    assertThat(file(tmp.resolve(".sonar-keys.json")))
        .contains("\"1@gql-sha\"")
        .contains("\"2@act:2099-01-01T10:00Z\"");
  }

  @Test
  void noCacheNeitherReadsNorWrites() {
    add(1, ".gitlab-ci.yml");
    lint(1, "x: 1\n");
    write(".sonar-keys.json", "{\"version\":2,\"entries\":{\"1@sha1\":{\"sonarKey\":\"stale\"}}}");
    Cli.Result r = deep("--no-cache");
    assertOk(r);
    assertThat(file(tmp.resolve(".sonar-keys.json"))).contains("stale");
    assertThat(file(tmp.resolve("pratiques.csv"))).doesNotContain("stale");
    assertThat(r.out()).contains("CI lue : 1 par ci/lint, 0 par repli, 0 depuis le cache.");
  }

  @Test
  void anExplicitCacheFileIsUsed() {
    add(1, ".gitlab-ci.yml");
    lint(1, "x: 1\n");
    write(
        "elsewhere/keys.json",
        "{\"version\":2,\"entries\":{\"1@sha1\":{\"sonarKey\":\"cached\","
            + "\"keySource\":\"ci/lint\",\"ciSonar\":true}}}");
    Cli.Result r = deep("--cache", tmp.resolve("elsewhere/keys.json").toString());
    assertOk(r);
    assertThat(r.out()).contains("CI lue : 0 par ci/lint, 0 par repli, 1 depuis le cache.");
    assertThat(file(tmp.resolve("pratiques.csv")))
        .contains("\"true\";\"false\";\"cached\";\"ci/lint\"");
    assertThat(tmp.resolve(".sonar-keys.json")).doesNotExist();
  }

  @Test
  void anOldOrBrokenCacheIsIgnored() {
    add(1, ".gitlab-ci.yml");
    lint(1, "x: -Dsonar.projectKey=fresh\n");
    write("v1.json", "{\"version\":1,\"entries\":{\"1@sha1\":{\"sonarKey\":\"stale\"}}}");
    write("broken.json", "{not json");
    for (String name : new String[] {"v1.json", "broken.json"}) {
      Cli.Result r = deep("--cache", tmp.resolve(name).toString());
      assertOk(r);
      assertThat(file(tmp.resolve("pratiques.csv"))).contains("\"fresh\"").doesNotContain("stale");
      assertThat(file(tmp.resolve(name))).contains("\"version\" : 2");
    }
  }

  @Test
  void aCacheThatCannotBeWrittenIsReported() {
    add(1, ".gitlab-ci.yml");
    lint(1, "x: 1\n");
    write("cache-dir/placeholder", "");
    Cli.Result r = deep("--cache", tmp.resolve("cache-dir").toString());
    assertOk(r);
    assertThat(r.err()).startsWith("  (cache non écrit : ").endsWith(")\n");
  }
}
