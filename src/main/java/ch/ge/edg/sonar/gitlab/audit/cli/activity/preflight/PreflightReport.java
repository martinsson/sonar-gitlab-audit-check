package ch.ge.edg.sonar.gitlab.audit.cli.activity.preflight;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.report.Screen.RED;

import ch.ge.edg.sonar.gitlab.audit.cli.activity.report.Screen;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.util.Optional;

/**
 * Le préambule à l'écran : les sondes, puis la conduite à tenir quand l'instance est inutilisable.
 */
public final class PreflightReport {

  private final Screen screen;

  public PreflightReport(Screen screen) {
    this.screen = screen;
  }

  /** Constate, imprime, et rend l'instance quand elle est utilisable. */
  public Optional<Preflight.Instance> run(Gitlab gitlab) {
    Preflight.Result result = new Preflight(gitlab).check();
    print(result);
    return result.instance();
  }

  private void print(Preflight.Result result) {
    screen.title("0. Connectivité, identité, licence");
    for (Preflight.Probe probe : result.probes()) {
      screen.line(probe.endpoint(), probe.verdict(), probe.detail());
    }
    String lead = "\n";
    for (String line : result.advice()) {
      screen.say(lead + "  " + line, RED);
      lead = "";
    }
  }
}
