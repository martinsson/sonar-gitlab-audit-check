package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Étape 1 : filtre de fraîcheur, et sa validation. */
final class RecencyGate {

  private final Gitlab gl;
  private final Funnel funnel;

  RecencyGate(Gitlab gl, Funnel funnel) {
    this.gl = gl;
    this.funnel = funnel;
  }

  /**
   * last_activity_at est inutilisable pour *classer* — un commentaire de ticket le déplace — mais
   * il est exactement bon pour *exclure*, parce qu'il se trompe dans le sens sûr : il est gonflé
   * par les événements non-commit, donc tout projet ayant commité dans la fenêtre a forcément un
   * last_activity_at récent. Le filtre sur-inclut ; il ne sous-inclut pas.
   */
  List<AuditedProject> run(List<AuditedProject> all) {
    Fate stale =
        new Fate.Excluded(Fate.Stage.RECENCY, Text.format("inactif > %dj", funnel.sinceDays()));
    return all.stream().map(p -> p.fate().inPlay() && isStale(p) ? p.with(stale) : p).toList();
  }

  private boolean isStale(AuditedProject p) {
    OffsetDateTime last = p.project().lastActivity();
    return last == null || last.isBefore(funnel.windowStart());
  }

  /**
   * La propriété « le filtre ne sous-inclut pas » a une classe de fuite connue : réécritures
   * d'historique, imports et transferts peuvent désynchroniser last_activity_at des dates de
   * commit. On l'échantillonne au lieu de la croire — zéro faux négatif rend la porte gratuite, un
   * chiffre non nul remplace une hypothèse par une mesure. Les projets tirés portent leur compte.
   */
  List<AuditedProject> validate(List<AuditedProject> all) {
    int wanted = funnel.options().validationSample;
    if (wanted <= 0) return all;
    List<AuditedProject> below =
        all.stream()
            .filter(p -> p.fate().excludedAt(Fate.Stage.RECENCY))
            .collect(Collectors.toCollection(ArrayList::new));
    if (below.isEmpty()) return all;

    Collections.shuffle(below, new Random(funnel.options().seed));
    List<AuditedProject> sample = below.subList(0, Math.min(wanted, below.size()));
    CommitPager pager = new CommitPager(gl, funnel);
    List<Integer> counts = gl.map(sample, funnel.concurrency(), p -> pager.count(p.project()));
    IdentityHashMap<AuditedProject, Integer> counted = new IdentityHashMap<>();
    for (int i = 0; i < sample.size(); i++) counted.put(sample.get(i), counts.get(i));
    return all.stream()
        .map(
            p -> {
              Integer n = counted.get(p);
              return n == null ? p : p.with(new CommitMeasure.Sampled(n));
            })
        .toList();
  }
}
