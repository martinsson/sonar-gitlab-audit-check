package ch.ge.edg.sonar.gitlab.audit.cli.shared;

import ch.ge.edg.sonar.gitlab.audit.matching.NamespaceMapping;
import java.io.PrintStream;
import java.nio.file.Path;
import picocli.CommandLine.Option;

/** --namespace-mapping : le fichier namespace GitLab → préfixe Sonar, des outils qui apparient. */
public final class NamespaceMappingOption {

  @Option(
      names = "--namespace-mapping",
      paramLabel = "<yaml>",
      defaultValue = NamespaceMapping.DEFAULT_FILE,
      description = "namespace GitLab → préfixe Sonar (défaut : ${DEFAULT-VALUE})")
  Path file = Path.of(NamespaceMapping.DEFAULT_FILE);

  /**
   * Le mapping, lu contre {@code workDir} ; ce qui en a empêché la lecture est dit sur {@code err}.
   */
  public NamespaceMapping load(Path workDir, PrintStream err) {
    NamespaceMapping mapping = NamespaceMapping.load(workDir, file);
    mapping.warnings().forEach(err::println);
    return mapping;
  }
}
