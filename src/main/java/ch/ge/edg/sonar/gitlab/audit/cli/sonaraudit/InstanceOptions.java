package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** L'instance interrogée, et le projet témoin des tests d'endpoints. */
final class InstanceOptions {

  @Option(names = "--url", defaultValue = "${env:SONAR_URL}", description = "URL de l'instance")
  @Nullable String url;

  @Option(
      names = "--token",
      defaultValue = "${env:SONAR_TOKEN}",
      description = "User token squ_...")
  @Nullable String token;

  @Option(
      names = "--organization",
      defaultValue = "${env:SONAR_ORG}",
      description = "SonarQube Cloud")
  @Nullable String organization;

  @Option(names = "--project", description = "projet témoin (sinon : le premier visible)")
  @Nullable String project;
}
