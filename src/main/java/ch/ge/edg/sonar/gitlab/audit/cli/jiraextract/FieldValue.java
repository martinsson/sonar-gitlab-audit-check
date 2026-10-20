package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Une valeur de champ Jira en texte de cellule : option, utilisateur, version, liste… */
public final class FieldValue {

  /** Au-delà, un texte est coupé : une description entière n'a rien à faire dans un tableau. */
  static final int MAX_TEXT = 300;

  /** Les clés qui portent le libellé d'un objet, dans l'ordre où on les cherche. */
  private static final List<String> LABELS = List.of("value", "name", "key", "displayName");

  /** Les sprints de Jira Server arrivent en texte « …Sprint@1a[id=1,name=Sprint 3,…] ». */
  private static final Pattern SPRINT_NAME = Pattern.compile(".*name=([^,\\]]*).*");

  private FieldValue() {}

  public static String render(@Nullable JsonNode v) {
    if (v == null || v.isMissingNode() || v.isNull()) return "";
    if (v.isArray()) {
      List<String> parts = new ArrayList<>();
      v.forEach(x -> parts.add(render(x)));
      return String.join("|", parts);
    }
    if (v.isObject()) return object(v);
    return text(v.asText());
  }

  /** Le libellé, et l'enfant d'une liste en cascade (« Parent / Enfant ») ; sinon le JSON. */
  private static String object(JsonNode v) {
    for (String k : LABELS) {
      if (v.has(k)) return v.path(k).asText() + child(v);
    }
    return v.toString();
  }

  private static String child(JsonNode v) {
    return v.has("child") ? " / " + render(v.path("child")) : "";
  }

  private static String text(String raw) {
    String s = raw;
    if (s.contains("com.atlassian.greenhopper") && s.contains("name=")) {
      s = SPRINT_NAME.matcher(s).replaceAll("$1");
    }
    return s.length() > MAX_TEXT ? s.substring(0, MAX_TEXT) + "…" : s;
  }
}
