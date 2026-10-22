package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Une page de {@code /go/api/pipelines/:nom/history}, du run le plus récent au plus ancien.
 *
 * @param runs les runs de la page, « pipelines » chez GoCD
 */
public record HistoryPage(
    @JsonProperty("pipelines") List<Run> runs, @JsonProperty("_links") Links links) {

  public HistoryPage(List<Run> runs, @Nullable Links links) {
    this.runs = List.copyOf(runs);
    this.links = links == null ? new Links(null) : links;
  }

  public record Links(@Nullable Link next) {}

  public record Link(String href) {}

  public static HistoryPage of(JsonNode json) {
    return GocdJson.read(json, HistoryPage.class);
  }

  /** Le chemin de la page suivante ; null sur la dernière. */
  public @Nullable String next() {
    Link next = links.next();
    return next == null || next.href().isEmpty() || runs.isEmpty() ? null : next.href();
  }
}
