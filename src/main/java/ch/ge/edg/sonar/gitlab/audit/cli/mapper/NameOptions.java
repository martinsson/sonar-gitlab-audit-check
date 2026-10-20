package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Option;

/** Les options de ProjectMapper qui règlent la ressemblance de noms. */
final class NameOptions {

  @Option(names = "--names", description = "ressemblance de noms : gauche=droite1,droite2")
  @Nullable String names;

  @Option(
      names = "--plain-names",
      description = "le nom de gauche n'est pas un chemin : pas de dernier segment privilégié")
  boolean plainNames;

  @Option(
      names = "--seuil-nom",
      defaultValue = "0.5",
      description = "score minimal d'une suggestion, 0–1 (défaut : ${DEFAULT-VALUE})")
  double threshold;

  @Option(
      names = "--marge-nom",
      defaultValue = "0.1",
      description = "écart minimal avec le candidat suivant (défaut : ${DEFAULT-VALUE})")
  double margin;
}
