///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Étape 1 du classement : uniquement du post-traitement du CSV d'inventaire.
 * Aucun appel API — donc rejouable à volonté, et sans coût.
 *
 * La démarche est décrite dans ANALYSIS.md. En résumé :
 *
 *   1. Séparer les cohortes. Jamais analysé et obsolète ne se classent pas :
 *      ce sont des constats de gouvernance, pas des mesures de qualité.
 *   2. Classer les seuls projets actifs, par percentile *à taille comparable*,
 *      sur quatre signaux orientés vélocité plutôt que niveau de dette.
 *   3. Une métrique absente est retirée de la moyenne, jamais remplacée par 0.
 *
 * Usage :
 *   jbang SonarAuditCheck.java --csv projets.csv     # produit l'inventaire
 *   jbang SonarRank.java --in projets.csv            # produit le classement
 */
@Command(name = "SonarRank", mixinStandardHelpOptions = true,
        description = "Classement de première passe à partir du CSV d'inventaire.")
public class SonarRank implements Callable<Integer> {

    // ----------------------------------------------------------------------
    // Options
    // ----------------------------------------------------------------------

    @Option(names = "--in", required = true,
            description = "CSV d'inventaire produit par SonarAuditCheck --csv")
    Path in;

    @Option(names = "--out", defaultValue = "classement.csv",
            description = "CSV de sortie (défaut : ${DEFAULT-VALUE})")
    Path out;

    @Option(names = "--stale-days", defaultValue = "90",
            description = "seuil d'obsolescence (défaut : ${DEFAULT-VALUE})")
    int staleDays;

    @Option(names = "--abandoned-days", defaultValue = "365",
            description = "au-delà, un projet obsolète est dormant (défaut : ${DEFAULT-VALUE})")
    int abandonedDays;

    @Option(names = "--min-ncloc", defaultValue = "500",
            description = "en deçà, aucun ratio n'a de sens (défaut : ${DEFAULT-VALUE})")
    int minNcloc;

    @Option(names = "--top-percent", defaultValue = "10",
            description = "part de chaque strate retenue (défaut : ${DEFAULT-VALUE})")
    int topPercent;

    @Option(names = "--min-signals", defaultValue = "2",
            description = "signaux minimum pour retenir un score (défaut : ${DEFAULT-VALUE})")
    int minSignals;

