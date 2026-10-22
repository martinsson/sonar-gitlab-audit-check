package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Un run de pipeline, tel que {@code /go/api/pipelines/:nom/:compteur} et l'historique le
 * décrivent.
 */
public record Run(
    int counter,
    String label,
    @Nullable Instant scheduledDate,
    BuildCause buildCause,
    List<Stage> stages) {

  public Run(
      int counter,
      String label,
      @Nullable Instant scheduledDate,
      @Nullable BuildCause buildCause,
      List<Stage> stages) {
    this.counter = counter;
    this.label = label;
    this.scheduledDate = scheduledDate;
    this.buildCause = buildCause == null ? new BuildCause(false, List.of()) : buildCause;
    this.stages = List.copyOf(stages);
  }

  /** Ce qui a lancé le run, et les révisions de chaque matériau qu'il a pris. */
  public record BuildCause(boolean triggerForced, List<MaterialRevision> materialRevisions) {}

  /**
   * Un matériau du run et ses révisions.
   *
   * @param changed vrai si le matériau a changé depuis le run précédent
   */
  public record MaterialRevision(
      boolean changed, Material material, List<Modification> modifications) {

    public MaterialRevision(
        boolean changed, @Nullable Material material, List<Modification> modifications) {
      this.changed = changed;
      this.material = material == null ? new Material("", "", "") : material;
      this.modifications = List.copyOf(modifications);
    }
  }

  /**
   * Un commit pour un matériau git, un run amont « build/12/stage/1 » pour un matériau pipeline.
   */
  public record Modification(String revision, @Nullable Instant modifiedTime, String comment) {}

  public static Run of(JsonNode json) {
    return GocdJson.read(json, Run.class);
  }
}
