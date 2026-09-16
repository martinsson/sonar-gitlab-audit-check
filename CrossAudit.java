///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Csv.java
//SOURCES NameMatcher.java
//SOURCES ProjectMapper.java

import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * Le croisement Sonar × GitLab, enfin écrit.
 *
 * Il était conçu depuis le début — GITLAB_ANALYSIS.md §6 en donne la méthode et
 * les quatre affirmations qu'il rend possibles — et le README le rangeait dans
 * « pas encore fait ». Il n'y était pas : aucun outil ne lisait les deux côtés,
 * aucun CSV ne portait une colonne des deux. Ce fichier est cette pièce
 * manquante, et rien d'autre.
 *
 * Trois principes, tous hérités de §6 :
 *
 *   ADDITIF        Il lit deux CSV déjà produits et n'appelle aucune API. On
 *                  peut le relancer, changer de seuil, refaire la jointure
 *                  autrement, sans repayer l'un ou l'autre audit — et sans
 *                  qu'un échec ici n'invalide quoi que ce soit là-bas.
 *
 *   ÉTIQUETÉ       Chaque appariement porte la méthode qui l'a produit et sa
 *                  confiance. Un rapprochement par ressemblance de nom n'a pas
 *                  à ressembler, dans un tableau, à une clé lue dans la CI.
 *
 *   JAMAIS PORTEUR Seules les jointures exactes et dérivées alimentent une
 *                  affirmation. Les suggestions sortent dans le CSV, marquées,
 *                  pour qu'un humain tranche — jamais dans un compte.
 *
 * Le taux d'appariement est lui-même un résultat : un taux bas ne dit pas que
 * l'outil a échoué, il dit que l'intégration GitLab↔SonarQube n'est pas
 * configurée, ce qui est la même classe de constat que les projets jamais
 * analysés.
 *
 * Usage :
 *   jbang SonarAuditCheck.java --csv projets.csv
 *   jbang GitlabActivityAudit.java --group mon/groupe --deep --out-dir ./audit
 *   jbang CrossAudit.java --sonar projets.csv --gitlab ./audit/pratiques.csv \
 *       --out croisement.csv
 */
@Command(name = "CrossAudit", mixinStandardHelpOptions = true,
        sortOptions = false, usageHelpAutoWidth = true,
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
            "La colonne qui fait la jointure est cle_sonar, écrite par",
            "GitlabActivityAudit --deep. Sans elle, seul le rapprochement par",
            "nom reste possible, et il ne sort qu'en suggestions.",
        })
public class CrossAudit implements Callable<Integer> {

    @Option(names = "--sonar", required = true,
            description = "CSV d'inventaire de SonarAuditCheck --csv")
    Path sonarCsv;

    @Option(names = "--gitlab", required = true,
            description = "pratiques.csv de GitlabActivityAudit --deep")
    Path gitlabCsv;

    @Option(names = "--out", description = "CSV du croisement (sinon : rien n'est écrit)")
    Path out;

    @Option(names = "--seuil-nom", defaultValue = "0.5",
            description = "score de ressemblance minimal d'une suggestion, 0–1 "
                    + "(défaut : ${DEFAULT-VALUE})")
    double nameThreshold;

    @Option(names = "--marge-nom", defaultValue = "0.1",
            description = "écart minimal entre le meilleur candidat et le suivant ; "
                    + "en dessous, aucun n'est proposé (défaut : ${DEFAULT-VALUE})")
    double nameMargin;

    @Option(names = "--report",
            description = "rapport JSON d'évaluation des méthodes d'appariement")
    Path report;

    @Option(names = "--examples", defaultValue = "15",
            description = "exemples par rubrique du rapport (défaut : ${DEFAULT-VALUE})")
    int examples;