    @Option(names = "--comma",
            description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
    boolean comma;

    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true,
                StandardCharsets.UTF_8));
        // Un CSV illisible est le cas d'erreur courant ici : une trace de pile
        // n'y apprend rien à personne, la ligne de message si.
        int code = new CommandLine(new SonarRank())
                .setExecutionExceptionHandler((e, cmd, parse) -> {
                    if (e instanceof IOException) {
                        cmd.getErr().println("Lecture impossible : " + e.getMessage());
                        return 2;
                    }
                    throw e;
                })
                .execute(args);
        System.exit(code);
    }

    // ----------------------------------------------------------------------
    // Orchestration
    // ----------------------------------------------------------------------

    @Override
    public Integer call() throws Exception {
        List<Row> rows = readInventory(in);
        if (rows.isEmpty()) {
            System.err.println("Aucune ligne exploitable dans " + in);
            return 1;
        }

        Map<Cohort, List<Row>> cohorts = splitCohorts(rows);
        reportGovernance(rows, cohorts);

        List<Scored> velocity = rankActive(cohorts.get(Cohort.ACTIVE));
        List<Scored> carrying = rankCarrying(cohorts.get(Cohort.ACTIVE));
        List<Scored> abandoned = rankAbandoned(cohorts.get(Cohort.STALE));

        reportVelocity(velocity);
        reportSideLists(carrying, abandoned);

        write(velocity, carrying, abandoned);
        return 0;
    }

    // ----------------------------------------------------------------------
    // Lecture
    // ----------------------------------------------------------------------

    /**
     * Le CSV peut arriver au format machine (virgule) ou Excel (point-virgule,
     * précédé d'un BOM) : on renifle l'en-tête plutôt que d'imposer un format,
     * parce qu'un aller-retour par Excel est le trajet le plus probable de ce
     * fichier.
     */
    private static List<Row> readInventory(Path path) throws IOException, CsvException {
        String head;
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            head = r.readLine();
        }
        if (head == null) return List.of();
        char sep = (count(head, ';') > count(head, ',')) ? ';' : ',';

        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             var csv = new CSVReaderBuilder(r)
                     .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                     .build()) {
            List<String[]> all = csv.readAll();
            if (all.isEmpty()) return List.of();

            String[] header = all.get(0);
            header[0] = stripBom(header[0]);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.length; i++) index.put(header[i].trim(), i);

            for (String required : List.of("key", "ncloc", "analysisDate")) {
                if (!index.containsKey(required)) {
                    throw new IOException("colonne '" + required + "' absente de " + path
                            + " — ce fichier vient-il bien de --csv ?");
                }
            }

            List<Row> rows = new ArrayList<>();
            for (String[] cells : all.subList(1, all.size())) {
                if (cells.length == 0 || (cells.length == 1 && cells[0].isBlank())) continue;
                rows.add(new Row(index, cells));
            }
            return rows;
        }
    }

    /** Une ligne du CSV. Les valeurs restent des String : vide n'est pas zéro. */
    record Row(Map<String, Integer> index, String[] cells) {

        String str(String column) {
            Integer i = index.get(column);
            if (i == null || i >= cells.length || cells[i] == null) return "";
            return cells[i].trim();
        }

        /** {@code null} si la métrique est absente — jamais 0. */
        Double num(String column) {
            String s = str(column);
            if (s.isEmpty()) return null;
            // Un aller-retour par Excel en locale française transforme 12.5 en 12,5.
            if (!s.contains(".") && s.contains(",")) s = s.replace(',', '.');
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        String key() { return str("key"); }

        String name() { return str("name"); }

        double ncloc() {
            Double n = num("ncloc");
            return n == null ? 0 : n;
        }

        /** {@code null} = jamais analysé. */
        Integer daysSinceAnalysis() {
            Double d = num("days_since_analysis");
            return (d == null || str("analysisDate").isEmpty()) ? null : (int) (double) d;
        }
    }

    // ----------------------------------------------------------------------
    // 1. Cohortes
    // ----------------------------------------------------------------------

    enum Cohort { NEVER, STALE, ACTIVE }

    private Map<Cohort, List<Row>> splitCohorts(List<Row> rows) {
        Map<Cohort, List<Row>> byCohort = new EnumMap<>(Cohort.class);
        for (Cohort c : Cohort.values()) byCohort.put(c, new ArrayList<>());
        for (Row r : rows) {
            Integer days = r.daysSinceAnalysis();
            if (days == null) byCohort.get(Cohort.NEVER).add(r);
            else if (days > staleDays) byCohort.get(Cohort.STALE).add(r);
            else byCohort.get(Cohort.ACTIVE).add(r);
        }
        return byCohort;
    }

    private void reportGovernance(List<Row> all, Map<Cohort, List<Row>> cohorts) {
        title("1. Gouvernance (constats qui ne demandent aucun score)");

        int total = all.size();
        int never = cohorts.get(Cohort.NEVER).size();
        int stale = cohorts.get(Cohort.STALE).size();
        int active = cohorts.get(Cohort.ACTIVE).size();

        System.out.printf("  Projets dans l'inventaire       : %d%n", total);
        System.out.printf("  Jamais analysés                 : %s%n", pct(never, total, YELLOW));
        System.out.printf("  Analysés, obsolètes (> %d j)    : %s%n", staleDays,
                pct(stale, total, YELLOW));
        System.out.printf("  Actifs (< %d j)                 : %s%n", staleDays,
                pct(active, total, ""));

        // Le point aveugle de tout tri par couverture : ces projets n'y figurent pas.
        long noCoverageAll = all.stream().filter(r -> r.num("coverage") == null).count();
        long noCoverageActive = cohorts.get(Cohort.ACTIVE).stream()
                .filter(r -> r.num("coverage") == null).count();
        System.out.println();
        System.out.printf("  Sans donnée de couverture       : %d sur %d%n", noCoverageAll, total);
        System.out.printf("    dont projets actifs           : %s%n",
                pct((int) noCoverageActive, active, RED));
        if (noCoverageActive > 0) {
            // Le bloc texte retire l'indentation commune : le décalage de la
            // fermeture est ce qui donne les quatre espaces d'alignement.
            System.out.println(c("""
                    Un projet analysé obtient coverage=0.0 dès que son analyseur
                    rapporte des lignes couvrables. Une couverture *absente* sur un
                    projet actif signale donc un langage sans instrumentation, ou un
                    rapport jamais branché sur le scanner — pas du code non testé.
                    C'est un constat d'outillage, et il ne se classe pas.\
                """, DIM));
        }

        System.out.printf("%n  → Population classable          : %d projets actifs.%n", active);
    }

    // ----------------------------------------------------------------------
    // 2. Signaux et strates
    // ----------------------------------------------------------------------

    /**
     * Quatre signaux, tous déjà dans le CSV. La sévérité va toujours dans le
     * même sens — plus c'est haut, pire c'est — la couverture est donc inversée.
     *
     * Un seul écart au poids uniforme : {@code sqale_debt_ratio} mesure le
     * niveau de dette, pas sa vitesse d'accumulation, et à poids plein il ramène
     * le classement vers les gros projets anciens — exactement ce que cette
     * démarche cherche à éviter. Les trois autres restent à égalité tant que
     * personne n'a lu une shortlist et dit lequel l'a trompé.
     */
    enum Signal {
        INJECTION("issues_neuves_par_kloc", 1.0, r -> {
            Double v = r.num("new_violations");
            Double l = r.num("new_lines");
            if (v == null || l == null || l <= 0) return null;
            return v / l * 1000;
        }),
        NEW_COVERAGE("couverture_code_neuf", 1.0, r -> {
            Double v = r.num("new_coverage");
            return v == null ? null : -v;                    // moins couvert = pire
        }),
        NEW_DUPLICATION("duplication_code_neuf", 1.0,
                r -> r.num("new_duplicated_lines_density")),
        DEBT_RATIO("ratio_dette", 0.5, r -> r.num("sqale_debt_ratio"));

        final String column;
        final double weight;
        final Function<Row, Double> severity;

        Signal(String column, double weight, Function<Row, Double> severity) {
            this.column = column;
            this.weight = weight;
            this.severity = severity;
        }
    }

    /** Les seuils sont arbitraires ; ce qui compte est de ne comparer qu'à taille comparable. */
    enum Bucket {
        XS("< 1k lignes", 0, 1_000),
        S("1k - 10k", 1_000, 10_000),
        M("10k - 100k", 10_000, 100_000),
        L("> 100k", 100_000, Integer.MAX_VALUE);

        final String label;
        final int min;
        final int max;

        Bucket(String label, int min, int max) {
            this.label = label;
            this.min = min;
            this.max = max;
        }

        static Bucket of(double ncloc) {
            for (Bucket b : values()) {
                if (ncloc >= b.min && ncloc < b.max) return b;
            }
            return L;
        }
    }

    /** Pourquoi un projet actif ne reçoit pas de score de vélocité. */
    enum Exclusion {
        NONE(""),
        TOO_SMALL("trop petit"),
        NO_NEW_CODE("pas de code neuf sur la fenêtre"),
        TOO_FEW_SIGNALS("trop peu de signaux");

        final String label;

        Exclusion(String label) { this.label = label; }
    }

    static final class Scored {
        final Row row;
        final Bucket bucket;
        final Map<Signal, Double> severities = new EnumMap<>(Signal.class);
        final Map<Signal, Double> percentiles = new EnumMap<>(Signal.class);
        Exclusion exclusion = Exclusion.NONE;
        double score;
        boolean shortlisted;

        Scored(Row row) {
            this.row = row;
            this.bucket = Bucket.of(row.ncloc());
        }

        int signalsPresent() { return percentiles.size(); }
    }

    // ----------------------------------------------------------------------
    // 3. Classement vélocité
    // ----------------------------------------------------------------------

    private List<Scored> rankActive(List<Row> active) {
        List<Scored> all = active.stream().map(Scored::new).toList();

        // Une exclusion ne pénalise pas le score : elle retire du classement.
        for (Scored s : all) {
            Double newLines = s.row.num("new_lines");
            if (s.row.ncloc() < minNcloc) s.exclusion = Exclusion.TOO_SMALL;
            else if (newLines == null || newLines <= 0) s.exclusion = Exclusion.NO_NEW_CODE;
        }

        List<Scored> candidates = all.stream()
                .filter(s -> s.exclusion == Exclusion.NONE).toList();

        for (Scored s : candidates) {
            for (Signal sig : Signal.values()) {
                Double v = sig.severity.apply(s.row);
                if (v != null) s.severities.put(sig, v);
            }
        }

        // Percentiles calculés strate par strate, signal par signal.
        Map<Bucket, List<Scored>> byBucket = new EnumMap<>(Bucket.class);
        for (Scored s : candidates) {
            byBucket.computeIfAbsent(s.bucket, b -> new ArrayList<>()).add(s);
        }

        for (List<Scored> stratum : byBucket.values()) {
            for (Signal sig : Signal.values()) {
                List<Double> values = stratum.stream()
                        .map(s -> s.severities.get(sig))
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList();
                if (values.isEmpty()) continue;
                for (Scored s : stratum) {
                    Double v = s.severities.get(sig);
                    if (v != null) s.percentiles.put(sig, percentileOf(v, values));
                }
            }
        }

        for (Scored s : candidates) {
            if (s.signalsPresent() < minSignals) {
                s.exclusion = Exclusion.TOO_FEW_SIGNALS;
                continue;
            }
            double num = 0;
            double den = 0;
            for (Map.Entry<Signal, Double> e : s.percentiles.entrySet()) {
                num += e.getValue() * e.getKey().weight;
                den += e.getKey().weight;
            }
            s.score = num / den;
        }

        List<Scored> ranked = candidates.stream()
                .filter(s -> s.exclusion == Exclusion.NONE)
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .collect(Collectors.toCollection(ArrayList::new));

        // Décile haut de *chaque* strate : la shortlist reste répartie sur les tailles.
        Map<Bucket, Integer> taken = new EnumMap<>(Bucket.class);
        for (Scored s : ranked) {
            int stratumSize = byBucket.getOrDefault(s.bucket, List.of()).size();
            int quota = Math.max(1, (int) Math.round(stratumSize * topPercent / 100.0));
            int used = taken.getOrDefault(s.bucket, 0);
            if (used < quota) {
                s.shortlisted = true;
                taken.put(s.bucket, used + 1);
            }
        }

        List<Scored> result = new ArrayList<>(ranked);
        all.stream().filter(s -> s.exclusion != Exclusion.NONE).forEach(result::add);
        return result;
    }

    /**
     * Percentile par rang moyen : la part de la strate que ce projet dépasse en
     * sévérité. Robuste aux valeurs extrêmes, et sans unité à réconcilier entre
     * des signaux qui n'en partagent aucune.
     */
    static double percentileOf(double v, List<Double> sorted) {
        int less = 0;
        int equal = 0;
        for (double x : sorted) {
            if (x < v) less++;
            else if (x == v) equal++;
        }
        return 100.0 * (less + 0.5 * equal) / sorted.size();
    }

    private void reportVelocity(List<Scored> scored) {
        title("2. Créent de la dette (classement vélocité, projets actifs)");

        List<Scored> ranked = scored.stream()
                .filter(s -> s.exclusion == Exclusion.NONE).toList();
        List<Scored> shortlist = ranked.stream().filter(s -> s.shortlisted).toList();

        if (ranked.isEmpty()) {
            System.out.println(c(scored.isEmpty()
                    ? "  Aucun projet actif dans cet inventaire : rien à classer."
                    : "  Aucun projet actif ne réunit assez de signaux pour être classé.",
                    YELLOW));
            reportExclusions(scored);
            return;
        }

        System.out.printf("  Projets classés                 : %d%n", ranked.size());
        System.out.printf("  Retenus (décile haut / strate)  : %s%n%n",
                c(String.valueOf(shortlist.size()), BOLD));

        System.out.printf("  %-4s %-44s %-7s %8s %7s %6s%n",
                "#", "projet", "strate", "ncloc", "score", "sig.");
        int rank = 0;
        for (Scored s : shortlist) {
            System.out.printf("  %-4d %-44s %-7s %8.0f %7.1f %6s%n",
                    ++rank, truncate(label(s.row), 44), s.bucket.name(), s.row.ncloc(),
                    s.score, s.signalsPresent() + "/4");
        }

        long partial = ranked.stream()
                .filter(s -> s.signalsPresent() < Signal.values().length).count();
        if (partial > 0) {
            System.out.printf("%n  %s%n", c(("%d des %d projets classés le sont sur un "
                    + "sous-ensemble de signaux.").formatted(partial, ranked.size()), DIM));
            System.out.println(c("  Une métrique absente est retirée de la moyenne, jamais "
                    + "comptée comme 0 :", DIM));
            System.out.println(c("  lire signaux_presents avant de comparer deux scores.", DIM));
        }

        reportExclusions(scored);
    }

    /** Un classement qui ne dit pas ce qu'il a écarté se lit comme complet. */
    private void reportExclusions(List<Scored> scored) {
        Map<Exclusion, Long> counts = new EnumMap<>(Exclusion.class);
        for (Scored s : scored) {
            if (s.exclusion != Exclusion.NONE) counts.merge(s.exclusion, 1L, Long::sum);
        }
        if (counts.isEmpty()) return;
        System.out.println("\n  Écartés du classement :");
        counts.forEach((e, n) -> System.out.printf("    %-38s %d%n", e.label, n));
    }

    // ----------------------------------------------------------------------
    // 4. Listes annexes
    // ----------------------------------------------------------------------

    /** Actifs sans code neuf : ils portent la dette sans en créer. */
    private List<Scored> rankCarrying(List<Row> active) {
        return active.stream()
                .map(Scored::new)
                .filter(s -> {
                    Double newLines = s.row.num("new_lines");
                    return (newLines == null || newLines <= 0)
                            && s.row.ncloc() >= minNcloc
                            && s.row.num("sqale_debt_ratio") != null;
                })
                .peek(s -> s.score = s.row.num("sqale_debt_ratio"))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .toList();
    }

    /** Obsolètes récents et endettés : quelqu'un a lâché un projet en mauvais état. */
    private List<Scored> rankAbandoned(List<Row> stale) {
        return stale.stream()
                .map(Scored::new)
                .filter(s -> {
                    Integer days = s.row.daysSinceAnalysis();
                    return days != null && days <= abandonedDays
                            && s.row.ncloc() >= minNcloc
                            && s.row.num("sqale_debt_ratio") != null;
                })
                .peek(s -> s.score = s.row.num("sqale_debt_ratio"))
                .sorted(Comparator.comparingDouble((Scored s) -> s.score).reversed())
                .toList();
    }

    private void reportSideLists(List<Scored> carrying, List<Scored> abandoned) {
        title("3. Listes annexes");
        System.out.printf("  Portent la dette sans en créer  : %d "
                + "(actifs, aucune ligne neuve)%n", carrying.size());
        System.out.printf("  Abandons récents (%d-%d j)     : %d%n",
                staleDays, abandonedDays, abandoned.size());
        System.out.println(c("    Les deux se classent sur sqale_debt_ratio seul : "
                + "un niveau, pas une vitesse.", DIM));
    }

    // ----------------------------------------------------------------------
    // 5. Sortie CSV
    // ----------------------------------------------------------------------

    private static final List<String> RAW_COLUMNS = List.of(
            "ncloc", "new_lines", "new_violations", "new_coverage", "coverage",
            "new_duplicated_lines_density", "sqale_debt_ratio", "sqale_rating",
            "reliability_rating", "security_rating", "alert_status", "analysisDate",
            "days_since_analysis");

    /** Au-delà, une liste annexe n'est plus une liste mais un export. */
    private static final int SIDE_LIST_KEEP = 30;

    private void write(List<Scored> velocity, List<Scored> carrying,
                       List<Scored> abandoned) throws IOException {
        List<String> header = new ArrayList<>(List.of(
                "liste", "rang", "retenu", "key", "name", "strate", "score",
                "signaux_presents", "exclusion"));
        for (Signal s : Signal.values()) header.add("pct_" + s.column);
        header.addAll(RAW_COLUMNS);

        try (Writer w = writer()) {
            CSVWriter csv = comma
                    ? new CSVWriter(w)
                    : new CSVWriter(w, ';', CSVWriter.DEFAULT_QUOTE_CHARACTER,
                            CSVWriter.DEFAULT_ESCAPE_CHARACTER, "\r\n");
            csv.writeNext(header.toArray(String[]::new));
            writeList(csv, "VELOCITE", velocity, true);
            writeList(csv, "DETTE_PORTEE", carrying, false);
            writeList(csv, "ABANDON_RECENT", abandoned, false);
            csv.flush();
        }

        System.out.printf("%n  Classement écrit : %s%n", out.toAbsolutePath());
        System.out.println(c("  Filtrer la colonne 'liste', puis 'retenu' = O "
                + "pour la shortlist.", DIM));
        if (!comma) {
            System.out.println(c("  Format Excel (point-virgule + BOM UTF-8) : "
                    + "double-clic, ou `start " + out + "`.", DIM));
            System.out.println(c("  Pour un autre outil : relancer avec --comma.", DIM));
        }
    }

    private void writeList(CSVWriter csv, String list, List<Scored> rows, boolean velocity) {
        int rank = 0;
        for (Scored s : rows) {
            boolean ranked = s.exclusion == Exclusion.NONE;
            if (ranked) rank++;
            List<String> row = new ArrayList<>();
            row.add(list);
            row.add(ranked ? String.valueOf(rank) : "");
            row.add(velocity ? (s.shortlisted ? "O" : "N")
                    : (rank <= SIDE_LIST_KEEP ? "O" : "N"));
            row.add(s.row.key());
            row.add(s.row.name());
            row.add(s.bucket.name());
            row.add(ranked ? "%.1f".formatted(s.score) : "");
            row.add(velocity ? String.valueOf(s.signalsPresent()) : "");
            row.add(s.exclusion.label);
            for (Signal sig : Signal.values()) {
                Double p = s.percentiles.get(sig);
                row.add(p == null ? "" : "%.0f".formatted(p));
            }
            for (String col : RAW_COLUMNS) row.add(s.row.str(col));
            csv.writeNext(row.toArray(String[]::new));
        }
    }

    /**
     * Sans BOM, Excel sous Windows lit un CSV UTF-8 en ANSI et massacre chaque
     * accent ; avec un séparateur virgule sous locale française, il empile tout
     * dans une seule colonne. Les deux à la fois font passer l'outil pour cassé.
     */
    private Writer writer() throws IOException {
        Writer w = Files.newBufferedWriter(out, StandardCharsets.UTF_8);
        if (!comma) w.write(BOM);
        return w;
    }

    // ----------------------------------------------------------------------
    // Présentation
    // ----------------------------------------------------------------------

    static final char BOM = (char) 0xFEFF;
    static final String ESC = String.valueOf((char) 27);
    static final String BOLD = ESC + "[1m";
    static final String DIM = ESC + "[2m";
    static final String RED = ESC + "[31m";
    static final String YELLOW = ESC + "[33m";
    static final String RESET = ESC + "[0m";

    static String c(String s, String color) {
        return color.isEmpty() ? s : color + s + RESET;
    }

    static void title(String text) {
        System.out.println("\n" + c(text, BOLD));
        System.out.println("-".repeat(Math.min(78, text.length() + 4)));
    }

    static String pct(int n, int total, String color) {
        String s = total == 0 ? String.valueOf(n)
                : "%d (%.0f%%)".formatted(n, 100.0 * n / total);
        return c(s, color);
    }

    static String label(Row r) {
        String name = r.name();
        return name.isEmpty() ? r.key() : name;
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    static int count(String s, char ch) {
        return (int) s.chars().filter(x -> x == ch).count();
    }

    static String stripBom(String s) {
        return (!s.isEmpty() && s.charAt(0) == BOM) ? s.substring(1) : s;
    }
}
