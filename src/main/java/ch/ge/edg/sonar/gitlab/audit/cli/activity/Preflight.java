package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.rest.RestResponse;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Préambule : connectivité, identité, licence. Il constate ; {@link PreflightReport} imprime. */
final class Preflight {

  private final Gitlab gitlab;

  Preflight(Gitlab gitlab) {
    this.gitlab = gitlab;
  }

  /**
   * Ce que l'instance permet de mesurer.
   *
   * @param enterprise le plan Enterprise, sans lequel ni DORA ni les approbations ne se lisent
   */
  record Instance(boolean enterprise) {}

  /** Ce que dit une sonde du préambule. */
  record Probe(String endpoint, ProbeVerdict verdict, String detail) {}

  /**
   * Les sondes dans l'ordre, ce qu'il faut en conclure quand l'instance est inutilisable, et
   * l'instance quand elle est utilisable.
   *
   * @param advice vide quand l'instance est utilisable
   * @param instance vide quand l'instance est injoignable ou le jeton refusé : rien d'autre n'a de
   *     sens
   */
  record Result(List<Probe> probes, List<String> advice, Optional<Instance> instance) {}

  Result check() {
    List<Probe> probes = new ArrayList<>();
    RestResponse me = gitlab.get("user", Map.of());
    if (me.unreachable()) {
      probes.add(new Probe("api/v4/user", ProbeVerdict.ERROR, me.message()));
      return new Result(
          probes,
          List.of("Instance injoignable. Vérifie l'URL, le proxy, le TLS."),
          Optional.empty());
    }
    if (!me.is(200)) {
      probes.add(
          new Probe("api/v4/user", ProbeVerdict.DENIED, me.statusLine() + " — jeton refusé"));
      return new Result(
          probes,
          List.of(
              "Le jeton n'est pas accepté. La portée read_api suffit,",
              "mais un jeton expiré ou révoqué donne le même 401."),
          Optional.empty());
    }
    JsonNode user = me.tree();
    probes.add(
        new Probe(
            "api/v4/user",
            ProbeVerdict.OK,
            Text.format("%s (%s)", text(user, "username"), text(user, "name"))));
    boolean enterprise = enterprise(probes);
    return new Result(probes, List.of(), Optional.of(new Instance(enterprise)));
  }

  /**
   * Le plan conditionne la moitié des signaux de pratique : approbations, DORA, règles de push. Le
   * découvrir après coup, c'est réécrire l'analyse.
   */
  private boolean enterprise(List<Probe> probes) {
    RestResponse meta = gitlab.get("metadata", Map.of());
    if (meta.is(200)) {
      JsonNode metadata = meta.tree();
      boolean enterprise = metadata.path("enterprise").asBoolean(false);
      probes.add(
          new Probe(
              "api/v4/metadata",
              enterprise ? ProbeVerdict.OK : ProbeVerdict.EMPTY,
              Text.format(
                  "GitLab %s · %s",
                  text(metadata, "version"),
                  enterprise ? "Enterprise" : "Community — DORA et approbations indisponibles")));
      return enterprise;
    }
    probes.add(
        new Probe(
            "api/v4/metadata",
            ProbeVerdict.MISSING,
            meta.statusLine() + " — plan inconnu, on suppose Community"));
    return false;
  }
}
