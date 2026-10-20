package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.parse;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Route GraphQL : ~50 projets par requête via des alias {@code project(fullPath:)}. Donne la date
 * du dernier commit réel sur la branche par défaut — un signal de commit, pas un proxy d'événement
 * — pour deux ordres de grandeur de moins que REST.
 *
 * <p>NON VÉRIFIÉ contre une instance réelle : la forme de Repository.tree.lastCommit varie selon la
 * version de GitLab. En cas d'erreur GraphQL on retombe silencieusement sur REST, qui reste la
 * route certaine.
 */
final class GraphqlDates {

  private static final int BATCH = 50;

  private final Gitlab gl;

  GraphqlDates(Gitlab gl) {
    this.gl = gl;
  }

  /**
   * Ce que GraphQL a daté.
   *
   * @param heads une tête par projet demandé, dans l'ordre, inconnue quand il ne l'a pas datée
   * @param calls les requêtes GraphQL envoyées
   * @param available faux quand GraphQL est indisponible dès le premier lot
   */
  record Dating(List<CommitMeasure.Head> heads, int calls, boolean available) {

    /** Les projets datés. */
    long dated() {
      return heads.stream().filter(h -> h.lastCommit() != null).count();
    }
  }

  Dating date(List<AuditedProject> fresh) {
    List<CommitMeasure.Head> heads = new ArrayList<>();
    int calls = 0;
    for (int i = 0; i < fresh.size(); i += BATCH) {
      List<AuditedProject> batch = fresh.subList(i, Math.min(i + BATCH, fresh.size()));
      calls++;
      JsonNode data = data(gl.graphql(query(batch)));
      if (data == null) {
        if (i == 0) return new Dating(unknown(fresh.size()), calls, false);
        break; // partiel : les projets non datés passeront par REST
      }
      heads.addAll(heads(batch.size(), data));
    }
    heads.addAll(unknown(fresh.size() - heads.size()));
    return new Dating(heads, calls, true);
  }

  private static List<CommitMeasure.Head> unknown(int n) {
    return Collections.nCopies(n, CommitMeasure.Head.unknown());
  }

  private static String query(List<AuditedProject> batch) {
    StringBuilder q = new StringBuilder("{");
    for (int j = 0; j < batch.size(); j++) {
      q.append("p")
          .append(j)
          .append(":project(fullPath:\"")
          .append(batch.get(j).project().path().replace("\"", "\\\""))
          .append("\"){repository{rootRef tree{lastCommit{sha committedDate}}}} ");
    }
    return q.append("}").toString();
  }

  /** Le nœud data d'une réponse sans erreur, sinon null. */
  private static @Nullable JsonNode data(Gitlab.Response r) {
    if (r.status() != 200) return null;
    JsonNode json = r.json();
    JsonNode data = json.path("data");
    return data.isMissingNode() || json.has("errors") ? null : data;
  }

  private static List<CommitMeasure.Head> heads(int size, JsonNode data) {
    List<CommitMeasure.Head> heads = new ArrayList<>();
    for (int j = 0; j < size; j++) {
      JsonNode last = data.path("p" + j).path("repository").path("tree").path("lastCommit");
      JsonNode n = last.path("committedDate");
      if (n.isTextual()) {
        // Le SHA de tête indexe le cache des clés Sonar : il ne coûte
        // rien de plus ici, et c'est le seul index qui n'invalide
        // jamais dans le mauvais sens.
        JsonNode sha = last.path("sha");
        heads.add(new CommitMeasure.Head(parse(n.asText()), sha.isTextual() ? sha.asText() : null));
      } else {
        heads.add(CommitMeasure.Head.unknown());
      }
    }
    return heads;
  }
}
