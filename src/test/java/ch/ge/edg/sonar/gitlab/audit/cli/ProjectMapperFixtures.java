package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Synthetic CSV inputs for ProjectMapper and helpers to run it and read what it wrote. */
final class ProjectMapperFixtures {

  private ProjectMapperFixtures() {}

  /** Left side of the Sonar × GitLab preset: what GitlabActivityAudit --deep writes. */
  static final String PRESET_LEFT =
      """
      path,cle_sonar,cle_pom,id,nom_pom
      team-a/alpha-service,team-a:alpha,,1,
      team-a/beta,,org.example:beta,2,
      team-b/gamma,,,42,
      team-c/101-delta,,,4,
      team-e/12-,,,5,
      team-f/zp-web,,,6,Zeta Portal
      team-f/common-ui,,,7,Common
      team-g/inventory-tracker,,,8,
      team-h/lonely-thing,,,9,
      app1,,,10,
      team-j/report-engine,,,11,
      team-k/report-engine,,,12,
      team-l/gamma-tools-extra-stuff,,,13,
      team-m/epsilon-api,org.example:eps-key,,14,
      """;

  /** Right side of the preset: the Sonar inventory. */
  static final String PRESET_RIGHT =
      """
      key,name,alm,alm_repository,liens
      team-a:alpha,Alpha Service,,,gitlab https://git.example.test/team-a/alpha-service.git
      org.example:beta,Beta,,,
      org.example:gamma,Gamma,gitlab,42,
      org.example:gamma-gh,Gamma GH,github,42,
      grp.delta:delta,Delta,,,
      TEAM-E_12,Team E,,,
      org.example:zportal,zeta-portal,,,
      org.example:common-a,common,,,
      org.example:common-b,common,,,
      org.example:invntory-tracker,Inventory Tracker,,,
      org.example:app2,App2,,,
      org.example:reporting-engine-svc,Reporting Engine,,,gitlab https://git.example.test/team-j/report-engine
      org.example:eps-key,Unrelated Thing,,,
      org.example:epsilon-api,Epsilon Api,,,
      org.example:beta-legacy,Beta legacy,,,gitlab https://git.example.test/team-a/beta \
      | gitlab https://git.example.test/team-h/lonely-thing
      ,No key,,,
      """;

  static Path write(Path dir, String name, String content) throws IOException {
    return Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
  }

  /** Runs the command with {@code --color never} prepended. */
  static Cli.Result run(String... args) {
    List<String> all = new ArrayList<>(List.of("--color", "never"));
    all.addAll(List.of(args));
    return Cli.run(new ProjectMapper(), all.toArray(String[]::new));
  }

  /** Output with the temporary directory replaced, so that it can be compared across runs. */
  static String stable(String text, Path dir) {
    String real;
    try {
      real = dir.toRealPath().toString();
    } catch (IOException e) {
      real = dir.toString();
    }
    return text.replace(dir.toAbsolutePath().toString(), "<tmp>")
        .replace(real, "<tmp>")
        .replace(System.lineSeparator(), "\n");
  }

  /** An Excel-format CSV (BOM, semicolons, CRLF) shown as plain lines, the BOM made visible. */
  static String excelCsv(Path file) throws IOException {
    String s = Files.readString(file, StandardCharsets.UTF_8);
    return s.replace("﻿", "<BOM>").replace("\r\n", "⏎\n");
  }
}
