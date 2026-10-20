package ch.ge.edg.sonar.gitlab.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * One HTTP stack (ARCHITECTURE.md): {@code rest} is the only package that opens connections, so
 * retries, captures, TLS and the error model are written once.
 */
@AnalyzeClasses(
    packages = "ch.ge.edg.sonar.gitlab.audit",
    importOptions = ImportOption.DoNotIncludeTests.class)
class TransportTest {

  private static final String REST = "ch.ge.edg.sonar.gitlab.audit.rest..";

  @ArchTest
  static final ArchRule ONLY_REST_SPEAKS_HTTP =
      noClasses()
          .that()
          .resideOutsideOfPackage(REST)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("java.net.http..", "javax.net.ssl..")
          .orShould()
          .dependOnClassesThat()
          .areAssignableTo(HttpURLConnection.class)
          .orShould()
          .callMethod(URL.class, "openConnection")
          .because(
              "a second transport is a second set of retries, captures and TLS settings that"
                  + " drift apart: GitLab and Sonar build on rest.RestClient");

  @ArchTest
  static final ArchRule ONLY_REST_TOUCHES_SYSTEM_PROPERTIES =
      noClasses()
          .that()
          .resideOutsideOfPackage(REST)
          .should()
          .callMethodWhere(setsASystemProperty())
          .because(
              "--insecure turns hostname verification off through a JVM-wide property: one place"
                  + " sets it, the client that also installs the trust-all TLS context");

  private static DescribedPredicate<JavaCall<?>> setsASystemProperty() {
    return DescribedPredicate.describe(
        "System.setProperty", (JavaCall<?> call) -> isSetProperty(call));
  }

  private static boolean isSetProperty(JavaAccess<?> access) {
    return access.getTargetOwner().getName().equals(System.class.getName())
        && access.getTarget().getName().equals("setProperty");
  }
}
