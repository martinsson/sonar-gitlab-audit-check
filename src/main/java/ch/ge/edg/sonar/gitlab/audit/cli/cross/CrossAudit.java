package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.cli.shared.NamespaceMappingOption;
import ch.ge.edg.sonar.gitlab.audit.cli.shared.StyleOptions;
import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabPractices;
import ch.ge.edg.sonar.gitlab.audit.exchange.SonarInventory;
import ch.ge.edg.sonar.gitlab.audit.matching.Evaluation;
import ch.ge.edg.sonar.gitlab.audit.matching.MappingFile;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import ch.ge.edg.sonar.gitlab.audit.matching.Spec;
import ch.ge.edg.sonar.gitlab.audit.matching.report.MappingFilePrinter;
import ch.ge.edg.sonar.gitlab.audit.runtime.Host;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

/**
 * Le croisement Sonar × GitLab, enfin écrit.
 *
 * <p>Il était conçu depuis le début — GITLAB_ANALYSIS.md §6 en donne la méthode et les quatre
 * affirmations qu'il rend possibles — et le README le rangeait dans « pas encore fait ». Il n'y
 * était pas : aucun outil ne lisait les deux côtés, aucun CSV ne portait une colonne des deux. Ce
 * fichier est cette pièce manquante, et rien d'autre.
 *
 * <p>Trois principes, tous hérités de §6 :
 *
 * <p>ADDITIF Il lit deux CSV déjà produits et n'appelle aucune API. On peut le relancer, changer de
 * seuil, refaire la jointure autrement, sans repayer l'un ou l'autre audit — et sans qu'un échec
 * ici n'invalide quoi que ce soit là-bas.
 *
 * <p>ÉTIQUETÉ Chaque appariement porte la méthode qui l'a produit et sa confiance. Un rapprochement
 * par ressemblance de nom n'a pas à ressembler, dans un tableau, à une clé lue dans la CI.
 *
 * <p>JAMAIS PORTEUR Seules les jointures exactes et dérivées alimentent une affirmation. Les
 * suggestions sortent dans le CSV, marquées, pour qu'un humain tranche — jamais dans un compte.
 *
 * <p>Le taux d'appariement est lui-même un résultat : un taux bas ne dit pas que l'outil a échoué,
 * il dit que l'intégration GitLab↔SonarQube n'est pas configurée, ce qui est la même classe de
 * constat que les projets jamais analysés.
 *
 * <p>Usage : jbang SonarAuditCheck.java --csv projets.csv jbang GitlabActivityAudit.java --group
 * mon/groupe --deep --out-dir ./audit jbang CrossAudit.java --sonar projets.csv --gitlab
 * ./audit/pratiques.csv \ --out croisement.csv
 */
@Command(
    name = "CrossAudit",
    mixinStandardHelpOptions = true,
    sortOptions = false,
    usageHelpAutoWidth = true,
    description = "Croise l'inventaire SonarQube et les pratiques GitLab.",
    synopsisHeading = "",
    customSynopsis = {
      "Usage : CrossAudit --sonar <projets.csv> --gitlab <pratiques.csv>",
      "                   [--out <croisement.csv>]",
    },
    footer = {
      "",
      "Les deux fichiers d'entrée viennent des deux autres outils :",
      "",
      "  1. SonarAuditCheck --csv projets.csv",
      "  2. GitlabActivityAudit --group mon/groupe --deep --out-dir ./audit",
      "  3. CrossAudit --sonar projets.csv --gitlab ./audit/pratiques.csv \\",
      "         --out ./audit/croisement.csv",
      "",
      "Aucun appel réseau : tout se lit dans les deux CSV. Relancer coûte",
      "zéro appel, donc les seuils se règlent sans repayer un audit.",
      "",
      "Avec l'inventaire GitLab complet (inventaire.csv, écrit à côté de",
      "pratiques.csv), chaque projet Sonar est aussi cherché dans TOUT GitLab,",
      "pas seulement dans la sélection : sonar-gitlab.csv, une ligne par",
      "projet Sonar.",
      "",
      "Les colonnes qui font la jointure sont cle_sonar (clé lue dans la",
      "CI) et cle_pom (groupId:artifactId du pom), écrites par",
      "GitlabActivityAudit --deep. Sans elles, seul le rapprochement par",
      "nom reste possible, et il ne sort qu'en suggestions.",
    })
public class CrossAudit implements Callable<Integer> {

  @Option(
      names = "--sonar",
      required = true,
      description = "CSV d'inventaire de SonarAuditCheck --csv")
  Path sonarCsv;

  @Option(
      names = "--gitlab",
      required = true,
      description = "pratiques.csv de GitlabActivityAudit --deep")
  Path gitlabCsv;

  @Option(names = "--out", description = "CSV du croisement (sinon : rien n'est écrit)")
  @Nullable Path out;

