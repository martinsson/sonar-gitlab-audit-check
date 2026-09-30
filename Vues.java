///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Csv.java

import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Des extractions de croisement.csv, rien de plus.
 *
 * Aucun appel réseau, aucune jointure, aucune mesure recalculée : chaque vue
 * choisit des colonnes et un ordre de lignes dans le fichier que CrossAudit a
 * déjà écrit. Une vue ne peut donc pas contredire le croisement, et on les
 * retouche — c'est un prototype — sans rien relancer d'autre.
 *
 * La seule chose ajoutée est la colonne {@code signaux} de la vue dérive, lue
 * dans les colonnes présentes : des raisons nommées plutôt qu'un score, parce
 * qu'un score cache laquelle des raisons pèse, et c'est elle qui dit quoi faire.
 *
 * Usage :
 *   jbang Vues.java --in ./audit/croisement.csv
 */
@Command(name = "Vues", mixinStandardHelpOptions = true,
        sortOptions = false, usageHelpAutoWidth = true,
        description = "Extrait des vues de croisement.csv (colonnes choisies, lignes triées).",
        footer = {
            "",
            "Écrit, à côté du fichier lu :",
            "  <nom>-essentiel.csv  toutes les colonnes, les importantes d'abord",
            "  <nom>-derive.csv     projets appariés, triés du plus inquiétant au moins",
            "",
            "Aucun appel réseau. Relancer ne coûte rien.",
        })
public class Vues implements Callable<Integer> {

    @Option(names = "--in", required = true, description = "croisement.csv de CrossAudit --out")
    Path in;

    @Option(names = "--comma",
            description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
    boolean comma;

    @Option(names = "--color", defaultValue = "auto",
            description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
    String colorMode;

    public static void main(String[] args) {
        ConsoleOut.install();
        System.exit(new CommandLine(new Vues()).execute(args));
    }

    /** Tout le croisement, ces colonnes d'abord : ce qu'on lit avant de défiler. */
    static final List<String> ESSENTIAL_FIRST = List.of(
            "projet", "confiance",
            "sq_violations_kloc_pente_pct_mois", "issues_par_kloc_modifie",
            "sq_alert_status", "sq_analysisDate", "sq_days_since_analysis",
            "gl_commits_window", "gl_lignes_modifiees", "gl_authors_window",
            "gl_mr_fusionnees", "gl_auto_merge", "gl_ci_securite",
            "sq_ncloc", "sq_sqale_debt_ratio", "sq_coverage", "sq_new_coverage");

    /**
     * Détecter un projet qui se dégrade : la dérivée d'abord, puis ce qui dit si
     * elle est crédible, le travail auquel elle se rapporte, et les filets qui
     * auraient dû l'arrêter.
     */
    static final List<String> DRIFT = List.of(
            "projet", "signaux",
            // La dérivée
            "sq_violations_kloc_pente_pct_mois", "sq_dette_ratio_pente_pct_mois",
            "sq_violations_pente_mois", "issues_par_kloc_modifie", "issues_par_commit",
            // Sa crédibilité
            "sq_tendance_analyses", "sq_tendance_jours",
            "sq_analysisDate", "sq_days_since_analysis",
            // Le travail qu'elle rapporte
            "gl_commits_window", "gl_lignes_modifiees", "gl_authors_window",
            // Les filets
            "gl_mr_fusionnees", "gl_auto_merge", "gl_part_approuvee",
            "sq_new_violations", "sq_new_lines", "sq_new_coverage",
            "gl_ci_securite", "sq_alert_status", "gl_taux_succes", "gl_rouge_non_resolu",
            // L'échelle
            "sq_ncloc", "sq_sqale_debt_ratio",
            "confiance");

    @Override
    public Integer call() throws IOException {
        ConsoleOut.colorMode(colorMode);
        Csv.Table t = Csv.read(in);
        t.require(in, "CrossAudit --out", "projet", "confiance");

        List<String> header = t.index().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
        List<String> essentialCols = new ArrayList<>(new LinkedHashSet<>(concat(
                ESSENTIAL_FIRST.stream().filter(t::has).toList(), header)));
        Path essential = sibling(in, "essentiel");
        write(essential, essentialCols, t.rows());

        // Appariés seulement : sans côté Sonar, il n'y a pas de pente à lire.
        List<Csv.Row> joined = new ArrayList<>(t.rows().stream()
                .filter(r -> Set.of("exact", "derived", "manuel").contains(r.str("confiance")))
                .toList());
        joined.sort(Comparator.comparingInt((Csv.Row r) -> signals(r).size()).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (Csv.Row r) -> orMin(r.num("sq_violations_kloc_pente_pct_mois"))).reversed()));
        Path drift = sibling(in, "derive");
        write(drift, DRIFT, joined);

        List<String> missing = DRIFT.stream()
                .filter(c -> !c.equals("signaux") && !t.has(c)).toList();
        System.out.printf("  Vue essentiel : %d lignes → %s%n", t.rows().size(), essential.toAbsolutePath());
        System.out.printf("  Vue dérive    : %d projets appariés → %s%n", joined.size(), drift.toAbsolutePath());
        System.out.println(ConsoleOut.color(
                "    Triée par nombre de signaux, puis par pente de la densité d'issues.", DIM));
        if (!missing.isEmpty()) {
            System.out.println(ConsoleOut.color(("  Colonnes absentes du croisement, laissées vides : %s%n"
                    + "    Un croisement plus ancien : relancer les trois outils les remplit.")
                    .formatted(String.join(", ", missing)), YELLOW));
        }
        return 0;
    }

