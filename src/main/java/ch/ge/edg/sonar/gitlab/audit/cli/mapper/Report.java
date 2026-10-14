package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Le rapport : ce que vaut chaque méthode. Écrit en JSON (--report) pour être relu, par un humain
 * ou par un assistant ; les noms des composants sont ceux des champs du JSON.
 */
public record Report(
    Summary summary,
    List<String> unavailable,
    List<MethodStats> methods,
    List<Agreement> agreements,
    List<Example> disagreements,
    List<LinkStats> links,
    @Nullable NameStats names) {

  /** Les garde-fous de la ressemblance de noms, à plat dans le JSON du résumé. */
  public record Guards(double threshold, double margin) {}

  public record Summary(
      int left,
      int right,
      Map<String, Integer> byMethod,
      int joined,
      int suggestions,
      int unmatched,
      int rightUnmatched,
      @JsonUnwrapped Guards guards) {}

  /**
   * {@code proposes} : lignes où la méthode, seule, désigne quelque chose. {@code wins} : lignes
   * qu'elle a tranchées dans l'ordre d'application. {@code alone} : lignes où aucune autre méthode
   * sûre ne propose rien — ce qu'on perdrait sans elle.
   */
  public record MethodStats(
      String id, String label, String kind, int proposes, int ambiguous, int wins, int alone) {}

  public record Agreement(String a, String b, int both, int agree, int disagree) {}

  public record Example(String left, String detail) {}

  public record LinkStats(
      String id,
      int rightWithLinks,
      int leftPointedAt,
      int confirmSure,
      int confirmSuggestion,
      int contradict,
      int wouldJoin) {}

  public record Bucket(String range, int correct, int wrong) {}

  /** {@code precision} est null quand rien n'est proposé : zéro sur zéro n'est pas 0 %. */
  public record ThresholdPoint(
      double threshold, int proposed, int correct, @Nullable Double precision, double recall) {}

  public record TokenCount(String token, int count, boolean onRight) {}

  /** Les mots restés seuls de chaque côté, à plat dans le JSON des noms. */
  public record Vocabulary(
      List<TokenCount> unmatchedLeftTokens, List<TokenCount> unmatchedRightPrefixes) {}

  public record NameStats(
      int evaluatedOn,
      List<Bucket> scoreOfBestCandidate,
      List<ThresholdPoint> thresholds,
      Map<String, Integer> rejections,
      List<Example> wrongOnSurePairs,
      List<Example> divergentSurePairs,
      List<Example> nearMisses,
      @JsonUnwrapped Vocabulary vocabulary) {}

  /** Le rapport en JSON indenté ; le répertoire est créé au besoin. */
  public void writeTo(Path path) throws IOException {
    Path dir = path.toAbsolutePath().getParent();
    if (dir != null) Files.createDirectories(dir);
    new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(path.toFile(), this);
  }
}