  @Option(
      names = "--inventaire",
      description =
          "inventaire.csv de GitlabActivityAudit (tout GitLab) : chaque projet "
              + "Sonar y est cherché (défaut : inventaire.csv à côté de --gitlab, s'il y est)")
  @Nullable Path inventoryCsv;

  @Option(
      names = "--sonar-gitlab",
      description =
          "CSV de chaque projet Sonar contre tout GitLab "
              + "(défaut : sonar-gitlab.csv à côté de --out, sinon de --gitlab)")
  @Nullable Path sonarGitlabCsv;

  @Option(
      names = "--appariement",
      description =
          "appariement à reprendre et à compléter "
              + "(défaut : appariement.csv à côté de --gitlab)")
  @Nullable Path mappingFile;

  @Option(
      names = "--reapparier",
      description =
          "recalculer les paires exact/derived de l'appariement "
              + "(les lignes manuel et rejete restent)")
  boolean recompute;

  @Option(
      names = "--seuil-nom",
      defaultValue = "0.5",
      description =
          "score de ressemblance minimal d'une suggestion, 0–1 " + "(défaut : ${DEFAULT-VALUE})")
  double nameThreshold;

  @Option(
      names = "--marge-nom",
      defaultValue = "0.1",
      description =
          "écart minimal entre le meilleur candidat et le suivant ; "
              + "en dessous, aucun n'est proposé (défaut : ${DEFAULT-VALUE})")
  double nameMargin;

  @Option(names = "--report", description = "rapport JSON d'évaluation des méthodes d'appariement")
  @Nullable Path report;

  @Option(
      names = "--examples",
      defaultValue = "15",
      description = "exemples par rubrique du rapport (défaut : ${DEFAULT-VALUE})")
  int examples;

  @Mixin StyleOptions style = new StyleOptions();

  @Mixin NamespaceMappingOption namespaceMapping = new NamespaceMappingOption();

  private final Host host;

  CrossAudit(Host host) {
    this.host = host;
  }

  public static void main(String[] args) {
    Host host = Host.system();
    System.exit(host.commandLine(new CrossAudit(host)).execute(args));
  }

  @Override
  public Integer call() throws Exception {
    Console stdout = host.console(style.colorMode());
    namespaceMapping.load(host.workDir(), stdout.err());
    CrossReport console = new CrossReport(stdout);
    // required = true : picocli refuse la ligne de commande avant d'appeler call().
    Path sonarFile = sonarCsv;
    Path gitlabFile = gitlabCsv;

    Csv.Table sonar = Csv.read(sonarFile);
    sonar.require(sonarFile, "SonarAuditCheck --csv", SonarInventory.required());
    Csv.Table gitlab = Csv.read(gitlabFile);
    gitlab.require(gitlabFile, "GitlabActivityAudit --deep", GitlabPractices.required());
    console.inputs(sonar, sonarFile, gitlab, gitlabFile);

    // L'appariement est repris d'un lancement à l'autre : les paires déjà
    // trouvées ne bougent plus, et une correction saisie à la main
    // (manuel, rejete) survit aux audits suivants.
    Path file =
        mappingFile != null
            ? mappingFile
            : gitlabFile.toAbsolutePath().resolveSibling("appariement.csv");
    // Lu avant que l'appariement ne soit réécrit : un rejet ne garde
    // ensuite que son chemin, et la passe sur tout GitLab doit le respecter.
    SonarToGitlab.Rejections rejected = SonarToGitlab.Rejections.read(file);
    Result mapping =
        MappingFilePrinter.mapAndKeep(
            Spec.sonarGitlab(nameThreshold, nameMargin),
            gitlab,
            sonar,
            new MappingFile(file, style.comma(), recompute),
            stdout);
    Report rep = Evaluation.evaluate(mapping, examples);
    List<CrossPair> pairs = mapping.matches().stream().map(CrossPair::new).toList();
    WidePass widePass = widePass();
    SonarToGitlab.Result wide = widePass.run(sonar, gitlabFile, mapping, rejected, console);
    console.matchRate(pairs, sonar, rep, wide != null);
    if (wide != null) widePass.write(wide, gitlabFile, console, stdout);
    new CrossFindings(stdout).print(pairs, wide == null ? Set.of() : wide.joinedPaths());
    if (out != null) CrossCsv.write(mapping, pairs, out, style.comma(), stdout);
    if (report != null) {
      rep.writeTo(report);
      console.mappingReport(report);
    }
    return 0;
  }

  /** La passe sur tout GitLab telle que les options la décrivent. */
  private WidePass widePass() {
    return new WidePass(
        inventoryCsv,
        sonarGitlabCsv,
        out,
        style.comma(),
        new SonarToGitlab.NameThresholds(nameThreshold, nameMargin));
  }
}
