package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.runtime.Sleeper;
import ch.ge.edg.sonar.gitlab.audit.sonar.Captures;
import ch.ge.edg.sonar.gitlab.audit.sonar.Sonar;
import ch.ge.edg.sonar.gitlab.audit.sonar.SonarConnection;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Ce que chaque section du diagnostic partage : l'instance, la console, et le nombre d'appels en
 * vol à la fois.
 */
record Session(Sonar sq, Console out, int concurrency, Clock clock) {

  /** L'instance décrite par les options, ou son rejeu. */
  static Session open(SonarAudit.Options options, Console out, Clock clock, Sleeper sleeper)
      throws IOException, GeneralSecurityException {
    InstanceOptions instance = options.instance();
    OutputOptions output = options.output();
    ScanOptions scan = options.scan();
    Sonar sq =
        new Sonar(
            new SonarConnection(
                instance.url, instance.token, instance.organization, scan.timeout, scan.insecure),
            new Captures(output.dumpDir, output.replayDir, out.err()),
            sleeper);
    return new Session(sq, out, scan.concurrency, clock);
  }

  /** Le fuseau de la course, celui de l'horloge. */
  ZoneId zone() {
    return clock.getZone();
  }

  /** Un GET lu comme {@code type} ; null si l'appel échoue ou si la réponse est illisible. */
  <T> @Nullable T read(String path, Map<String, String> params, Class<T> type) {
    return sq.get(path, params).as(type);
  }

  /** Une passe par projet, recouverte, avec son compteur. */
  <T, R> List<R> eachProject(String label, List<T> projects, Function<T, R> work) {
    Progress bar = new Progress(out, label, projects.size());
    List<R> read =
        sq.map(
            projects,
            concurrency,
            p -> {
              R r = work.apply(p);
              bar.tick();
              return r;
            });
    bar.done();
    return read;
  }
}
