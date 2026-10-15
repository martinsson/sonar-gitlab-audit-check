package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.parse;
import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Console.DIM;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import com.fasterxml.jackson.databind.JsonNode;
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

  private final AuditContext ctx;

  GraphqlDates(AuditContext ctx) {
    this.ctx = ctx;
  }

  /** Le nombre de projets datés, ou -1 quand GraphQL est indisponible dès le premier lot. */
  int date(List<Proj> fresh) {
    int done = 0;
    for (int i = 0; i < fresh.size(); i += BATCH) {
      List<Proj> batch = fresh.subList(i, Math.min(i + BATCH, fresh.size()));
      ctx.counters().graphqlCalls++;
      JsonNode data = data(ctx.gl().graphql(query(batch)));
      if (data == null) {
        if (i == 0) {
          ctx.con().say("  GraphQL indisponible sur cette instance — repli REST.", DIM);
          return -1;
        }
        break; // partiel : les projets non datés passeront par REST
      }
      done += apply(batch, data);
    }
    return done;
  }

  private static String query(List<Proj> batch) {
    StringBuilder q = new StringBuilder("{");
    for (int j = 0; j < batch.size(); j++) {
      q.append("p")
          .append(j)
          .append(":project(fullPath:\"")
          .append(batch.get(j).path.replace("\"", "\\\""))
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

  private static int apply(List<Proj> batch, JsonNode data) {
    int done = 0;
    for (int j = 0; j < batch.size(); j++) {
      JsonNode last = data.path("p" + j).path("repository").path("tree").path("lastCommit");
      JsonNode n = last.path("committedDate");
      if (n.isTextual()) {
        batch.get(j).lastCommit = parse(n.asText());
        // Le SHA de tête indexe le cache des clés Sonar : il ne coûte
        // rien de plus ici, et c'est le seul index qui n'invalide
        // jamais dans le mauvais sens.
        if (last.path("sha").isTextual()) batch.get(j).headSha = last.path("sha").asText();
        done++;
      }
    }
    return done;
  }
}
