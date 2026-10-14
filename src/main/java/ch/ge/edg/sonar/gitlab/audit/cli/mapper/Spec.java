package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Ce qu'on demande : les clés qui nomment une ligne, les méthodes dans l'ordre, les noms. */
public record Spec(
    String leftKey,
    String rightKey,
    List<KeyMethod> methods,
    @Nullable NameSpec names,
    double threshold,
    double margin) {

  public static final String NAMES_ID = "noms";
  public static final String NAMES_LABEL = "noms ressemblants (TF-IDF)";
  public static final String NONE_LABEL = "aucune";

  /**
   * Le croisement tel que CrossAudit le fait : gauche = pratiques.csv, droite = inventaire Sonar.
   */
  public static Spec sonarGitlab(double threshold, double margin) {
    return new Spec(
        "path",
        "key",
        List.of(
            // La clé que le scanner envoie vraiment : la seule méthode qui
            // lit l'instruction donnée au pipeline.
            new KeyMethod(
                "cle_ci", "clé lue dans la CI", Kind.EXACT, "cle_sonar", "key", null, null),
            // groupId:artifactId du pom racine : la clé que le scanner
            // Maven prend quand la CI ne lui en donne pas — le cas du
            // composant Sonar partagé avec project_key vide.
            new KeyMethod("cle_pom", "clé Maven du pom", Kind.EXACT, "cle_pom", "key", null, null),
            // Ce que SonarQube a enregistré en important le dépôt.
            new KeyMethod(
                "liaison",
                "liaison DevOps Sonar → GitLab",
                Kind.EXACT,
                "id",
                "alm_repository",
                "alm",
                "gitlab"),
            // Mapping namespace GitLab → préfixe Sonar + nom court extrait.
            new KeyMethod(
                "ns_map", "mapping namespace GL→Sonar", Kind.MAPPED, "path", "key", null, null),
            // Le chemin normalisé comme une clé Sonar l'est souvent.
            new KeyMethod(
                "chemin",
                "clé normalisée = chemin GitLab",
                Kind.DERIVED,
                "path",
                "key",
                null,
                null),
            // Le <name> du pom devient le nom affiché dans Sonar. Plus
            // faible qu'une clé — un nom se répète —, donc après elles,
            // et seulement s'il ne désigne qu'un projet.
            new KeyMethod(
                "nom_pom", "nom du pom = nom Sonar", Kind.NAME, "nom_pom", "name", null, null),
            // Saisis à la main : lus, jamais utilisés pour apparier.
            new KeyMethod(
                "liens", "liens saisis dans Sonar", Kind.LINK, "path", "liens", null, null)),
        new NameSpec("path", List.of("key", "name"), true),
        threshold,
        margin);
  }

  public Spec withKeys(String left, String right) {
    return new Spec(left, right, methods, names, threshold, margin);
  }
}
