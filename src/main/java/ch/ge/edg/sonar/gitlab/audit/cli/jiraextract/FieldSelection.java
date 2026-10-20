package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Les champs propres à l'instance que le CSV des tickets reprend, lus dans /rest/api/2/field : les
 * champs personnalisés dont le nom évoque la plateforme ou la gravité, ceux demandés par --field,
 * et le champ « Epic Link » de Jira Software, dont l'id change d'une instance à l'autre.
 */
final class FieldSelection {

  /**
   * Champs personnalisés repris d'office : ceux qui pourraient dire la plateforme ou la gravité.
   */
  static final Pattern AUTO_FIELD =
      Pattern.compile(
          "(?i)env|plateforme|platform|s[ée]v[ée]rit|severity|gravit|impact|incident|cause|origine"
              + "|d[ée]tect");

  private static final String EPIC_LINK = ":gh-epic-link";

  private final Map<String, String> columns;
  private final @Nullable String epicField;

  private FieldSelection(Map<String, String> columns, @Nullable String epicField) {
    this.columns = columns;
    this.epicField = epicField;
  }

  /**
   * La sélection d'une instance.
   *
   * @param fields la réponse de /rest/api/2/field
   * @param wanted les --field, par id ou par nom (sans égard à la casse)
   * @param err où dire qu'un champ demandé n'existe pas : on l'ignore, on ne s'arrête pas
   */
  public static FieldSelection of(JsonNode fields, List<String> wanted, PrintStream err) {
    Map<String, String> names = new LinkedHashMap<>();
    Map<String, String> columns = new LinkedHashMap<>();
    String epic = null;
    for (JsonNode f : fields) {
      String id = f.path("id").asText();
      String name = f.path("name").asText();
      names.put(id, name);
      if (f.path("schema").path("custom").asText("").endsWith(EPIC_LINK)) epic = id;
      if (f.path("custom").asBoolean(false) && AUTO_FIELD.matcher(name).find()) {
        columns.put(id, name);
      }
    }
    for (String w : wanted) {
      Map.Entry<String, String> field = lookup(names, w);
      if (field == null) err.println("Champ inconnu, ignoré : " + w);
      else columns.put(field.getKey(), field.getValue());
    }
    return new FieldSelection(columns, epic);
  }

  /** Par id d'abord, puis par nom : le premier dans l'ordre de /rest/api/2/field. */
  private static Map.@Nullable Entry<String, String> lookup(Map<String, String> names, String w) {
    String byId = names.get(w);
    if (byId != null) return Map.entry(w, byId);
    for (Map.Entry<String, String> e : names.entrySet()) {
      if (e.getValue().equalsIgnoreCase(w)) return e;
    }
    return null;
  }

  /** « Champs en plus : … », s'il y en a. */
  public void announce(PrintStream err) {
    if (!columns.isEmpty()) err.println("Champs en plus : " + String.join(", ", columns.values()));
  }

  /** Les en-têtes des colonnes en plus : « nom [id] ». */
  public List<String> headers() {
    List<String> out = new ArrayList<>();
    columns.forEach((id, name) -> out.add(name + " [" + id + "]"));
    return out;
  }

  /** Les cellules des colonnes en plus, pour les champs d'un ticket. */
  public List<String> cells(JsonNode issueFields) {
    List<String> out = new ArrayList<>();
    for (String id : columns.keySet()) out.add(FieldValue.render(issueFields.path(id)));
    return out;
  }

  /** L'épopée d'un ticket ; vide si l'instance n'a pas de champ Epic Link. */
  public String epic(JsonNode issueFields) {
    return epicField == null ? "" : FieldValue.render(issueFields.path(epicField));
  }
}
