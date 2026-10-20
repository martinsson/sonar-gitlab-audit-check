package ch.ge.edg.sonar.gitlab.audit.matching;

import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabPractices;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import java.util.ArrayList;
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
        GitlabPractices.PATH,
        SonarInventory.KEY,
        sonarGitlabMethods(),
        new NameSpec(GitlabPractices.PATH, List.of(SonarInventory.KEY, SonarInventory.NAME), true),
        threshold,
        margin);
  }

  private static List<KeyMethod> sonarGitlabMethods() {
    List<KeyMethod> all = new ArrayList<>(exactMethods());
    all.addAll(looseMethods());
    return all;
  }

  /** Les clés qui s'égalent : lues telles quelles des deux côtés. */
  private static List<KeyMethod> exactMethods() {
    return List.of(
        // La clé que le scanner envoie vraiment : la seule méthode qui
        // lit l'instruction donnée au pipeline.
        new KeyMethod(
            "cle_ci",
            "clé lue dans la CI",
            Kind.EXACT,
            GitlabPractices.SONAR_KEY,
            SonarInventory.KEY,
            null,
            null),
        // groupId:artifactId du pom racine : la clé que le scanner
        // Maven prend quand la CI ne lui en donne pas — le cas du
        // composant Sonar partagé avec project_key vide.
        new KeyMethod(
            "cle_pom",
            "clé Maven du pom",
            Kind.EXACT,
            GitlabPractices.POM_KEY,
            SonarInventory.KEY,
            null,
            null),
        // Ce que SonarQube a enregistré en important le dépôt.
        new KeyMethod(
            "liaison",
            "liaison DevOps Sonar → GitLab",
            Kind.EXACT,
            GitlabPractices.ID,
            SonarInventory.ALM_REPOSITORY,
            SonarInventory.ALM,
            "gitlab"));
  }

  /** Les méthodes plus lâches : un dossier, un nom de pom, des liens saisis. */
  private static List<KeyMethod> looseMethods() {
    return List.of(
        // Mapping namespace GitLab → préfixe Sonar + nom court extrait.
        new KeyMethod(
            "ns_map",
            "mapping namespace GL→Sonar",
            Kind.MAPPED,
            GitlabPractices.PATH,
            SonarInventory.KEY,
            null,
            null),
        // Le chemin normalisé comme une clé Sonar l'est souvent.
        new KeyMethod(
            "chemin",
            "clé normalisée = chemin GitLab",
            Kind.DERIVED,
            GitlabPractices.PATH,
            SonarInventory.KEY,
            null,
            null),
        // Le <name> du pom devient le nom affiché dans Sonar. Plus
        // faible qu'une clé — un nom se répète —, donc après elles,
        // et seulement s'il ne désigne qu'un projet.
        new KeyMethod(
            "nom_pom",
            "nom du pom = nom Sonar",
            Kind.NAME,
            GitlabPractices.POM_NAME,
            SonarInventory.NAME,
            null,
            null),
        // Saisis à la main : lus, jamais utilisés pour apparier.
        new KeyMethod(
            "liens",
            "liens saisis dans Sonar",
            Kind.LINK,
            GitlabPractices.PATH,
            SonarInventory.LIENS,
            null,
            null));
  }

  public Spec withKeys(String left, String right) {
    return new Spec(left, right, methods, names, threshold, margin);
  }
}