    @Option(names = "--comma",
            description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
    boolean comma;

    @Option(names = "--color", defaultValue = "auto",
            description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
    String colorMode;

    public static void main(String[] args) {
        ConsoleOut.install();
        System.exit(new CommandLine(new CrossAudit()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        ConsoleOut.colorMode(colorMode);

        Csv.Table sonar = Csv.read(sonarCsv);
        sonar.require(sonarCsv, "SonarAuditCheck --csv", "key", "ncloc", "analysisDate");
        Csv.Table gitlab = Csv.read(gitlabCsv);
        gitlab.require(gitlabCsv, "GitlabActivityAudit --deep", "path", "commits_window");

        System.out.println(c("\nCroisement SonarQube × GitLab", BOLD));
        System.out.printf("  Côté Sonar   : %d projets (%s)%n", sonar.rows().size(), sonarCsv);
        System.out.printf("  Côté GitLab  : %d projets (%s)%n", gitlab.rows().size(), gitlabCsv);

        if (!gitlab.has("cle_sonar")) {
            System.out.println(c("\n  pratiques.csv ne porte pas de colonne cle_sonar.", YELLOW));
            System.out.println(c("  Elle vient de GitlabActivityAudit --deep : sans elle, la seule", YELLOW));
            System.out.println(c("  jointure possible est le rapprochement de noms, qui ne sort", YELLOW));
            System.out.println(c("  qu'en suggestions à confirmer. Relancer l'audit GitLab donne", YELLOW));
            System.out.println(c("  la jointure exacte.", YELLOW));
        }

        ProjectMapper.Result mapping = ProjectMapper.map(
                ProjectMapper.Spec.sonarGitlab(nameThreshold, nameMargin), gitlab, sonar);
        ProjectMapper.Report rep = ProjectMapper.evaluate(mapping, examples);
        List<Pair> pairs = mapping.matches().stream().map(Pair::new).toList();
        matchRate(pairs, sonar, rep);
        findings(pairs);
        if (out != null) write(mapping, pairs);
        if (report != null) {
            ProjectMapper.writeReport(rep, report);
            System.out.printf("  Rapport d'appariement : %s%n", report.toAbsolutePath());
        }
        return 0;
    }

    // ----------------------------------------------------------------------
    // La jointure — faite par ProjectMapper, préréglage Sonar × GitLab
    // ----------------------------------------------------------------------

    /**
     * Un projet GitLab et, s'il en a un, son homologue Sonar. L'appariement et
     * son évaluation vivent dans {@link ProjectMapper}, qui tourne aussi seul :
     * ce qui suit n'est que la lecture qu'en font les constats.
     */
    record Pair(ProjectMapper.Match m) {

        Csv.Row gl() { return m.left(); }

        Csv.Row sq() { return m.right(); }

        /** Ni suggestion, ni absence : les seules paires qui alimentent un constat. */
        boolean joined() { return m.joined(); }
    }

    // ----------------------------------------------------------------------
    // Le taux d'appariement, qui est un résultat et pas une note d'exécution
    // ----------------------------------------------------------------------

    private void matchRate(List<Pair> pairs, Csv.Table sonar, ProjectMapper.Report rep) {
        title("1. Appariement");
        System.out.println(c("  Méthodes appliquées dans l'ordre ; les suggestions de noms ne", DIM));
        System.out.println(c("  comptent dans aucun constat, elles sortent dans le CSV pour", DIM));
        System.out.println(c("  qu'un humain tranche.", DIM));
        ProjectMapper.print(rep);

        long joined = pairs.stream().filter(Pair::joined).count();
        if (joined < pairs.size() * 0.5) {
            System.out.println();
            for (String l : List.of(
                    "Moins d'un projet sur deux apparié. Ce n'est pas un échec de l'outil,",
                    "c'est le constat : l'intégration GitLab↔SonarQube n'est pas configurée,",
                    "et personne ne peut aujourd'hui répondre « ce dépôt est-il analysé ? »",
                    "sans le faire à la main. Même classe de trou de gouvernance que les",
                    "projets jamais analysés.")) {
                System.out.println(c("  " + l, YELLOW));
            }
        }

        long sonarUnmatched = sonar.rows().size() - pairs.stream()
                .filter(Pair::joined).map(p -> p.sq().str("key")).distinct().count();
        System.out.printf("%n  Projets Sonar sans dépôt GitLab apparié : %d%n", sonarUnmatched);
        System.out.println(c("    Attention au dénominateur : pratiques.csv ne contient que les", DIM));
        System.out.println(c("    projets sélectionnés par l'audit GitLab, pas tout le parc. Un", DIM));
        System.out.println(c("    projet Sonar non apparié peut simplement ne pas avoir été tiré.", DIM));
    }

    // ----------------------------------------------------------------------
    // Ce que ni l'un ni l'autre ne pouvait dire seul
    // ----------------------------------------------------------------------

    private void findings(List<Pair> pairs) {
        title("2. Ce qu'aucun des deux rapports ne pouvait dire seul");
        List<Pair> ok = pairs.stream().filter(Pair::joined).toList();

        // 1. Vivant dans GitLab, absent de Sonar. La distinction que le seul
        //    inventaire Sonar ne permet pas : un dépôt absent y est indiscernable
        //    d'un dépôt mort, et c'est l'inverse qui est intéressant.
        List<Pair> active = pairs.stream()
                .filter(p -> !p.joined())
                .filter(p -> num(p.gl(), "commits_window") != null
                        && num(p.gl(), "commits_window") >= 20)
                .sorted(Comparator.comparingDouble((Pair p) -> num(p.gl(), "commits_window")).reversed())
                .toList();
        finding("Actifs dans GitLab, absents de SonarQube", active.size(),
                "Vivants et non analysés. Séparés enfin des dépôts morts, qu'un "
                        + "inventaire\n    Sonar seul confond avec eux.",
                active, p -> "%s — %s commits".formatted(p.gl().str("path"),
                        p.gl().str("commits_window")));

        // 2. La CI dit qu'elle lance Sonar, et Sonar n'a rien. Vérifiable en une
        //    minute par n'importe qui, ce qui en fait le constat le plus utile
        //    du lot : soit le job échoue en silence, soit il pousse ailleurs.
        List<Pair> claimed = pairs.stream()
                .filter(p -> !p.joined())
                .filter(p -> p.gl().flag("ci_sonar"))
                .toList();
        finding("CI configurée pour Sonar, sans projet Sonar apparié", claimed.size(),
                "Le pipeline dit lancer une analyse ; rien ne la reçoit sous une clé\n"
                        + "    qu'on sache rattacher. Job en échec silencieux, ou clé "
                        + "qui ne correspond\n    à rien. Constat concret, vérifiable projet par projet.",
                claimed, p -> p.gl().str("path")
                        + (p.gl().str("cle_sonar").isEmpty() ? "" : " → " + p.gl().str("cle_sonar")));

        // 3. Dette qui se crée, revue qui ne l'arrête pas. Sonar dit que la dette
        //    arrive ; GitLab dit que rien ne la relit. Ni l'un ni l'autre ne peut
        //    l'affirmer seul, et c'est le signal « cette équipe a besoin d'aide ».
        List<Pair> unreviewed = ok.stream()
                .filter(p -> nz(p.sq(), "new_violations") > 0)
                .filter(p -> nz(p.gl(), "commits_window") >= 20)
                .filter(p -> nz(p.gl(), "mr_fusionnees") == 0)
                .sorted(Comparator.comparingDouble((Pair p) -> nz(p.sq(), "new_violations")).reversed())
                .toList();
        finding("Dette créée sur du code que personne n'a relu", unreviewed.size(),
                "Sonar voit des violations sur le code neuf ; GitLab voit ce code "
                        + "atteindre\n    la branche par défaut sans une seule MR fusionnée.",
                unreviewed, p -> "%s — %s violations neuves, %s commits, 0 MR".formatted(
                        p.gl().str("path"), p.sq().str("new_violations"),
                        p.gl().str("commits_window")));

        // 4. Sans test et sans revue : les deux filets à la fois. Aucun des deux
        //    rapports ne peut le dire, et c'est la seule intersection qui justifie
        //    d'intervenir avant que quelque chose casse.
        List<Pair> naked = ok.stream()
                .filter(p -> num(p.sq(), "coverage") != null && nz(p.sq(), "coverage") < 20)
                .filter(p -> nz(p.gl(), "commits_window") >= 20)
                .filter(p -> nz(p.gl(), "mr_fusionnees") == 0)
                .sorted(Comparator.comparingDouble((Pair p) -> nz(p.gl(), "commits_window")).reversed())
                .toList();
        finding("Ni tests ni revue, sur du code qui bouge", naked.size(),
                "Couverture sous 20 %, aucune MR fusionnée, activité soutenue. Les "
                        + "deux\n    filets manquent en même temps.",
                naked, p -> "%s — couverture %s %%, %s commits".formatted(
                        p.gl().str("path"), p.sq().str("coverage"), p.gl().str("commits_window")));

        // 5. Dette portée sans être créée. Le confirmer par une source
        //    indépendante, plutôt que de le déduire d'une date d'analyse ancienne
        //    — qui ne dit rien de ce qui se passe dans le dépôt.
        List<Pair> carrying = ok.stream()
                .filter(p -> nz(p.sq(), "sqale_index") > 0)
                .filter(p -> num(p.gl(), "commits_window") != null
                        && nz(p.gl(), "commits_window") == 0)
                .sorted(Comparator.comparingDouble((Pair p) -> nz(p.sq(), "sqale_index")).reversed())
                .toList();
        finding("Dette portée, pas créée", carrying.size(),
                "De la dette au bilan, zéro commit sur la fenêtre. Confirmé par le "
                        + "dépôt\n    lui-même, pas déduit d'une date d'analyse ancienne.",
                carrying, p -> "%s — %s min de dette, 0 commit".formatted(
                        p.gl().str("path"), p.sq().str("sqale_index")));

        // 6. Le cas qui ne se voit que depuis les deux côtés : des tests existent,
        //    et SonarQube n'en sait rien. Sans le compte de tests, « couverture 0 »
        //    et « pas de tests » sont la même cellule vide.
        List<Pair> unwired = ok.stream()
                .filter(p -> nz(p.sq(), "tests") > 0)
                .filter(p -> num(p.sq(), "coverage") != null && nz(p.sq(), "coverage") == 0)
                .toList();
        finding("Des tests tournent, la couverture n'arrive pas", unwired.size(),
                "Tests comptés par SonarQube, couverture à zéro : le rapport de "
                        + "couverture\n    n'est pas branché. Problème de tuyauterie CI, pas "
                        + "d'ingénierie — et les\n    deux se ressemblent tant qu'on ne "
                        + "regarde que le pourcentage.",
                unwired, p -> "%s — %s tests, couverture 0 %%".formatted(
                        p.gl().str("path"), p.sq().str("tests")));
    }

    /** Un constat, son effectif, sa lecture, et au plus cinq exemples nommés. */
    private void finding(String title, int n, String reading,
                         List<Pair> examples, java.util.function.Function<Pair, String> line) {
        System.out.printf("%n  %s : %s%n", title,
                c(String.valueOf(n), n > 0 ? YELLOW : GREEN));
        if (n == 0) return;
        System.out.println(c("    " + reading, DIM));
        examples.stream().limit(5).forEach(p -> System.out.println("      " + line.apply(p)));
        if (n > 5) System.out.printf("      … et %d autres, dans le CSV.%n", n - 5);
    }

    // ----------------------------------------------------------------------
    // CSV
    // ----------------------------------------------------------------------

    /**
     * Les colonnes des deux côtés, plus la méthode d'appariement. Le préfixe dit
     * d'où vient chaque valeur : sans lui, {@code coverage} et {@code commits}
     * dans la même ligne laissent croire à une mesure unique alors que ce sont
     * deux systèmes, deux dates et deux définitions.
     */
    private static final List<String> GL_COLUMNS = List.of(
            "path", "bucket", "commits_window", "authors_window", "mr_fusionnees",
            "branche_protegee", "taux_succes", "ci_sonar", "ci_securite",
            "cle_sonar", "source_cle_sonar", "id");

    private static final List<String> SQ_COLUMNS = List.of(
            "key", "name", "analysisDate", "days_since_analysis", "ncloc",
            "coverage", "new_coverage", "tests", "test_failures", "lines_to_cover",
            "uncovered_lines", "sqale_index", "sqale_debt_ratio",
            "bugs", "vulnerabilities", "code_smells",
            "new_lines", "new_violations", "alert_status",
            "alm", "alm_repository", "liens");

    private void write(ProjectMapper.Result mapping, List<Pair> pairs) throws IOException {
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);

        List<String> header = new ArrayList<>(List.of("methode_jointure", "confiance"));
        header.addAll(ProjectMapper.detailHeader(mapping));
        GL_COLUMNS.forEach(c -> header.add("gl_" + c));
        SQ_COLUMNS.forEach(c -> header.add("sq_" + c));

        try (CSVWriter w = Csv.writer(out, comma)) {
            w.writeNext(header.toArray(String[]::new));
            for (Pair p : pairs) {
                List<String> row = new ArrayList<>(List.of(p.m().label(),
                        p.m().confidence().name().toLowerCase(Locale.ROOT)));
                row.addAll(ProjectMapper.detailCells(mapping, p.m()));
                GL_COLUMNS.forEach(col -> row.add(p.gl().str(col)));
                SQ_COLUMNS.forEach(col -> row.add(p.sq() == null ? "" : p.sq().str(col)));
                w.writeNext(row.toArray(String[]::new));
            }
        }
        System.out.printf("%n  Croisement : %d lignes → %s%n", pairs.size(), out.toAbsolutePath());
        System.out.println(c(Csv.openingHint(out, comma), DIM));
        System.out.println(c("    Une ligne par projet GitLab, appariée ou non. "
                + "Filtrer sur confiance", DIM));
        System.out.println(c("    = suggestion donne la liste des rapprochements "
                + "qu'un humain doit", DIM));
        System.out.println(c("    confirmer ; ils ne comptent dans aucun des constats "
                + "ci-dessus.", DIM));
    }

    // ----------------------------------------------------------------------
    // Utilitaires
    // ----------------------------------------------------------------------

    static final String BOLD = "\033[1m", DIM = "\033[2m";
    static final String GREEN = "\033[32m", YELLOW = "\033[33m";

    static String c(String text, String color) {
        return ConsoleOut.color(text, color);
    }

    static void title(String text) {
        System.out.println();
        System.out.println(c(text, BOLD));
        System.out.println(c("-".repeat(Math.min(text.length(), 72)), DIM));
    }

    /** {@code null} quand la cellule est vide : absent n'est pas zéro. */
    static Double num(Csv.Row r, String column) {
        return r == null ? null : r.num(column);
    }

    /** La même, ramenée à zéro là où un seuil a besoin d'un nombre. */
    static double nz(Csv.Row r, String column) {
        Double d = num(r, column);
        return d == null ? 0 : d;
    }

    static String pct(long n, long total) {
        return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
    }
}
