package ch.ge.edg.sonar.gitlab.audit.gocd;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Un matériau, tel qu'un run le décrit. Sans droit admin, c'est la seule source : la configuration
 * des pipelines est réservée, la description du matériau ne l'est pas.
 *
 * @param type « Git », « Pipeline »...
 * @param description « URL: https://…, Branch: main » pour git, « amont [ stage ] » pour un
 *     pipeline
 */
public record Material(String type, String name, String description) {

  private static final Pattern URL_FIELD = Pattern.compile("URL:\\s*(\\S+?)(?:,|\\s|$)");
  private static final Pattern ANY_URL =
      Pattern.compile("(https?://\\S+?|[\\w.-]+@[\\w.-]+:\\S+?)(?:,|\\s|$)");

  public boolean isGit() {
    return "git".equals(type.toLowerCase(Locale.ROOT));
  }

  public boolean isPipeline() {
    return "pipeline".equals(type.toLowerCase(Locale.ROOT));
  }

  /** L'URL d'un matériau git, d'après sa description. */
  public String gitUrl() {
    Matcher m = URL_FIELD.matcher(description);
    if (m.find()) return m.group(1);
    m = ANY_URL.matcher(description);
    return m.find() ? m.group(1) : "";
  }

  /** Le pipeline amont d'un matériau pipeline, d'après sa description. */
  public String upstreamName() {
    int bracket = description.indexOf(" [");
    return bracket > 0 ? description.substring(0, bracket).trim() : name;
  }
}
