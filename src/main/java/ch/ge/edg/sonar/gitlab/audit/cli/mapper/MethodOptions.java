package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.matching.KeyMethod;
import ch.ge.edg.sonar.gitlab.audit.matching.Kind;
import ch.ge.edg.sonar.gitlab.audit.matching.NameSpec;
import ch.ge.edg.sonar.gitlab.audit.matching.Spec;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;

/** Les options de ProjectMapper qui disent comment apparier : colonnes, méthodes, noms. */
final class MethodOptions {

  @Option(
      names = "--left-key",
      description = "colonne qui nomme une ligne à gauche (défaut du préréglage : path)")
  @Nullable String leftKey;

  @Option(
      names = "--right-key",
      description = "colonne qui nomme une ligne à droite (défaut du préréglage : key)")
  @Nullable String rightKey;

  @Option(names = "--exact", description = "méthode par égalité stricte (répétable)")
  List<String> exact = new ArrayList<>();

  @Option(names = "--derived", description = "méthode par égalité normalisée (répétable)")
  List<String> derived = new ArrayList<>();

  @Option(names = "--link", description = "méthode informative par URL de dépôt (répétable)")
  List<String> link = new ArrayList<>();

  @Mixin NameOptions nameOptions = new NameOptions();

  @picocli.CommandLine.Spec(picocli.CommandLine.Spec.Target.MIXEE)
  CommandSpec mixee;

  /** Sans méthode ni noms, le préréglage Sonar × GitLab ; sinon ce que la ligne de commande dit. */
  Spec spec() {
    if (exact.isEmpty() && derived.isEmpty() && link.isEmpty() && nameOptions.names == null) {
      Spec spec = Spec.sonarGitlab(nameOptions.threshold, nameOptions.margin);
      if (leftKey == null && rightKey == null) return spec;
      return spec.withKeys(orElse(leftKey, spec.leftKey()), orElse(rightKey, spec.rightKey()));
    }
    List<KeyMethod> parsed = new ArrayList<>();
    exact.forEach(m -> parsed.add(method(m, Kind.EXACT)));
    derived.forEach(m -> parsed.add(method(m, Kind.DERIVED)));
    link.forEach(m -> parsed.add(method(m, Kind.LINK)));
    String nameSpec = nameOptions.names;
    return new Spec(
        orElse(leftKey, "path"),
        orElse(rightKey, "key"),
        parsed,
        nameSpec == null ? null : nameSpec(nameSpec, !nameOptions.plainNames),
        nameOptions.threshold,
        nameOptions.margin);
  }

  /** Une méthode de la ligne de commande ; illisible, l'erreur de saisie de picocli. */
  KeyMethod method(String s, Kind kind) {
    try {
      return KeyMethod.parse(s, kind);
    } catch (IllegalArgumentException e) {
      throw new CommandLine.ParameterException(mixee.commandLine(), e.getMessage(), e);
    }
  }

  /** --names ; illisible, l'erreur de saisie de picocli. */
  NameSpec nameSpec(String s, boolean pathLike) {
    try {
      return NameSpec.parse(s, pathLike);
    } catch (IllegalArgumentException e) {
      throw new CommandLine.ParameterException(mixee.commandLine(), e.getMessage(), e);
    }
  }

  private static String orElse(@Nullable String v, String dflt) {
    return v == null || v.isBlank() ? dflt : v;
  }
}
