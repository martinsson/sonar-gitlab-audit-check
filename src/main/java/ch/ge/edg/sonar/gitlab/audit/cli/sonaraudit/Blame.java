package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.DIM;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Presentation.c;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.params;
import static ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.Values.parseDate;

import ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit.SonarApi.ScmResponse;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Sonar stocke l'auteur et la date du dernier commit par ligne : churn sans Git. */
final class Blame {

  private final Session session;
  private final PrintStream out;

  Blame(Session session) {
    this.session = session;
    this.out = session.out();
  }

  void report(String sample) {
    String fileKey = Lookups.firstFileOf(session, sample);
    ScmResponse scm =
        (fileKey == null)
            ? null
            : session.read(
                "api/sources/scm",
                params("key", fileKey, "from", "1", "to", "200"),
                ScmResponse.class);
    List<List<String>> rows = scm == null ? List.of() : scm.rows();
    if (fileKey == null || rows.isEmpty()) {
      out.println();
      out.println(
          c(
              "  Blame SCM inaccessible : il manque 'See Source Code' au token.\n"
                  + "  Sans lui, ni le churn ni l'âge du code ne sont calculables ici.",
              YELLOW));
      return;
    }
    // Chaque entrée est positionnelle : [ligne, auteur, date, révision].
    // Attention : Sonar ne renvoie PAS une entrée par ligne — les lignes
    // consécutives partageant un même changeset sont regroupées sous la
    // première. Le compte porte donc sur des changesets, pas sur des lignes.
    Set<String> authors =
        rows.stream()
            .filter(ScmResponse::hasAuthor)
            .map(row -> row.get(1))
            .collect(Collectors.toSet());

    // 200 + des lignes, mais tous les auteurs vides : Sonar a indexé le fichier
    // sans jamais recevoir de métadonnées SCM. C'est le cas 'fetch-depth: 1' —
    // un constat d'audit à part entière, pas une absence de résultat.
    if (authors.isEmpty()) {
      out.println();
      out.println(
          c(
              // Le délimiteur fermant, deux colonnes à gauche, garde le retrait du texte.
              """
                  Blame SCM vide : le scanner tourne sans métadonnées SCM
                  (clone shallow en CI, type 'fetch-depth: 1'). Sonar ne peut donc
                  ni attribuer les issues à un auteur, ni calculer correctement le
                  new code. C'est un constat d'audit à part entière.
                """
                  .stripTrailing(),
              YELLOW));
      return;
    }
    accessible(fileKey, rows.size(), authors.size(), dates(rows));
  }

  private static List<LocalDateTime> dates(List<List<String>> rows) {
    return rows.stream()
        .map(row -> row.size() > 2 ? parseDate(row.get(2)) : null)
        .filter(Objects::nonNull)
        .sorted()
        .toList();
  }

  private void accessible(String fileKey, int changesets, int authors, List<LocalDateTime> dates) {
    out.println();
    out.println(c("  Blame SCM accessible via l'API :", GREEN));
    out.printf("    Fichier témoin                : %s%n", Values.shortName(fileKey));
    out.printf("    Auteurs distincts (%d changesets): %d%n", changesets, authors);
    if (!dates.isEmpty()) {
      out.printf(
          "    Dernière modification         : %s%n", dates.get(dates.size() - 1).toLocalDate());
      out.printf("    Plus ancienne ligne           : %s%n", dates.get(0).toLocalDate());
    }
    out.println(c("    → churn et âge du code calculables sans accès Git", DIM));
  }
}
