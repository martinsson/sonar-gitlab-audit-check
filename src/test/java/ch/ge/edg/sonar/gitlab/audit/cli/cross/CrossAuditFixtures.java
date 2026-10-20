package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Keys;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Synthetic inputs for CrossAudit and SonarToGitlab: invented teams, projects and people. */
final class CrossAuditFixtures {

  static final String SONAR_HEADER =
      "key,name,analysisDate,ncloc,coverage,tests,sqale_index,new_violations,alm,alm_repository,"
          + "liens,tendance_analyses,violations_pente_mois";

  static final String GITLAB_HEADER =
      "path,name,id,bucket,commits_window,mr_fusionnees,ci_sonar,cle_sonar,cle_pom,nom_pom,"
          + "fenetre_j,lignes_modifiees,commits_lignes,lignes_tronquees";

  static final String INVENTORY_HEADER =
      "path,name,id,selectionne,commits_window,last_activity_at,fork,mirror,exclu";

  static {
    // The mapper's key helpers warn once per JVM, from their static initialiser, that
    // namespace-mapping.yaml
    // is absent from the working directory: trigger it here, outside any captured run, so that
    // what a run prints does not depend on test order.
    Keys.normalise("");
  }

  private CrossAuditFixtures() {}

  /** Writes a comma-separated file, one line per string, and returns its path. */
  static Path csv(Path file, String header, String... rows) throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add(header);
    lines.addAll(List.of(rows));
    Files.createDirectories(file.toAbsolutePath().getParent());
    Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
    return file;
  }

  static Csv.Table table(Path dir, String name, String header, String... rows) throws IOException {
    return Csv.read(csv(dir.resolve(name), header, rows));
  }

  /** The Sonar side of the main scenario. */
  static Path sonar(Path dir) throws IOException {
    return csv(
        dir.resolve("projets.csv"),
        SONAR_HEADER,
        "alpha-key,Alpha Service,2026-01-10,5000,10,0,300,5,,,,6,6",
        "org.example:beta-lib,Beta Lib,2025-03-01,2000,0,12,120,0,,,,2,",
        "delta-portal-app,Delta Portal App,2026-02-02,3000,55,40,10,0,,,,,",
        "zeta-sonar,Zeta Web,2026-02-03,1500,15,0,0,3,,,,7,2",
        "orphan-x,Orphan X,,,,,,,gitlab,101,,,",
        "team-e_lambda-svc,Lambda Svc,2026-01-05,800,,,,,,,,,",
        "mu-tool,Mu Tool,2026-01-06,700,,,,,,,,,",
        "nu-thing,Nu Thing,,,,,,,,,,,",
        "zz-other-key,(iota-three) Legacy,2026-01-07,600,,,,,,,,,");
  }

  /** The GitLab selection (pratiques.csv) of the main scenario. */
  static Path gitlab(Path dir) throws IOException {
    return csv(
        dir.resolve("pratiques.csv"),
        GITLAB_HEADER,
        "team-a/alpha-service,alpha-service,11,actif,30,0,true,alpha-key,,,90,1000,10,false",
        "team-a/beta-lib,beta-lib,12,dormant,0,2,false,,org.example:beta-lib,,90,0,0,false",
        "team-b/gamma-api,gamma-api,13,actif,50,4,true,gamma-wrong,,,90,800,20,false",
        "team-b/delta-portal,delta-portal,14,actif,25,1,false,,,,,,,",
        "team-c/epsilon-batch,epsilon-batch,15,,,,,,,,,,,",
        "team-c/zeta-web,zeta-web,16,actif,40,0,false,,,Zeta Web,90,600,12,true",
        "team-d/eta-one,eta-one,17,actif,21,0,true,,,,,,,",
        "team-d/theta-two,theta-two,18,actif,22,0,false,,,,,,,",
        "team-d/iota-three,iota-three,19,actif,23,0,false,,,,,,,",
        "team-d/kappa-four,kappa-four,20,actif,24,0,false,,,,,,,");
  }

  /** The whole GitLab inventory of the main scenario, written next to pratiques.csv. */
  static Path inventory(Path dir) throws IOException {
    return csv(
        dir.resolve("inventaire.csv"),
        INVENTORY_HEADER,
        "team-a/alpha-service,alpha-service,11,true,30,2026-02-01,false,false,",
        "team-a/beta-lib,beta-lib,12,true,0,2025-01-01,false,false,",
        "team-b/gamma-api,gamma-api,13,true,50,2026-02-01,false,false,",
        "team-b/delta-portal,delta-portal,14,true,25,2026-02-01,false,false,",
        "team-c/epsilon-batch,epsilon-batch,15,true,,2024-01-01,false,false,",
        "team-c/zeta-web,zeta-web,16,true,40,2026-02-01,false,false,",
        "team-d/eta-one,eta-one,17,true,21,2026-02-01,false,false,",
        "team-d/theta-two,theta-two,18,true,22,2026-02-01,false,false,",
        "team-d/iota-three,iota-three,19,true,23,2026-02-01,false,false,",
        "team-d/kappa-four,kappa-four,20,true,24,2026-02-01,false,false,",
        "team-z/orphan,orphan,101,false,12,2026-02-01,false,false,",
        "team-e/lambda-svc,lambda-svc,102,false,3,2026-01-01,false,false,",
        "team-f/mu-tool,mu-tool,103,false,15,2026-01-20,false,false,",
        "alice/mu-tool,mu-tool,104,false,0,2025-06-01,true,false,",
        "team-g/legacy,legacy,105,false,0,2020-01-01,false,false,oui");
  }

  /** Runs CrossAudit in-process, colors off unless asked, under a fixed locale. */
  static Cli.Result run(String... args) {
    Locale saved = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    try {
      return Cli.run(CrossAudit::new, args);
    } finally {
      Locale.setDefault(saved);
    }
  }

  /** The output with the temporary directory replaced, so that it can be snapshotted. */
  static String scrub(String text, Path dir) {
    return text.replace(dir.toAbsolutePath().toString(), "<tmp>");
  }

  static String read(Path file) throws IOException {
    return Files.readString(file, StandardCharsets.UTF_8);
  }
}
