package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

import java.util.List;
import java.util.function.Predicate;

/**
 * L'entonnoir tel qu'il a fini : chaque projet du périmètre avec ce qu'on en a appris. Les
 * effectifs publiés se comptent sur les projets, ils ne se tiennent pas à côté.
 *
 * @param projects tout le périmètre, exclus compris, dans l'ordre de l'inventaire
 * @param graphqlCalls les requêtes GraphQL envoyées pour dater les derniers commits
 */
public record FunnelResult(List<AuditedProject> projects, int graphqlCalls) {

  public int inScope() {
    return projects.size();
  }

  /** Retenus par l'inventaire. */
  public int kept() {
    return count(p -> !p.fate().excludedAt(Fate.Stage.INVENTORY));
  }

  /** Passés par le filtre de fraîcheur. */
  public int fresh() {
    return count(
        p ->
            !p.fate().excludedAt(Fate.Stage.INVENTORY) && !p.fate().excludedAt(Fate.Stage.RECENCY));
  }

  /** Au-dessus du plancher, tirés ou non. */
  public int eligible() {
    return count(p -> p.fate() instanceof Fate.Eligible || p.fate().isSelected());
  }

  public List<AuditedProject> selected() {
    return projects.stream().filter(p -> p.fate().isSelected()).toList();
  }

  /** Laissés de côté par --active-within, sans être mesurés. */
  public int notCounted() {
    return count(p -> p.fate().excludedAt(Fate.Stage.ACTIVITY_CUT));
  }

  /** Les projets sous le filtre de fraîcheur tirés pour le valider. */
  public int sampleSize() {
    return count(p -> p.commits() instanceof CommitMeasure.Sampled);
  }

  /** Ceux d'entre eux qui avaient des commits dans la fenêtre. */
  public int sampleLeaks() {
    return count(p -> p.commits().leaked());
  }

  /** Comptés jusqu'au plafond de pages : leur volume est un plancher. */
  public int truncated() {
    return count(p -> p.commits().truncated());
  }

  private int count(Predicate<AuditedProject> test) {
    return (int) projects.stream().filter(test).count();
  }
}
