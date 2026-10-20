package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Request;
import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer.Response;
import ch.ge.edg.sonar.gitlab.audit.testing.JiraFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JiraExtractTest {

  private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
  private static final String BOM = String.valueOf((char) 0xFEFF);

  @TempDir Path dir;

  private Cli.Result run(FakeHttpServer jira, String... extra) {
    List<String> args =
        new ArrayList<>(
            List.of(
                "--url",
                jira.url() + "/",
                "--token",
                " tok ",
                "--out-dir",
                dir.resolve("out").toString()));
    args.addAll(List.of(extra));
    return Cli.run(new JiraExtract(ZURICH), args.toArray(String[]::new));
  }

  private String file(String name) throws IOException {
    return Files.readString(dir.resolve("out").resolve(name), StandardCharsets.UTF_8);
  }

  /** Both projects, the default query over 30 days, two fields asked for and one that is not. */
  private Cli.Result extractAll(FakeHttpServer jira) {
    Cli.Result r =
        run(
            jira,
            "--project",
            "ABC,XYZ",
            "--days",
            "30",
            "--field",
            "sprint,customfield_10400,nope");
    assertThat(r.exitCode()).isZero();
    return r;
  }

  @Test
  void the_console_tells_each_step_and_stdout_stays_empty() {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      Cli.Result r = extractAll(jira);
      Path out = dir.resolve("out");
      assertThat(r.err().lines())
          .containsExactly(
              "Champ inconnu, ignoré : nope",
              "Champs en plus : Environnement, Sévérité, Sprint, Story Points",
              "JQL : project in (\"ABC\",\"XYZ\") AND updated >= -30d ORDER BY key ASC",
              "  2 / 3 tickets",
              "  3 / 3 tickets",
              out.resolve("jira-tickets.csv") + " : 3 tickets, 8 changements de statut",
              "versions de XYZ : introuvable (ou masqué faute de droit)",
              out.resolve("jira-versions.csv") + " : 2 versions",
              "7 requêtes GET.");
      assertThat(r.out()).isEmpty();
    }
  }

  @Test
  void one_ticket_row_per_issue_with_dates_delays_and_the_extra_fields() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      extractAll(jira);
      assertThat(file("jira-tickets.csv"))
          .isEqualTo(
              BOM
                  + "cle;projet;type;sous_tache;statut;categorie_statut;priorite;resolution;"
                  + "cree_le;en_cours_le;termine_le;resolu_le;mis_a_jour_le;delai_resolution_j;"
                  + "delai_cycle_j;nb_changements_statut;composants;versions_corrigees;"
                  + "versions_affectees;labels;parent;epic;Environnement [customfield_10200];"
                  + "Sévérité [customfield_10201];Sprint [customfield_10300];"
                  + "Story Points [customfield_10400]\r\n"
                  + "ABC-1;ABC;Bug;N;Closed;done;High;Fixed;2026-03-02 09:00:00;"
                  + "2026-03-03 10:00:00;2026-03-07 12:00:00;2026-03-07 12:00:00;"
                  + "2026-03-07 12:30:00;5,1;4,1;5;api|web;1.2.0;;prod|hotfix;;ABC-100;"
                  + "Production / Site A;Majeure;Sprint 7;3.0\r\n"
                  + "ABC-2;ABC;Sub-task;O;Archived;done;Medium;;2026-03-09 09:00:00;"
                  + "2026-03-10 09:00:00;2026-03-11 15:00:00;;2026-03-12 09:00:00;;1,3;3;;;"
                  + "1.2.0;;ABC-1;;;;;\r\n"
                  + "ABC-3;ABC;Task;N;Open;new;;;2026-03-20 16:45:00;;;;2026-03-20 16:45:00;;;"
                  + "0;;;;;;;;\"Mineure; à \"\"revoir\"\" plus tard\";;\r\n");
    }
  }

  @Test
  void every_status_change_with_the_target_category() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      extractAll(jira);
      assertThat(file("jira-transitions.csv"))
          .isEqualTo(
              BOM
                  + "cle;le;de;vers;categorie_vers\r\n"
                  + "ABC-1;2026-03-03 10:00:00;Open;In Progress;indeterminate\r\n"
                  + "ABC-1;2026-03-04 10:15:30;In Progress;Closed;done\r\n"
                  + "ABC-1;2026-03-05 08:00:00;Closed;Reopened;new\r\n"
                  + "ABC-1;2026-03-06 09:00:00;Reopened;In Progress;indeterminate\r\n"
                  + "ABC-1;2026-03-07 12:00:00;In Progress;Closed;done\r\n"
                  + "ABC-2;2026-03-10 09:00:00;Open;In Progress;indeterminate\r\n"
                  + "ABC-2;2026-03-11 15:00:00;In Progress;Closed;done\r\n"
                  + "ABC-2;2026-03-12 09:00:00;Closed;Archived;\r\n");
    }
  }

  @Test
  void the_versions_and_the_raw_issues_as_jira_returned_them() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      extractAll(jira);
      assertThat(file("jira-versions.csv"))
          .isEqualTo(
              BOM
                  + "projet;version;livree;archivee;debut_le;livree_le;description\r\n"
                  + "ABC;1.2.0;O;N;2026-02-01;2026-03-10;\"Livraison de mars; correctifs\"\r\n"
                  + "ABC;1.3.0;N;O;;;\r\n");
      List<String> raw = file("jira-tickets.jsonl").lines().toList();
      assertThat(raw).hasSize(3);
      assertThat(JiraFixtures.json(raw.get(1)))
          .isEqualTo(JiraFixtures.json(JiraFixtures.read("issue-ABC-2.json")));
      assertThat(JiraFixtures.json(raw.get(0)).path("key").asText()).isEqualTo("ABC-1");
    }
  }

  @Test
  void asks_jira_for_every_field_and_the_changelog_with_the_pat_as_bearer() {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      run(jira, "--project", "ABC");
      List<Request> searches = jira.requests("/rest/api/2/search");
      assertThat(searches).hasSize(2);
      Request first = searches.get(0);
      assertThat(first.param("jql"))
          .isEqualTo("project in (\"ABC\") AND updated >= -365d ORDER BY key ASC");
      assertThat(first.param("startAt")).isEqualTo("0");
      assertThat(first.param("maxResults")).isEqualTo("100");
      assertThat(first.param("fields")).isEqualTo("*all");
      assertThat(first.param("expand")).isEqualTo("changelog");
      assertThat(searches.get(1).param("startAt")).isEqualTo("2");
      Request issue = jira.requests("/rest/api/2/issue/ABC-2").get(0);
      assertThat(issue.param("expand")).isEqualTo("changelog");
      assertThat(issue.param("fields")).isEqualTo("*all");
      assertThat(jira.requests())
          .allSatisfy(
              q -> {
                assertThat(q.method()).isEqualTo("GET");
                assertThat(q.header("Authorization")).isEqualTo("Bearer tok");
                assertThat(q.header("Accept")).isEqualTo("application/json");
              });
    }
  }

  @Test
  void comma_writes_for_a_tool_and_no_raw_skips_the_jsonl() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      Cli.Result r = run(jira, "--project", "ABC", "--comma", "--no-raw");
      assertThat(r.exitCode()).isZero();
      assertThat(file("jira-transitions.csv"))
          .startsWith("cle,le,de,vers,categorie_vers\nABC-1,2026-03-03 10:00:00,Open,");
      assertThat(file("jira-versions.csv"))
          .contains("ABC,1.2.0,O,N,2026-02-01,2026-03-10,Livraison de mars; correctifs\n");
      assertThat(dir.resolve("out/jira-tickets.jsonl")).doesNotExist();
      assertThat(dir.resolve("out/jira-tickets.csv")).exists();
    }
  }

  @Test
  void dates_follow_the_given_zone() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      List<String> args =
          List.of(
              "--url",
              jira.url(),
              "--token",
              "t",
              "--project",
              "ABC",
              "--out-dir",
              dir.resolve("out").toString());
      Cli.Result r = Cli.run(new JiraExtract(ZoneId.of("UTC")), args.toArray(String[]::new));
      assertThat(r.exitCode()).isZero();
      assertThat(file("jira-transitions.csv"))
          .contains("ABC-1;2026-03-04 09:15:30;In Progress;Closed;done\r\n");
    }
  }

  @Test
  void jql_replaces_the_query_and_versions_use_the_upper_case_key() throws IOException {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      Cli.Result r = run(jira, "--project", "abc", "--jql", "key = ABC-3");
      assertThat(r.exitCode()).isZero();
      assertThat(r.err()).contains("JQL : key = ABC-3");
      assertThat(jira.requests("/rest/api/2/search").get(0).param("jql")).isEqualTo("key = ABC-3");
      assertThat(file("jira-versions.csv")).contains("abc;1.2.0;O;N;");
    }
  }

  @Test
  void search_falls_back_to_latest_when_v2_is_gone() throws IOException {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/search", q -> Response.status(410, "{}"));
      jira.on("/rest/api/latest/search", q -> Response.json(JiraFixtures.read("search-2.json")));
      JiraFixtures.serve(jira);
      Cli.Result r = run(jira, "--project", "ABC", "--no-raw");
      assertThat(r.exitCode()).isZero();
      assertThat(file("jira-tickets.csv")).contains("\r\nABC-3;ABC;Task;");
    }
  }

  @Test
  void an_empty_page_ends_the_extraction_whatever_the_total_says() {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.json("/rest/api/2/field", "[]");
      jira.json("/rest/api/2/status", "[]");
      jira.json("/rest/api/2/search", "{\"total\":50,\"issues\":[]}");
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isZero();
      assertThat(jira.requests("/rest/api/2/search")).hasSize(1);
      assertThat(r.err()).contains("jira-tickets.csv : 0 tickets, 0 changements de statut");
      assertThat(r.err()).doesNotContain("Champs en plus");
      assertThat(r.err()).contains("versions de ABC : introuvable");
    }
  }

  @Test
  void a_refused_token_stops_with_exit_2_wherever_it_happens() {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/field", q -> Response.status(401, "{}"));
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).isEqualTo("Arrêt : " + jira.url() + " : jeton refusé (401)\n");
    }
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/project/ABC/versions", q -> Response.status(401, "{}"));
      JiraFixtures.serve(jira);
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).endsWith("Arrêt : " + jira.url() + " : jeton refusé (401)\n");
    }
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/search", q -> Response.status(401, "{}"));
      JiraFixtures.serve(jira);
      assertThat(run(jira, "--project", "ABC").err()).contains("jeton refusé (401)");
    }
  }

  @Test
  void a_failed_search_stops_and_shows_what_jira_said() {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on(
          "/rest/api/2/search",
          q -> Response.status(400, "{\"errorMessages\":\n  [\"La valeur 'XYZ' n'existe pas\"]}"));
      JiraFixtures.serve(jira);
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err())
          .endsWith(
              "Arrêt : search : HTTP 400 {\"errorMessages\": [\"La valeur 'XYZ' n'existe pas\"]}\n");
      assertThat(jira.requests("/rest/api/latest/search")).isEmpty();
    }
  }

  @Test
  void fields_statuses_and_a_full_issue_are_required() {
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/field", q -> Response.status(500, "boom"));
      JiraFixtures.serve(jira);
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).isEqualTo("Arrêt : /rest/api/2/field : HTTP 500\n");
    }
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/status", q -> Response.status(403, "{}"));
      JiraFixtures.serve(jira);
      assertThat(run(jira, "--project", "ABC").err())
          .isEqualTo("Arrêt : /rest/api/2/status : authentifié mais pas autorisé\n");
    }
    try (FakeHttpServer jira = new FakeHttpServer()) {
      jira.on("/rest/api/2/issue/ABC-2", q -> Response.status(404, "{}"));
      JiraFixtures.serve(jira);
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err())
          .endsWith("Arrêt : /rest/api/2/issue/ABC-2 : introuvable (ou masqué faute de droit)\n");
    }
  }

  @Test
  void a_missing_url_or_token_stops_before_any_call() {
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      Cli.Result noUrl =
          Cli.run(new JiraExtract(ZURICH), "--url", " ", "--token", "t", "--project", "ABC");
      assertThat(noUrl.exitCode()).isEqualTo(2);
      assertThat(noUrl.err()).isEqualTo("Arrêt : JIRA_URL manquant\n");
      Cli.Result noToken =
          Cli.run(new JiraExtract(ZURICH), "--url", jira.url(), "--token=", "--project", "ABC");
      assertThat(noToken.err()).isEqualTo("Arrêt : JIRA_TOKEN manquant\n");
      assertThat(jira.requests()).isEmpty();
    }
  }

  @Test
  void an_output_directory_that_cannot_be_created_stops_with_exit_2() throws IOException {
    Files.writeString(dir.resolve("out"), "a file, not a directory");
    try (FakeHttpServer jira = JiraFixtures.jira()) {
      Cli.Result r = run(jira, "--project", "ABC");
      assertThat(r.exitCode()).isEqualTo(2);
      assertThat(r.err()).contains("Arrêt : ").contains("out");
    }
  }

  @Test
  void the_project_is_required() {
    Cli.Result r = Cli.run(new JiraExtract(), "--url", "http://x", "--token", "t");
    assertThat(r.exitCode()).isEqualTo(2);
    assertThat(r.err()).contains("--project");
  }
}
