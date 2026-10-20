package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.NamespaceMappingOption;
import ch.ge.edg.sonar.gitlab.audit.cli.shared.StyleOptions;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.matching.Evaluation;
import ch.ge.edg.sonar.gitlab.audit.matching.MappingFile;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import ch.ge.edg.sonar.gitlab.audit.matching.Spec;
import ch.ge.edg.sonar.gitlab.audit.matching.report.MappingFilePrinter;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Apparier les lignes de deux CSV, et dire ce que vaut chaque manière de le faire.
 *
 * <p>Né de CrossAudit, qui en reste le premier client, et sorti pour servir ailleurs : tout ce qui
 * est propre à Sonar × GitLab tient dans un préréglage ({@link Spec#sonarGitlab}), le reste ne
 * connaît que des colonnes.
 *
 * <p>Deux produits, et le second compte autant que le premier :
 *
 * <p>L'APPARIEMENT Les méthodes sûres dans l'ordre donné — égalité exacte, puis égalité après
 * normalisation — puis la ressemblance de noms sur ce qui reste, en suggestion seulement. Les liens
 * sont lus et jamais utilisés pour apparier.
 *
 * <p>LE RAPPORT Chaque méthode est aussi évaluée seule, sur toutes les lignes : ce qu'elle propose,
 * ce qu'elle seule apporte, où elle contredit les autres. La ressemblance de noms est jugée contre
 * les paires sûres — précision et rappel à chaque seuil — et les échecs sont montrés par l'exemple
 * : quasi-réussites, noms qui divergent malgré une paire sûre, mots jamais retrouvés de l'autre
 * côté. C'est ce qui dit quoi améliorer ensuite. Le JSON (--report) est fait pour être relu, par un
 * humain ou par un assistant.
 *
 * <p>Usage :
 *
 * <pre>
 * jbang ProjectMapper.java --left pratiques.csv --right inventaire.csv  *     --out correspondances.csv --report rapport-appariement.json
 *
 * jbang ProjectMapper.java --left a.csv --right b.csv  *     --left-key nom --right-key code  *     --exact id:ref=ref_externe --derived nom:nom=code  *     --names nom=libelle,code --plain-names
 * </pre>
 */
@Command(
    name = "ProjectMapper",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Apparie deux CSV et évalue chaque méthode d'appariement.",
    footer = {
      "",
      "Sans --exact, --derived, --link ni --names : préréglage Sonar × GitLab",
      "(gauche = pratiques.csv de GitlabActivityAudit --deep, droite =",
      "inventaire de SonarAuditCheck --csv).",
      "",
      "Méthode : id:colonne_gauche=colonne_droite[?colonne=valeur]",
      "  --exact    égalité stricte             → confiance exact",
      "  --derived  égalité après normalisation → confiance derived",
      "  --link     la colonne droite porte des URL de dépôt ; informatif",
      "Appliquées dans l'ordre donné, --exact d'abord.",
      "",
      "--names gauche=droite1,droite2 : ressemblance TF-IDF, en suggestion.",
    })
public class ProjectMapper implements Callable<Integer> {

  @Option(
      names = "--left",
      required = true,
      description = "CSV à apparier (une ligne de sortie par ligne)")
  Path leftCsv;

  @Option(names = "--right", required = true, description = "CSV où chercher l'homologue")
  Path rightCsv;

  @Mixin MethodOptions methods = new MethodOptions();

  @Mixin NamespaceMappingOption namespaceMapping = new NamespaceMappingOption();

  @Option(names = "--out", description = "CSV d'appariement, une ligne par ligne de gauche")
  @Nullable Path out;

  @Option(
      names = "--reapparier",
      description =
          "recalculer les paires exact/derived d'un --out existant " + "(manuel et rejete restent)")
  boolean recompute;

  @Option(names = "--report", description = "rapport JSON d'évaluation des méthodes")
  @Nullable Path report;

  @Option(
      names = "--examples",
      defaultValue = "15",
      description = "exemples par rubrique du rapport (défaut : ${DEFAULT-VALUE})")
  int examples;

  @Mixin StyleOptions style = new StyleOptions();

  private final Host host;

  ProjectMapper(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new ProjectMapper(host)).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    Console console = host.console(style.colorMode());
    // Le mapping namespace-mapping.yaml du répertoire de travail se lit — et prévient s'il
    // manque — avant que la commande n'écrive quoi que ce soit.
    namespaceMapping.load(host.workDir(), console.err());
    Spec spec = methods.spec();
    Path leftFile = leftCsv;
    Path rightFile = rightCsv;
    Csv.Table left = Csv.read(leftFile);
    Csv.Table right = Csv.read(rightFile);
    left.require(leftFile, "--left", spec.leftKey());
    right.require(rightFile, "--right", spec.rightKey());

    MapperReport screen = new MapperReport(console);
    screen.inputs(left, leftFile, right, rightFile);

    Path mapping = out;
    Result r =
        mapping == null
            ? MappingFile.map(spec, left, right)
            : MappingFilePrinter.mapAndKeep(
                spec, left, right, new MappingFile(mapping, style.comma(), recompute), console);
    Report rep = Evaluation.evaluate(r, examples);
    screen.evaluation(rep);
    if (mapping != null) screen.openingHint(mapping, style.comma());
    Path json = report;
    if (json != null) {
      rep.writeTo(json);
      screen.reportWritten(json);
    }
    return 0;
  }
}