    /**
     * Les raisons pour lesquelles un projet mérite qu'on le regarde. Chaque seuil
     * est un choix de prototype, à discuter, pas une norme.
     */
    static List<String> signals(Csv.Row r) {
        List<String> out = new ArrayList<>();
        Double density = r.num("sq_violations_kloc_pente_pct_mois");
        if (density != null && density > 2) out.add("densité en hausse");
        Double perKloc = r.num("issues_par_kloc_modifie");
        if (perKloc != null && perKloc > 0) out.add("modifs ajoutent des issues");
        Double newLines = r.num("sq_new_lines"), newIssues = r.num("sq_new_violations");
        if (newLines != null && newLines >= 200 && newIssues != null
                && newIssues / newLines * 1000 > 10) out.add("code neuf > 10 issues/kLOC");
        Double newCov = r.num("sq_new_coverage");
        if (newCov != null && newCov < 50) out.add("code neuf < 50 % couvert");
        double commits = nz(r, "gl_commits_window"), merged = nz(r, "gl_mr_fusionnees");
        if (commits >= 20 && merged == 0) out.add("aucune revue");
        else if (merged >= 5 && nz(r, "gl_auto_merge") / merged > 0.5) out.add("auto-merge > 50 %");
        // Absente du fichier n'est pas « false » : on ne l'affirme que lue.
        if (r.str("gl_ci_securite").equalsIgnoreCase("false")) out.add("sans appsec");
        if (r.str("sq_alert_status").equals("ERROR")) out.add("quality gate rouge");
        Double age = r.num("sq_days_since_analysis");
        if (commits >= 20 && age != null && age > 30) out.add("analyse périmée");
        return out;
    }

    private void write(Path file, List<String> columns, List<Csv.Row> rows) throws IOException {
        try (CSVWriter w = Csv.writer(file, comma)) {
            w.writeNext(columns.toArray(String[]::new));
            for (Csv.Row r : rows) {
                w.writeNext(columns.stream()
                        .map(c -> c.equals("signaux") ? String.join(", ", signals(r)) : r.str(c))
                        .toArray(String[]::new));
            }
        }
    }

    /** croisement.csv → croisement-derive.csv, à côté. */
    static Path sibling(Path file, String suffix) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : ".csv";
        return file.resolveSibling(base + "-" + suffix + ext);
    }

    static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    static double nz(Csv.Row r, String column) {
        Double d = r.num(column);
        return d == null ? 0 : d;
    }

    /** Vide trié en dernier, jamais lu comme zéro. */
    static double orMin(Double d) {
        return d == null ? -Double.MAX_VALUE : d;
    }

    static final String DIM = "\033[2m", YELLOW = "\033[33m";
}
