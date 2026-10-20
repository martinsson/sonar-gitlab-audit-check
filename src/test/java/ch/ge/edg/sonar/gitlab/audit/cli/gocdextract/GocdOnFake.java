package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import ch.ge.edg.sonar.gitlab.audit.testing.FakeHttpServer;
import ch.ge.edg.sonar.gitlab.audit.testing.GocdFixtures;

/** The extraction's GoCD view, talking to a fake server. */
final class GocdOnFake {

  private GocdOnFake() {}

  static Gocd gocd(FakeHttpServer server) {
    return new Gocd(GocdFixtures.api(server));
  }
}
