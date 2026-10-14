package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Les auteurs d'une liste de commits, du plus au moins prolifique.
 *
 * @param ranked identité normalisée → nombre de commits, triés par nombre décroissant
 * @param labels identité normalisée → nom porté par les commits
 * @param total nombre de commits
 */
record Authors(List<Map.Entry<String, Long>> ranked, Map<String, String> labels, long total) {

  static Authors of(List<Commit> cs) {
    // Identité normalisée par Gitlab.identity, la même que côté parc : partie
    // locale de l'adresse, à défaut le nom, les deux ramenés dans le même
    // espace de noms. Sans cette dernière précaution, un commit signé « Dev 0 »
    // sans adresse et un commit de dev0@example.com font deux personnes, et le
    // bus factor double en silence.
    Map<String, Long> byEmail =
        cs.stream().collect(Collectors.groupingBy(Authors::identity, Collectors.counting()));

    // L'identité normalisée est illisible : on réaffiche le nom porté par les
    // commits, qui est ce que l'auditeur reconnaîtra.
    Map<String, String> labels = new HashMap<>();
    for (Commit x : cs) {
      String name = x.authorName();
      if (name != null && !name.isBlank()) labels.putIfAbsent(identity(x), name);
    }
    List<Map.Entry<String, Long>> ranked =
        byEmail.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .toList();
    return new Authors(ranked, labels, cs.size());
  }

  static String identity(Commit x) {
    return Gitlab.identity(x.authorEmail(), x.authorName());
  }

  int count() {
    return ranked.size();
  }

  /**
   * Bus factor : combien de personnes il faut réunir pour couvrir 80 % des commits. Un, c'est une
   * personne dont le départ arrête le projet.
   */
  int busFactor() {
    long cumulative = 0;
    int busFactor = 0;
    for (Map.Entry<String, Long> e : ranked) {
      cumulative += e.getValue();
      busFactor++;
      if (cumulative >= 0.8 * total) break;
    }
    return busFactor;
  }

  double share(Map.Entry<String, Long> author) {
    return 100.0 * author.getValue() / total;
  }

  String label(String identity) {
    return Format.label(labels, identity);
  }
}
