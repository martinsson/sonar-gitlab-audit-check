package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import ch.ge.edg.sonar.gitlab.audit.dora.JiraKeys;
import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Les clés Jira que cite un projet GitLab : dans ses commits (message), ses MR (titre, branche,
 * description) et ses noms de branches, groupées par préfixe. Chaque objet compte une fois par
 * préfixe, quel que soit le nombre de clés de ce préfixe qu'il cite.
 *
 * @param byPrefix par préfixe, dans l'ordre alphabétique
 */
record KeyMentions(Map<String, Mentions> byPrefix, int commits, int mrs, int branches) {

  /** Les MR et les branches lues au plus : vingt pages de cent. */
  static final int MAX_ITEMS = 2000;

  static KeyMentions read(Gitlab gitlab, String projectId, Instant since, int maxCommits) {
    Map<String, Mentions> byPrefix = new TreeMap<>();
    String base = "projects/" + projectId;
    Map<String, String> commitParams = new LinkedHashMap<>();
    commitParams.put("all", "true");
    commitParams.put("since", since.toString());
    List<JsonNode> commits = list(gitlab, base + "/repository/commits", commitParams, maxCommits);
    for (JsonNode c : commits) {
      tally(byPrefix, c.path("message").asText(c.path("title").asText()), Mentions::commit);
    }
    Map<String, String> mrParams = new LinkedHashMap<>();
    mrParams.put("state", "all");
    mrParams.put("updated_after", since.toString());
    List<JsonNode> mrs = list(gitlab, base + "/merge_requests", mrParams, MAX_ITEMS);
    for (JsonNode m : mrs) tally(byPrefix, mrText(m), Mentions::mr);
    List<JsonNode> branches = list(gitlab, base + "/repository/branches", Map.of(), MAX_ITEMS);
    for (JsonNode b : branches) {
      // Une branche s'écrit souvent feature/abc-123 : en majuscules avant de chercher.
      tally(byPrefix, b.path("name").asText().toUpperCase(Locale.ROOT), Mentions::branch);
    }
    return new KeyMentions(byPrefix, commits.size(), mrs.size(), branches.size());
  }

  private static List<JsonNode> list(
      Gitlab gitlab, String path, Map<String, String> params, int max) {
    return gitlab.paged(path, params, JsonNode.class, max).items();
  }

  static String mrText(JsonNode mr) {
    return mr.path("title").asText()
        + " "
        + mr.path("source_branch").asText()
        + " "
        + mr.path("description").asText("");
  }

  /** Les clés d'un texte : chaque ticket est noté, et chaque préfixe cité compté une fois. */
  static void tally(Map<String, Mentions> byPrefix, String text, Consumer<Mentions> count) {
    Set<String> prefixes = new LinkedHashSet<>();
    for (String key : JiraKeys.in(text)) {
      String prefix = JiraKeys.prefix(key);
      byPrefix.computeIfAbsent(prefix, p -> new Mentions()).ticket(key);
      prefixes.add(prefix);
    }
    for (String p : prefixes) count.accept(byPrefix.get(p));
  }

  /** Du plus cité dans les commits et les MR au moins cité ; à égalité, par ordre alphabétique. */
  List<Map.Entry<String, Mentions>> ranked() {
    List<Map.Entry<String, Mentions>> ranked = new ArrayList<>(byPrefix.entrySet());
    ranked.sort(Comparator.comparingInt(e -> -e.getValue().weight()));
    return ranked;
  }
}
