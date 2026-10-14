package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapper.Confidence;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.naming.NameMatcher;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Chaque projet Sonar contre TOUT GitLab, pas seulement la sélection.
 *
 * <p>Le croisement apparie les ~200 projets que l'audit GitLab a retenus : une ligne par projet
 * GitLab sélectionné. Vu depuis Sonar, c'est un dénominateur faux — un projet Sonar dont le dépôt
 * n'a pas été tiré y reste sans homologue, indiscernable d'un projet Sonar sans dépôt du tout. Ici,
 * la question part de l'autre côté : pour chaque projet Sonar, quel dépôt de l'inventaire GitLab
 * complet ?
 *
 * <p>Mêmes principes que le croisement, et mêmes outils :
 *
 * <p>1. les paires sûres du croisement (clé de la CI, clé du pom, saisies à la main) sont reprises
 * telles quelles, et un « rejete » n'est jamais reproposé ; 2. la liaison DevOps de Sonar, cette
 * fois contre tout l'inventaire ; 3. des méthodes dérivées, dans l'ordre, qui n'apparient que si
 * elles ne désignent qu'un dépôt : clé d'import GitLab, clé = chemin, clé = fin du chemin, nom
 * court de la clé = dernier segment ou nom GitLab, nom Sonar = dernier segment. Plusieurs dépôts du
 * même nom (copies, forks, espaces personnels) ne donnent qu'une suggestion, départagée par la
 * ressemblance des noms ou, à défaut, par l'activité ; 4. la ressemblance de noms de {@link
 * NameMatcher} sur ce qui reste, un pour un, avec le seuil et la marge du croisement : en
 * suggestion.
 *
 * <p>Les méthodes dérivées sont aussi jouées sur les paires exactes, pour dire combien de fois
 * elles auraient trouvé la même chose : c'est ce qui permet de les compter comme sûres.
 */
final class SonarToGitlab {

  static final String NONE = "aucune";

  /** Le sort d'un projet Sonar. {@code gitlab} est null quand rien n'a été trouvé. */
  record Pair(
      Csv.Row sonar,
      Csv.@Nullable Row gitlab,
      String method,
      Confidence confidence,
      @Nullable Double score,
      String candidates,
      String reason) {

    boolean joined() {
      return gitlab != null
          && (confidence == Confidence.EXACT
              || confidence == Confidence.DERIVED
              || confidence == Confidence.MANUEL);
    }

    String key() {
      return sonar.str("key");
    }

    String path() {
      return gitlab == null ? "" : gitlab.str("path");
    }
  }

  /** Une méthode dérivée jouée sur une paire exacte : d'accord ou non. */
  record Check(String method, boolean agrees) {}

  record Result(List<Pair> pairs, List<Check> checks) {

    /** Les dépôts GitLab qu'un projet Sonar désigne sûrement. */
    Set<String> joinedPaths() {
      Set<String> s = new HashSet<>();
      for (Pair p : pairs) if (p.joined()) s.add(p.path());
      return s;
    }
  }

  /**
   * Les refus saisis à la main dans l'appariement.
   *
   * @param pairs les paires refusées, « clé\nchemin » : jamais reproposées
   * @param paths les dépôts refusés sans clé connue : plus de suggestion de noms vers eux
   */
  record Rejections(Set<String> pairs, Set<String> paths) {

    /** Les lignes « rejete » d'un appariement, s'il existe déjà. */
    static Rejections read(Path file) throws IOException {
      Set<String> rejected = new HashSet<>(), rejectedPaths = new HashSet<>();
      if (Files.exists(file)) {
        for (Csv.Row r : Csv.read(file).rows()) {
          if (Confidence.parse(r.str("confiance")) != Confidence.REJETE) continue;
          if (r.str("droite_key").isEmpty()) rejectedPaths.add(r.str("gauche_path"));
          else rejected.add(r.str("droite_key") + "\n" + r.str("gauche_path"));
        }
      }
      return new Rejections(rejected, rejectedPaths);
    }

    boolean pair(String key, String path) {
      return pairs.contains(key + "\n" + path);
    }
  }

  /**
   * La ressemblance de noms exigée d'une suggestion.
   *
   * @param threshold score minimal du meilleur candidat
   * @param margin écart minimal entre le meilleur candidat et le suivant
   */
  record NameThresholds(double threshold, double margin) {}

  private SonarToGitlab() {}

  /**
   * @param fromCross les paires du croisement (gauche = GitLab, droite = Sonar)
   * @param rejected les refus saisis à la main
   */
  static Result map(
      Csv.Table sonar,
      Csv.Table inventory,
      List<ProjectMapper.Match> fromCross,
      Rejections rejected,
      NameThresholds thresholds) {
    GitlabInventoryIndex index = new GitlabInventoryIndex(inventory.rows());
    NameMatcher matcher =
        ProjectMapper.nameMatcher(
            new ProjectMapper.NameSpec("path", List.of("key", "name"), true), inventory, sonar);
    SonarFates fates = new SonarFates(sonar.rows());
    fromCross(fates, index, fromCross);
    devopsLink(fates, index, rejected);
    new DerivedMethods(fates, index, matcher, rejected, thresholds.margin()).run();
    new NameRound(fates, index, matcher, rejected, thresholds).run();
    return fates.result();
  }

  /** Les paires du croisement d'une clé Sonar : la première, et tous les chemins qu'elle a. */
  private record CrossPaths(ProjectMapper.Match first, List<String> paths) {}

  /** 1. Ce que le croisement tient déjà pour sûr. */
  private static void fromCross(
      SonarFates fates, GitlabInventoryIndex index, List<ProjectMapper.Match> fromCross) {
    Map<String, CrossPaths> byKey = new LinkedHashMap<>();
    for (ProjectMapper.Match m : fromCross) {
      Csv.Row right = m.right();
      if (!m.joined() || right == null) continue;
      byKey
          .computeIfAbsent(right.str("key"), k -> new CrossPaths(m, new ArrayList<>()))
          .paths()
          .add(m.left().str("path"));
    }
    for (Map.Entry<String, CrossPaths> e : byKey.entrySet()) {
      Integer s = fates.indexOf(e.getKey());
      if (s == null) continue;
      ProjectMapper.Match m = e.getValue().first();
      List<String> paths = e.getValue().paths();
      Integer g = index.byPath(paths.get(0));
      fates.set(
          s,
          new Pair(
              fates.sonar(s),
              g != null ? index.row(g) : m.left(),
              "croisement : " + m.label(),
              m.confidence(),
              null,
              paths.size() > 1 ? "clé partagée par " + String.join(", ", paths) : "",
              ""));
    }
  }

  /** 2. La liaison DevOps, contre tout l'inventaire. */
  private static void devopsLink(
      SonarFates fates, GitlabInventoryIndex index, Rejections rejected) {
    for (int s = 0; s < fates.size(); s++) {
      Csv.Row r = fates.sonar(s);
      if (fates.get(s) != null || !"gitlab".equals(r.str("alm"))) continue;
      Integer g = index.byId(r.str("alm_repository"));
      if (g != null && !rejected.pair(r.str("key"), index.path(g))) {
        fates.set(
            s,
            new Pair(
                r, index.row(g), "liaison DevOps Sonar → GitLab", Confidence.EXACT, null, "", ""));
      }
    }
  }
}
