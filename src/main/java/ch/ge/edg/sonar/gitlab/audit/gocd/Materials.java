package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ce que l'historique d'un run dit de ses matériaux. Sans droit admin, c'est la seule source : la
 * configuration des pipelines est réservée, la description du matériau ne l'est pas.
 */
public final class Materials {

  private static final Pattern URL_FIELD = Pattern.compile("URL:\\s*(\\S+?)(?:,|\\s|$)");
  private static final Pattern ANY_URL =
      Pattern.compile("(https?://\\S+?|[\\w.-]+@[\\w.-]+:\\S+?)(?:,|\\s|$)");

  private Materials() {}

  public static boolean isGit(JsonNode material) {
    return "git".equals(type(material));
  }

  public static boolean isPipeline(JsonNode material) {
    return "pipeline".equals(type(material));
  }

  private static String type(JsonNode material) {
    return material.path("type").asText("").toLowerCase(Locale.ROOT);
  }

  /** L'URL d'un matériau git, d'après sa description « URL: https://…, Branch: main ». */
  public static String gitUrl(JsonNode material) {
    String description = material.path("description").asText("");
    Matcher m = URL_FIELD.matcher(description);
    if (m.find()) return m.group(1);
    m = ANY_URL.matcher(description);
    return m.find() ? m.group(1) : "";
  }

  /** Le pipeline amont d'un matériau pipeline, d'après sa description « amont [ stage ] ». */
  public static String upstreamName(JsonNode material) {
    String description = material.path("description").asText("");
    int bracket = description.indexOf(" [");
    return bracket > 0
        ? description.substring(0, bracket).trim()
        : material.path("name").asText("");
  }
}
