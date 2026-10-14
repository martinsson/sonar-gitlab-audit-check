package ch.ge.edg.sonar.gitlab.audit.cli;

import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Evaluation;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.KeyMethod;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Keys;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Kind;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.MappingFile;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.NameSpec;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Report;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.ReportPrinter;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Result;
import ch.ge.edg.sonar.gitlab.audit.cli.mapper.Spec;
import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
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
 * <p>Usage : jbang ProjectMapper.java --left pratiques.csv --right inventaire.csv \ --out
 * correspondances.csv --report rapport-appariement.json
 *
 * <p>jbang ProjectMapper.java --left a.csv --right b.csv \ --left-key nom --right-key code \
 * --exact id:ref=ref_externe --derived nom:nom=code \ --names nom=libelle,code --plain-names
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
  @Nullable Path leftCsv;

  @Option(names = "--right", required = true, description = "CSV où chercher l'homologue")
  @Nullable Path rightCsv;

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

  @Option(
      names = "--comma",
      description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
  boolean comma;

  @Option(
      names = "--color",
      defaultValue = "auto",
      description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
  String colorMode = "auto";

  private static final String BOLD = "\033[1m";
  private static final String DIM = "\033[2m";

  static {
    // Le mapping namespace-mapping.yaml du répertoire de travail se charge — et prévient s'il
    // manque — au chargement de la commande, avant qu'elle n'écrive quoi que ce soit.
    Keys.namespaces();
  }

  public static void main(String[] args) {
    ConsoleOut.install();
    System.exit(new CommandLine(new ProjectMapper()).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    PrintStream console = System.out;
    ConsoleOut.colorMode(colorMode);
    Spec spec = spec();
    // picocli refuse la ligne de commande sans --left ni --right : jamais null ici.
    Path leftFile = Objects.requireNonNull(leftCsv);
    Path rightFile = Objects.requireNonNull(rightCsv);
    Csv.Table left = Csv.read(leftFile);
    Csv.Table right = Csv.read(rightFile);
    left.require(leftFile, "--left", spec.leftKey());
    right.require(rightFile, "--right", spec.rightKey());

    console.println(ConsoleOut.color("\nAppariement", BOLD));
    console.printf("  Gauche : %d lignes (%s)%n", left.rows().size(), leftFile);
    console.printf("  Droite : %d lignes (%s)%n", right.rows().size(), rightFile);

    Path mapping = out;
    Result r =
        MappingFile.mapAndKeep(
            spec,
            left,
            right,
            mapping == null ? null : new MappingFile(mapping, comma, recompute),
            console);
    Report rep = Evaluation.evaluate(r, examples);
    new ReportPrinter(console).print(rep);

    if (mapping != null) console.println(ConsoleOut.color(Csv.openingHint(mapping, comma), DIM));
    Path json = report;
    if (json != null) {
      rep.writeTo(json);
      console.printf("  Rapport     : %s%n", json.toAbsolutePath());
    }
    return 0;
  }

  /** Sans méthode ni noms, le préréglage Sonar × GitLab ; sinon ce que la ligne de commande dit. */
  private Spec spec() {
    if (exact.isEmpty() && derived.isEmpty() && link.isEmpty() && nameOptions.names == null) {
      Spec spec = Spec.sonarGitlab(nameOptions.threshold, nameOptions.margin);
      if (leftKey == null && rightKey == null) return spec;
      return spec.withKeys(orElse(leftKey, spec.leftKey()), orElse(rightKey, spec.rightKey()));
    }
    List<KeyMethod> methods = new ArrayList<>();
    exact.forEach(m -> methods.add(method(m, Kind.EXACT)));
    derived.forEach(m -> methods.add(method(m, Kind.DERIVED)));
    link.forEach(m -> methods.add(method(m, Kind.LINK)));
    String nameSpec = nameOptions.names;
    return new Spec(
        orElse(leftKey, "path"),
        orElse(rightKey, "key"),
        methods,
        nameSpec == null ? null : nameSpec(nameSpec, !nameOptions.plainNames),
        nameOptions.threshold,
        nameOptions.margin);
  }

  /** Une méthode de la ligne de commande ; illisible, l'erreur de saisie de picocli. */
  static KeyMethod method(String s, Kind kind) {
    try {
      return KeyMethod.parse(s, kind);
    } catch (IllegalArgumentException e) {
      throw new CommandLine.ParameterException(
          new CommandLine(new ProjectMapper()), e.getMessage(), e);
    }
  }

  /** --names ; illisible, l'erreur de saisie de picocli. */
  static NameSpec nameSpec(String s, boolean pathLike) {
    try {
      return NameSpec.parse(s, pathLike);
    } catch (IllegalArgumentException e) {
      throw new CommandLine.ParameterException(
          new CommandLine(new ProjectMapper()), e.getMessage(), e);
    }
  }

  private static String orElse(@Nullable String v, String dflt) {
    return v == null || v.isBlank() ? dflt : v;
  }
}
