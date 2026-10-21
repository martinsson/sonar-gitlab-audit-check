package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import static ch.ge.edg.sonar.gitlab.audit.cli.activity.Api.text;
import static ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab.parse;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Une seule passe de pagination donne quatre choses : le volume, les auteurs distincts, le taux de
 * commits de merge et l'étalement dans le temps. Ce décompte les accumule commit par commit.
 */
final class CommitTally {

  /**
   * Git écrit « Revert "titre d'origine" », l'interface GitLab reprend la même forme, et les dépôts
   * en commits conventionnels écrivent « revert: … ». Le motif est ancré au début du titre : «
   * revert » au milieu d'une phrase parle du sujet, pas d'une annulation.
   */
  static final Pattern REVERT = Pattern.compile("^\\s*revert\\b", Pattern.CASE_INSENSITIVE);

  private final OffsetDateTime windowStart;
  private final Pattern bots;
  private final Set<String> authors = new TreeSet<>();
  private final Set<Long> days = new HashSet<>();
  private int human;
  private int bot;
  private int merges;
  private int reverts;
  private @Nullable OffsetDateTime newest;
  private @Nullable String first;

  CommitTally(OffsetDateTime windowStart, Pattern bots) {
    this.windowStart = windowStart;
    this.bots = bots;
  }

  void add(JsonNode c) {
    // Les commits arrivent du plus récent au plus ancien : le premier est la
    // tête de la branche par défaut, repli d'index de cache sans GraphQL.
    if (first == null) first = text(c, "id");
    String email = text(c, "author_email");
    String name = text(c, "author_name");
    if (Gitlab.isBot(bots, email, name)) {
      bot++;
      return;
    }
    human++;
    if (REVERT.matcher(text(c, "title")).find()) reverts++;
    authors.add(Gitlab.identity(email, name));
    if (Gitlab.isMerge(c)) merges++;
    OffsetDateTime d = parse(text(c, "committed_date"));
    if (d != null) {
      days.add(ChronoUnit.DAYS.between(windowStart, d));
      if (newest == null || d.isAfter(newest)) newest = d;
    }
  }

  /** Le compte, la tête de GraphQL complétée par ce que la pagination a vu. */
  CommitMeasure.Counted result(CommitMeasure.Head dated, boolean truncated) {
    OffsetDateTime last = dated.lastCommit();
    String sha = dated.sha();
    CommitMeasure.Head head =
        new CommitMeasure.Head(
            last != null ? last : newest, Text.isBlank(sha) && first != null ? first : sha);
    return new CommitMeasure.Counted(
        head,
        new CommitMeasure.Volume(human, bot, authors.size(), merges, days.size(), reverts),
        truncated);
  }
}
