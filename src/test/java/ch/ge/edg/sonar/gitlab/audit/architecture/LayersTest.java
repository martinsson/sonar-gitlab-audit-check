package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The layers of ARCHITECTURE.md, top to bottom. A layer may use the layers below it, never the ones
 * above. Layers that hold no code yet (cli.shared, exchange, runtime) are optional, so the rule
 * already says where they sit.
 */
@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class LayersTest {

  private static final String ROOT = "ch.ge.edg.sonar.gitlab.audit.";

  @ArchTest
  static final ArchRule EACH_LAYER_USES_ONLY_THE_LAYERS_BELOW =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .withOptionalLayers(true)
          .layer("Commands")
          .definedBy(
              resideInAPackage(ROOT + "cli..")
                  .and(resideInAPackage(ROOT + "cli.shared..").negate())
                  .as("the command packages cli.*"))
          .layer("Shared options")
          .definedBy(ROOT + "cli.shared..")
          .layer("Domain")
          .definedBy(ROOT + "matching..", ROOT + "exclusions..", ROOT + "dora..")
          .layer("Exchange")
          .definedBy(ROOT + "exchange..")
          .layer("Clients")
          .definedBy(ROOT + "gitlab..", ROOT + "sonar..", ROOT + "jira..", ROOT + "gocd..")
          .layer("Infrastructure")
          .definedBy(
              ROOT + "rest..",
              ROOT + "csv..",
              ROOT + "console..",
              ROOT + "parallel..",
              ROOT + "runtime..")
          .whereLayer("Commands")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Shared options")
          .mayOnlyBeAccessedByLayers("Commands")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Commands", "Shared options")
          .whereLayer("Exchange")
          .mayOnlyBeAccessedByLayers("Commands", "Shared options", "Domain")
          .whereLayer("Clients")
          .mayOnlyBeAccessedByLayers("Commands", "Shared options", "Domain", "Exchange")
          .whereLayer("Infrastructure")
          .mayOnlyBeAccessedByLayers("Commands", "Shared options", "Domain", "Exchange", "Clients")
          .ensureAllClassesAreContainedInArchitecture()
          .because(
              "a change to a tool must never ripple into the code it is built on: the HTTP"
                  + " plumbing does not know GitLab, GitLab does not know the matcher, the"
                  + " matcher does not know which command prints it. Every package has its"
                  + " place, so a new one has to be given a layer before it can exist");
}
