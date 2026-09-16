///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Csv.java
//SOURCES NameMatcher.java

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Apparier les lignes de deux CSV, et dire ce que vaut chaque manière de le
 * faire.
 *
 * Né de CrossAudit, qui en reste le premier client, et sorti pour servir
 * ailleurs : tout ce qui est propre à Sonar × GitLab tient dans un préréglage
 * ({@link Spec#sonarGitlab}), le reste ne connaît que des colonnes.
 *
 * Deux produits, et le second compte autant que le premier :
 *
 *   L'APPARIEMENT   Les méthodes sûres dans l'ordre donné — égalité exacte,
 *                   puis égalité après normalisation — puis la ressemblance
 *                   de noms sur ce qui reste, en suggestion seulement. Les
 *                   liens sont lus et jamais utilisés pour apparier.
 *
 *   LE RAPPORT      Chaque méthode est aussi évaluée seule, sur toutes les
 *                   lignes : ce qu'elle propose, ce qu'elle seule apporte, où
 *                   elle contredit les autres. La ressemblance de noms est
 *                   jugée contre les paires sûres — précision et rappel à
 *                   chaque seuil — et les échecs sont montrés par l'exemple :
 *                   quasi-réussites, noms qui divergent malgré une paire
 *                   sûre, mots jamais retrouvés de l'autre côté. C'est ce qui
 *                   dit quoi améliorer ensuite. Le JSON (--report) est fait
 *                   pour être relu, par un humain ou par un assistant.
 *
 * Usage :
 *   jbang ProjectMapper.java --left pratiques.csv --right inventaire.csv \
 *       --out correspondances.csv --report rapport-appariement.json
 *
 *   jbang ProjectMapper.java --left a.csv --right b.csv \
 *       --left-key nom --right-key code \
 *       --exact id:ref=ref_externe --derived nom:nom=code \
 *       --names nom=libelle,code --plain-names
 */
@Command(name = "ProjectMapper", mixinStandardHelpOptions = true,
        sortOptions = false, usageHelpAutoWidth = true,
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

    @Option(names = "--left", required = true, description = "CSV à apparier (une ligne de sortie par ligne)")
    Path leftCsv;

    @Option(names = "--right", required = true, description = "CSV où chercher l'homologue")
    Path rightCsv;

    @Option(names = "--left-key", description = "colonne qui nomme une ligne à gauche (défaut du préréglage : path)")
    String leftKey;

    @Option(names = "--right-key", description = "colonne qui nomme une ligne à droite (défaut du préréglage : key)")
    String rightKey;

    @Option(names = "--exact", description = "méthode par égalité stricte (répétable)")
    List<String> exact = new ArrayList<>();

    @Option(names = "--derived", description = "méthode par égalité normalisée (répétable)")
    List<String> derived = new ArrayList<>();

    @Option(names = "--link", description = "méthode informative par URL de dépôt (répétable)")
    List<String> link = new ArrayList<>();

    @Option(names = "--names", description = "ressemblance de noms : gauche=droite1,droite2")
    String names;

    @Option(names = "--plain-names",
            description = "le nom de gauche n'est pas un chemin : pas de dernier segment privilégié")
    boolean plainNames;

    @Option(names = "--seuil-nom", defaultValue = "0.5",
            description = "score minimal d'une suggestion, 0–1 (défaut : ${DEFAULT-VALUE})")
    double threshold;

    @Option(names = "--marge-nom", defaultValue = "0.1",
            description = "écart minimal avec le candidat suivant (défaut : ${DEFAULT-VALUE})")
    double margin;

    @Option(names = "--out", description = "CSV d'appariement, une ligne par ligne de gauche")
    Path out;

    @Option(names = "--report", description = "rapport JSON d'évaluation des méthodes")
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
        System.exit(new CommandLine(new ProjectMapper()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        ConsoleOut.colorMode(colorMode);
        Spec spec;
        if (exact.isEmpty() && derived.isEmpty() && link.isEmpty() && names == null) {
            spec = Spec.sonarGitlab(threshold, margin);
            if (leftKey != null || rightKey != null) {
                spec = spec.withKeys(orElse(leftKey, spec.leftKey()), orElse(rightKey, spec.rightKey()));
            }
        } else {
            List<KeyMethod> methods = new ArrayList<>();
            exact.forEach(m -> methods.add(KeyMethod.parse(m, Kind.EXACT)));
            derived.forEach(m -> methods.add(KeyMethod.parse(m, Kind.DERIVED)));
            link.forEach(m -> methods.add(KeyMethod.parse(m, Kind.LINK)));
            spec = new Spec(orElse(leftKey, "path"), orElse(rightKey, "key"), methods,
                    names == null ? null : NameSpec.parse(names, !plainNames), threshold, margin);
        }

        Csv.Table left = Csv.read(leftCsv);
        Csv.Table right = Csv.read(rightCsv);
        left.require(leftCsv, "--left", spec.leftKey());
        right.require(rightCsv, "--right", spec.rightKey());

        System.out.println(c("\nAppariement", BOLD));
        System.out.printf("  Gauche : %d lignes (%s)%n", left.rows().size(), leftCsv);
        System.out.printf("  Droite : %d lignes (%s)%n", right.rows().size(), rightCsv);

        Result r = map(spec, left, right);
        Report rep = evaluate(r, examples);
        print(rep);

        if (out != null) {
            writeMapping(r, out, comma);
            System.out.printf("%n  Appariement : %d lignes → %s%n", r.matches().size(), out.toAbsolutePath());
            System.out.println(c(Csv.openingHint(out, comma), DIM));
        }
        if (report != null) {
            writeReport(rep, report);
            System.out.printf("  Rapport     : %s%n", report.toAbsolutePath());
        }
        return 0;
    }

    // ----------------------------------------------------------------------
    // Ce qu'on demande
    // ----------------------------------------------------------------------

    enum Kind { EXACT, DERIVED, LINK }

    enum Confidence { EXACT, DERIVED, SUGGESTION, NONE }

    /**
     * Une méthode par clé. {@code whereColumn}/{@code whereValue} restreignent
     * les lignes de droite qu'elle lit : une liaison GitLab et une liaison
     * GitHub portent toutes deux un « dépôt », pas du même système.
     */
    record KeyMethod(String id, String label, Kind kind, String leftColumn, String rightColumn,
                     String whereColumn, String whereValue) {

        private static final Pattern SPEC = Pattern.compile(
                "([\\w-]+):([^=?]+)=([^=?]+)(?:\\?([^=]+)=(.*))?");

        static KeyMethod parse(String s, Kind kind) {
            Matcher m = SPEC.matcher(s.trim());
            if (!m.matches()) {
                throw new CommandLine.ParameterException(new CommandLine(new ProjectMapper()),
                        "méthode illisible : « " + s + " » (attendu id:gauche=droite[?colonne=valeur])");
            }
            return new KeyMethod(m.group(1), m.group(1), kind, m.group(2).trim(), m.group(3).trim(),
                    m.group(4) == null ? null : m.group(4).trim(), m.group(5));
        }

        Confidence confidence() {
            return switch (kind) {
                case EXACT -> Confidence.EXACT;
                case DERIVED -> Confidence.DERIVED;
                case LINK -> Confidence.NONE;
            };
        }

        boolean joins() { return kind != Kind.LINK; }

        boolean accepts(Csv.Row right) {
            return whereColumn == null || whereValue.equals(right.str(whereColumn));
        }
    }

    /** La ressemblance de noms : une colonne à gauche, une ou plusieurs à droite. */
    record NameSpec(String leftColumn, List<String> rightColumns, boolean pathLike) {

        static NameSpec parse(String s, boolean pathLike) {
            int eq = s.indexOf('=');
            if (eq <= 0) {
                throw new CommandLine.ParameterException(new CommandLine(new ProjectMapper()),
                        "--names illisible : « " + s + " » (attendu gauche=droite1,droite2)");
            }
            return new NameSpec(s.substring(0, eq).trim(),
                    List.of(s.substring(eq + 1).trim().split("\\s*,\\s*")), pathLike);
        }
    }

    record Spec(String leftKey, String rightKey, List<KeyMethod> methods, NameSpec names,
                double threshold, double margin) {

        static final String NAMES_ID = "noms";
        static final String NAMES_LABEL = "noms ressemblants (TF-IDF)";
        static final String NONE_LABEL = "aucune";

        /**
         * Le croisement tel que CrossAudit le fait : gauche = pratiques.csv,
         * droite = inventaire Sonar.
         */
        static Spec sonarGitlab(double threshold, double margin) {
            return new Spec("path", "key", List.of(
                    // La clé que le scanner envoie vraiment : la seule méthode qui
                    // lit l'instruction donnée au pipeline.
                    new KeyMethod("cle_ci", "clé lue dans la CI", Kind.EXACT,
                            "cle_sonar", "key", null, null),
                    // Ce que SonarQube a enregistré en important le dépôt.
                    new KeyMethod("liaison", "liaison DevOps Sonar → GitLab", Kind.EXACT,
                            "id", "alm_repository", "alm", "gitlab"),
                    // Le chemin normalisé comme une clé Sonar l'est souvent.
                    new KeyMethod("chemin", "clé normalisée = chemin GitLab", Kind.DERIVED,
                            "path", "key", null, null),
                    // Saisis à la main : lus, jamais utilisés pour apparier.
                    new KeyMethod("liens", "liens saisis dans Sonar", Kind.LINK,
                            "path", "liens", null, null)),
                    new NameSpec("path", List.of("key", "name"), true),
                    threshold, margin);
        }

        Spec withKeys(String left, String right) {
            return new Spec(left, right, methods, names, threshold, margin);
        }
    }

    // ----------------------------------------------------------------------
    // L'appariement
    // ----------------------------------------------------------------------

    /** Un rapprochement de noms : le candidat, son score, le suivant, et le refus éventuel. */
    record NameGuess(String candidate, double score, String runnerUp, double runnerUpScore,
                     String rejection) {

        boolean accepted() { return rejection == null && !candidate.isEmpty(); }
    }

    /**
     * Une ligne de gauche et son sort.
     *
     * {@code method} est l'id de la méthode retenue ({@link Spec#NAMES_ID}
     * pour une suggestion, null sinon). {@code found} donne, pour chaque
     * méthode par clé, les clés de droite qu'elle désigne — qu'elle ait été
     * retenue ou non. {@code guess} est la suggestion de noms faite sur ce qui
     * restait ; {@code free} la même ressemblance, calculée contre toute la
     * droite, pour l'évaluation.
     */
    record Match(int index, Csv.Row left, Csv.Row right, String method, String label,
                 Confidence confidence, Map<String, List<String>> found,
                 NameGuess guess, NameGuess free) {

        /** Une jointure sur laquelle on peut compter : ni suggestion, ni absence. */
        boolean joined() {
            return right != null && (confidence == Confidence.EXACT || confidence == Confidence.DERIVED);
        }

        String rightKey(Spec spec) { return right == null ? "" : right.str(spec.rightKey()); }
    }

    record Result(Spec spec, Csv.Table left, Csv.Table right, List<KeyMethod> available,
                  List<String> unavailable, List<Match> matches, NameMatcher matcher,
                  Map<String, Integer> rightIndex) { }

    static Result map(Spec spec, Csv.Table left, Csv.Table right) {
        List<KeyMethod> available = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (KeyMethod m : spec.methods()) {
            List<String> missing = new ArrayList<>();
            if (!left.has(m.leftColumn())) missing.add("gauche : " + m.leftColumn());
            if (!right.has(m.rightColumn())) missing.add("droite : " + m.rightColumn());
            if (m.whereColumn() != null && !right.has(m.whereColumn())) missing.add("droite : " + m.whereColumn());
            if (missing.isEmpty()) available.add(m);
            else unavailable.add(m.label() + " (colonne absente, " + String.join(", ", missing) + ")");
        }
        NameSpec names = spec.names();
        if (names != null && (!left.has(names.leftColumn())
                || names.rightColumns().stream().noneMatch(right::has))) {
            unavailable.add(Spec.NAMES_LABEL + " (colonne absente)");
            names = null;
        }

        List<Csv.Row> rights = right.rows();
        Map<String, Integer> rightIndex = new HashMap<>();
        for (int j = 0; j < rights.size(); j++) {
            String k = rights.get(j).str(spec.rightKey());
            if (!k.isEmpty()) rightIndex.putIfAbsent(k, j);
        }

        // Un index par méthode. Plusieurs lignes de droite peuvent partager une
        // valeur — un monodépôt lié plusieurs fois, un lien recopié : on les
        // garde toutes, dans l'ordre du fichier.
        Map<String, Map<String, List<Integer>>> indexes = new HashMap<>();
        for (KeyMethod m : available) {
            Map<String, List<Integer>> idx = new HashMap<>();
            for (int j = 0; j < rights.size(); j++) {
                Csv.Row r = rights.get(j);
                if (r.str(spec.rightKey()).isEmpty() || !m.accepts(r)) continue;
                for (String v : rightValues(m, r.str(m.rightColumn()))) {
                    List<Integer> l = idx.computeIfAbsent(v, k -> new ArrayList<>());
                    if (!l.contains(j)) l.add(j);
                }
            }
            indexes.put(m.id(), idx);
        }

        List<Match> matches = new ArrayList<>();
        Set<Integer> taken = new HashSet<>();
        List<Integer> unresolved = new ArrayList<>();
        for (int i = 0; i < left.rows().size(); i++) {
            Csv.Row l = left.rows().get(i);
            Map<String, List<String>> found = new LinkedHashMap<>();
            Integer hit = null;
            KeyMethod by = null;
            for (KeyMethod m : available) {
                String v = leftValue(m, l.str(m.leftColumn()));
                List<Integer> js = v.isEmpty() ? List.of() : indexes.get(m.id()).getOrDefault(v, List.of());
                found.put(m.id(), js.stream().map(j -> rights.get(j).str(spec.rightKey())).toList());
                if (hit == null && m.joins() && !js.isEmpty()) {
                    hit = js.get(0);
                    by = m;
                }
            }
            if (hit != null) {
                taken.add(hit);
                matches.add(new Match(i, l, rights.get(hit), by.id(), by.label(), by.confidence(),
                        found, null, null));
            } else {
                unresolved.add(i);
                matches.add(new Match(i, l, null, null, Spec.NONE_LABEL, Confidence.NONE,
                        found, null, null));
            }
        }

        NameMatcher matcher = names == null ? null : nameMatcher(names, left, right);
        if (matcher != null) {
            for (int i = 0; i < matches.size(); i++) {
                Match m = matches.get(i);
                matches.set(i, with(m, m.right(), m.method(), m.label(), m.confidence(), null,
                        guess(matcher, i, j -> true, rights, spec)));
            }
            suggest(spec, matcher, matches, unresolved, rights, rightIndex, taken);
        }
        return new Result(spec, left, right, available, unavailable, matches, matcher, rightIndex);
    }

    private static NameMatcher nameMatcher(NameSpec names, Csv.Table left, Csv.Table right) {
        List<Map<String, Double>> srcTf = new ArrayList<>();
        List<Set<String>> srcNum = new ArrayList<>();
        for (Csv.Row l : left.rows()) {
            String v = l.str(names.leftColumn());
            Map<String, Double> tf = new HashMap<>();
            if (names.pathLike()) {
                // Le dernier segment compte double : le namespace aide à
                // départager, il ne doit pas suffire à rapprocher.
                int cut = v.lastIndexOf('/');
                NameMatcher.addTokens(tf, v.substring(cut + 1), 2.0);
                if (cut > 0) NameMatcher.addTokens(tf, v.substring(0, cut), 1.0);
                srcNum.add(NameMatcher.numbers(v.substring(cut + 1)));
            } else {
                NameMatcher.addTokens(tf, v, 1.0);
                srcNum.add(NameMatcher.numbers(v));
            }
            srcTf.add(tf);
        }
        List<Map<String, Double>> dstTf = new ArrayList<>();
        List<Set<String>> dstNum = new ArrayList<>();
        for (Csv.Row r : right.rows()) {
            Map<String, Double> tf = new HashMap<>();
            String numbered = "";
            for (String col : names.rightColumns()) {
                String v = r.str(col);
                NameMatcher.addTokens(tf, v, 1.0);
                // Les nombres de la dernière colonne non vide — le nom affiché
                // dans le préréglage : la clé porte souvent un préfixe numéroté
                // qui ne dit rien du projet.
                if (!v.isEmpty()) numbered = v;
            }
            dstTf.add(tf);
            dstNum.add(NameMatcher.numbers(numbered));
        }
        return new NameMatcher(srcTf, srcNum, dstTf, dstNum);
    }

    private static NameGuess guess(NameMatcher matcher, int i, java.util.function.IntPredicate allowed,
                                   List<Csv.Row> rights, Spec spec) {
        List<NameMatcher.Scored> ranked = matcher.rank(i, allowed);
        if (ranked.isEmpty()) {
            return new NameGuess("", 0, "", 0,
                    matcher.hasNumberVeto(i) ? "numéros différents" : "aucun candidat");
        }
        NameMatcher.Scored best = ranked.get(0);
        NameMatcher.Scored second = ranked.size() > 1 ? ranked.get(1) : null;
        String rejection = best.score() < spec.threshold() ? "sous le seuil"
                : second != null && best.score() - second.score() < spec.margin() ? "ambigu"
                : null;
        return new NameGuess(rights.get(best.index()).str(spec.rightKey()), best.score(),
                second == null ? "" : rights.get(second.index()).str(spec.rightKey()),
                second == null ? 0 : second.score(), rejection);
    }

    /**
     * La ressemblance de noms, sur les lignes restées sans jointure et les
     * lignes de droite que personne n'a prises. Trois garde-fous : seuil,
     * marge avec le suivant, et un pour un — une ligne de droite ne se propose
     * qu'une fois, au meilleur score ; les autres apprennent qui l'a prise.
     */
    private static void suggest(Spec spec, NameMatcher matcher, List<Match> matches,
                                List<Integer> unresolved, List<Csv.Row> rights,
                                Map<String, Integer> rightIndex, Set<Integer> taken) {
        record Proposal(int row, NameGuess guess) { }
        List<Proposal> proposals = new ArrayList<>();
        for (int i : unresolved) {
            NameGuess g = guess(matcher, i, j -> !taken.contains(j), rights, spec);
            if (g.accepted()) proposals.add(new Proposal(i, g));
            else setGuess(matches, i, null, g);
        }
        proposals.sort(Comparator.comparingDouble((Proposal p) -> p.guess().score()).reversed());
        Map<String, String> claimedBy = new HashMap<>();
        for (Proposal p : proposals) {
            String winner = claimedBy.putIfAbsent(p.guess().candidate(),
                    matches.get(p.row()).left().str(spec.leftKey()));
            if (winner == null) {
                setGuess(matches, p.row(), rights.get(rightIndex.get(p.guess().candidate())), p.guess());
            } else {
                NameGuess g = p.guess();
                setGuess(matches, p.row(), null, new NameGuess(g.candidate(), g.score(),
                        g.runnerUp(), g.runnerUpScore(), "déjà proposé à " + winner));
            }
        }
    }

    private static void setGuess(List<Match> matches, int i, Csv.Row right, NameGuess g) {
        Match m = matches.get(i);
        matches.set(i, right == null
                ? with(m, null, null, Spec.NONE_LABEL, Confidence.NONE, g, m.free())
                : with(m, right, Spec.NAMES_ID, Spec.NAMES_LABEL, Confidence.SUGGESTION, g, m.free()));
    }

    private static Match with(Match m, Csv.Row right, String method, String label, Confidence conf,
                              NameGuess guess, NameGuess free) {
        return new Match(m.index(), m.left(), right, method, label, conf, m.found(), guess, free);
    }

    private static String leftValue(KeyMethod m, String v) {
        return switch (m.kind()) {
            case EXACT -> v.trim();
            case DERIVED -> normalise(v);
            case LINK -> v.trim().toLowerCase(Locale.ROOT);
        };
    }

    private static List<String> rightValues(KeyMethod m, String v) {
        if (v.isBlank()) return List.of();
        return switch (m.kind()) {
            case EXACT -> List.of(v.trim());
            case DERIVED -> List.of(normalise(v));
            case LINK -> linkedPaths(v);
        };
    }

    /**
     * Les clés s'écrivent {@code groupe:projet}, {@code groupe_projet} ou
     * {@code groupe-projet} selon qui les a créées ; un chemin s'écrit
     * {@code groupe/projet}. Tout séparateur devient le même, la casse tombe.
     */
    static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    /**
     * Les chemins de dépôt que désigne une liste de liens `type url | type url`.
     * `https://hote/groupe/projet.git`, `git@hote:groupe/projet`,
     * `https://hote/groupe/projet/-/tree/main` donnent tous `groupe/projet`.
     * L'hôte est ignoré : l'autre côté ne le porte pas.
     */
    static List<String> linkedPaths(String links) {
        List<String> out = new ArrayList<>();
        if (links == null || links.isBlank()) return out;
        for (String entry : links.split(" \\| ")) {
            String url = entry.contains(" ") ? entry.substring(entry.indexOf(' ') + 1) : entry;
            String path = url.trim()
                    .replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]+/", "")
                    .replaceFirst("^[^@/\\s]+@[^:/]+:", "")
                    .replaceFirst("/-/.*$", "")
                    .replaceFirst("[?#].*$", "")
                    .replaceFirst("\\.git/?$", "")
                    .replaceFirst("/+$", "")
                    .toLowerCase(Locale.ROOT);
            if (path.contains("/") && !path.contains("://") && !out.contains(path)) out.add(path);
        }
        return out;
    }

    // ----------------------------------------------------------------------
    // Colonnes d'appariement, partagées avec CrossAudit
    // ----------------------------------------------------------------------

    static List<String> detailHeader(Result r) {
        List<String> h = new ArrayList<>(List.of("candidat_nom", "score_nom", "second_nom", "rejet_nom"));
        for (KeyMethod m : r.available()) {
            h.add("m_" + m.id());
            if (m.kind() == Kind.LINK) h.add(m.id() + "_concorde");
        }
        if (r.matcher() != null) h.add("m_" + Spec.NAMES_ID + "_libre");
        return h;
    }

    static List<String> detailCells(Result r, Match p) {
        NameGuess g = p.guess();
        List<String> row = new ArrayList<>(List.of(
                g == null ? "" : g.candidate(),
                g == null || g.candidate().isEmpty() ? "" : score(g.score()),
                g == null || g.runnerUp().isEmpty() ? ""
                        : "%s (%s)".formatted(g.runnerUp(), score(g.runnerUpScore())),
                g == null || g.rejection() == null ? "" : g.rejection()));
        String rk = p.rightKey(r.spec());
        for (KeyMethod m : r.available()) {
            List<String> f = p.found().get(m.id());
            row.add(String.join(" ", f));
            if (m.kind() == Kind.LINK) {
                row.add(f.isEmpty() || p.right() == null ? "" : f.contains(rk) ? "oui" : "non");
            }
        }
        if (r.matcher() != null) {
            NameGuess f = p.free();
            row.add(f == null || f.candidate().isEmpty() ? ""
                    : "%s (%s%s)".formatted(f.candidate(), score(f.score()),
                            f.rejection() == null ? "" : ", " + f.rejection()));
        }
        return row;
    }

    static void writeMapping(Result r, Path out, boolean comma) throws IOException {
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        Spec s = r.spec();
        List<String> header = new ArrayList<>(List.of(
                "gauche_" + s.leftKey(), "droite_" + s.rightKey(), "methode", "confiance"));
        header.addAll(detailHeader(r));
        try (CSVWriter w = Csv.writer(out, comma)) {
            w.writeNext(header.toArray(String[]::new));
            for (Match p : r.matches()) {
                List<String> row = new ArrayList<>(List.of(p.left().str(s.leftKey()), p.rightKey(s),
                        p.label(), p.confidence().name().toLowerCase(Locale.ROOT)));
                row.addAll(detailCells(r, p));
                w.writeNext(row.toArray(String[]::new));
            }
        }
    }

    // ----------------------------------------------------------------------
    // Le rapport : ce que vaut chaque méthode
    // ----------------------------------------------------------------------

    record Report(Summary summary, List<String> unavailable, List<MethodStats> methods,
                  List<Agreement> agreements, List<Example> disagreements,
                  List<LinkStats> links, NameStats names) { }

    record Summary(int left, int right, Map<String, Integer> byMethod, int joined,
                   int suggestions, int unmatched, int rightUnmatched, double threshold, double margin) { }

    /**
     * {@code proposes} : lignes où la méthode, seule, désigne quelque chose.
     * {@code wins} : lignes qu'elle a tranchées dans l'ordre d'application.
     * {@code alone} : lignes où aucune autre méthode sûre ne propose rien — ce
     * qu'on perdrait sans elle.
     */
    record MethodStats(String id, String label, String kind, int proposes, int ambiguous,
                       int wins, int alone) { }

    record Agreement(String a, String b, int both, int agree, int disagree) { }

    record Example(String left, String detail) { }

    record LinkStats(String id, int rightWithLinks, int leftPointedAt, int confirmSure,
                     int confirmSuggestion, int contradict, int wouldJoin) { }

    record Bucket(String range, int correct, int wrong) { }

    /** {@code precision} est null quand rien n'est proposé : zéro sur zéro n'est pas 0 %. */
    record ThresholdPoint(double threshold, int proposed, int correct, Double precision, double recall) { }

    record TokenCount(String token, int count, boolean onRight) { }

    record NameStats(int evaluatedOn, List<Bucket> scoreOfBestCandidate,
                     List<ThresholdPoint> thresholds, Map<String, Integer> rejections,
                     List<Example> wrongOnSurePairs, List<Example> divergentSurePairs,
                     List<Example> nearMisses, List<TokenCount> unmatchedLeftTokens,
                     List<TokenCount> unmatchedRightPrefixes) { }

    static Report evaluate(Result r, int examples) {
        Spec s = r.spec();
        List<Match> ms = r.matches();

        Map<String, Integer> byMethod = new LinkedHashMap<>();
        for (KeyMethod m : r.available()) if (m.joins()) byMethod.put(m.label(), 0);
        if (r.matcher() != null) byMethod.put(Spec.NAMES_LABEL, 0);
        byMethod.put(Spec.NONE_LABEL, 0);
        for (Match m : ms) byMethod.merge(m.label(), 1, Integer::sum);
        int joined = (int) ms.stream().filter(Match::joined).count();
        int suggestions = (int) ms.stream().filter(m -> m.confidence() == Confidence.SUGGESTION).count();
        Set<String> usedRight = ms.stream().filter(m -> m.right() != null)
                .map(m -> m.rightKey(s)).collect(Collectors.toSet());
        int rightUnmatched = (int) r.right().rows().stream()
                .filter(x -> !usedRight.contains(x.str(s.rightKey()))).count();
        Summary summary = new Summary(ms.size(), r.right().rows().size(), byMethod, joined,
                suggestions, byMethod.get(Spec.NONE_LABEL), rightUnmatched, s.threshold(), s.margin());

        // Ce que chaque méthode propose, seule. Les noms comptent pour ce
        // qu'ils auraient proposé contre toute la droite, garde-fous compris.
        Map<String, String> labels = new LinkedHashMap<>();
        r.available().forEach(m -> labels.put(m.id(), m.label()));
        if (r.matcher() != null) labels.put(Spec.NAMES_ID, Spec.NAMES_LABEL);
        List<Map<String, List<String>>> proposals = new ArrayList<>();
        for (Match m : ms) {
            Map<String, List<String>> p = new LinkedHashMap<>(m.found());
            if (r.matcher() != null) {
                p.put(Spec.NAMES_ID, m.free() != null && m.free().accepted()
                        ? List.of(m.free().candidate()) : List.of());
            }
            proposals.add(p);
        }

        List<MethodStats> methods = new ArrayList<>();
        for (var e : labels.entrySet()) {
            String id = e.getKey();
            KeyMethod km = r.available().stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
            int proposes = 0, ambiguous = 0, wins = 0, alone = 0;
            for (int i = 0; i < ms.size(); i++) {
                List<String> mine = proposals.get(i).get(id);
                if (id.equals(ms.get(i).method())) wins++;
                if (mine.isEmpty()) continue;
                proposes++;
                if (mine.size() > 1) ambiguous++;
                boolean other = false;
                for (KeyMethod k : r.available()) {
                    if (k.joins() && !k.id().equals(id) && !proposals.get(i).get(k.id()).isEmpty()) other = true;
                }
                if (!other) alone++;
            }
            methods.add(new MethodStats(id, e.getValue(),
                    km == null ? "names" : km.kind().name().toLowerCase(Locale.ROOT),
                    proposes, ambiguous, wins, alone));
        }

        List<Agreement> agreements = new ArrayList<>();
        List<Example> disagreements = new ArrayList<>();
        List<String> ids = new ArrayList<>(labels.keySet());
        for (int a = 0; a < ids.size(); a++) {
            for (int b = a + 1; b < ids.size(); b++) {
                int both = 0, agree = 0, disagree = 0;
                for (int i = 0; i < ms.size(); i++) {
                    List<String> pa = proposals.get(i).get(ids.get(a));
                    List<String> pb = proposals.get(i).get(ids.get(b));
                    if (pa.isEmpty() || pb.isEmpty()) continue;
                    both++;
                    if (pa.stream().anyMatch(pb::contains)) {
                        agree++;
                    } else {
                        disagree++;
                        if (disagreements.size() < examples) {
                            disagreements.add(new Example(ms.get(i).left().str(s.leftKey()),
                                    "%s → %s ; %s → %s".formatted(ids.get(a), String.join(", ", pa),
                                            ids.get(b), String.join(", ", pb))));
                        }
                    }
                }
                if (both > 0) agreements.add(new Agreement(ids.get(a), ids.get(b), both, agree, disagree));
            }
        }

        List<LinkStats> links = new ArrayList<>();
        for (KeyMethod k : r.available()) {
            if (k.kind() != Kind.LINK) continue;
            int withLinks = (int) r.right().rows().stream()
                    .filter(x -> !linkedPaths(x.str(k.rightColumn())).isEmpty()).count();
            int pointed = 0, confirm = 0, confirmGuess = 0, contradict = 0, wouldJoin = 0;
            for (Match m : ms) {
                List<String> f = m.found().get(k.id());
                if (f.isEmpty()) continue;
                pointed++;
                if (m.right() == null) wouldJoin++;
                else if (!f.contains(m.rightKey(s))) contradict++;
                else if (m.confidence() == Confidence.SUGGESTION) confirmGuess++;
                else confirm++;
            }
            links.add(new LinkStats(k.id(), withLinks, pointed, confirm, confirmGuess, contradict, wouldJoin));
        }

        return new Report(summary, r.unavailable(), methods, agreements, disagreements, links,
                r.matcher() == null ? null : nameStats(r, examples));
    }

    /**
     * La ressemblance de noms jugée là où l'on connaît la réponse : les paires
     * sûres. Ce n'est pas une vérité absolue — une clé normalisée peut se
     * tromper — mais c'est la meilleure disponible, et elle dit à quel seuil
     * les suggestions deviennent fiables.
     */
    private static NameStats nameStats(Result r, int examples) {
        Spec s = r.spec();
        List<Match> ms = r.matches();
        List<Match> sure = ms.stream().filter(Match::joined).toList();

        int[] correct = new int[10], wrong = new int[10];
        List<Example> wrongOnSure = new ArrayList<>();
        List<Example> divergent = new ArrayList<>();
        List<Match> divergentAll = new ArrayList<>();
        Map<Match, Double> trueScore = new HashMap<>();
        for (Match m : sure) {
            NameGuess f = m.free();
            Integer j = r.rightIndex().get(m.rightKey(s));
            double ts = j == null ? 0 : r.matcher().score(m.index(), j);
            trueScore.put(m, ts);
            if (ts < 0.3) divergentAll.add(m);
            if (f == null || f.candidate().isEmpty()) continue;
            int b = Math.min(9, (int) (f.score() * 10));
            if (f.candidate().equals(m.rightKey(s))) {
                correct[b]++;
            } else {
                wrong[b]++;
                if (f.accepted() && wrongOnSure.size() < examples) {
                    wrongOnSure.add(new Example(m.left().str(s.leftKey()),
                            "proposé %s (%s), vrai %s (%s, par %s)".formatted(f.candidate(),
                                    score(f.score()), m.rightKey(s), score(ts), m.method())));
                }
            }
        }
        List<Bucket> buckets = new ArrayList<>();
        for (int b = 0; b < 10; b++) {
            buckets.add(new Bucket(String.format(Locale.ROOT, "%.1f–%.1f", b / 10.0, (b + 1) / 10.0),
                    correct[b], wrong[b]));
        }

        List<ThresholdPoint> points = new ArrayList<>();
        for (int t = 3; t <= 9; t++) {
            double th = t / 10.0;
            int proposed = 0, ok = 0;
            for (Match m : sure) {
                NameGuess f = m.free();
                if (f == null || f.candidate().isEmpty() || f.score() < th) continue;
                if (!f.runnerUp().isEmpty() && f.score() - f.runnerUpScore() < s.margin()) continue;
                proposed++;
                if (f.candidate().equals(m.rightKey(s))) ok++;
            }
            points.add(new ThresholdPoint(th, proposed, ok,
                    proposed == 0 ? null : round(ok / (double) proposed),
                    sure.isEmpty() ? 0 : round(ok / (double) sure.size())));
        }

        divergentAll.sort(Comparator.comparingDouble(trueScore::get));
        for (Match m : divergentAll.subList(0, Math.min(examples, divergentAll.size()))) {
            Csv.Row x = m.right();
            String names = s.names().rightColumns().stream().map(x::str)
                    .filter(v -> !v.isEmpty()).distinct().collect(Collectors.joining(" / "));
            divergent.add(new Example(m.left().str(s.leftKey()),
                    "%s — score %s, apparié par %s".formatted(names, score(trueScore.get(m)), m.method())));
        }

        Map<String, Integer> rejections = new LinkedHashMap<>();
        for (Match m : ms) {
            if (m.guess() == null || m.guess().rejection() == null) continue;
            String reason = m.guess().rejection().startsWith("déjà proposé") ? "déjà proposé" : m.guess().rejection();
            rejections.merge(reason, 1, Integer::sum);
        }

        List<Example> near = ms.stream()
                .filter(m -> m.right() == null && m.guess() != null && !m.guess().candidate().isEmpty())
                .filter(m -> m.guess().score() >= s.threshold() - 0.15)
                .sorted(Comparator.comparingDouble((Match m) -> m.guess().score()).reversed())
                .limit(examples)
                .map(m -> new Example(m.left().str(s.leftKey()), "%s (%s) — %s%s".formatted(
                        m.guess().candidate(), score(m.guess().score()), m.guess().rejection(),
                        m.guess().runnerUp().isEmpty() ? ""
                                : ", suivant %s (%s)".formatted(m.guess().runnerUp(),
                                score(m.guess().runnerUpScore())))))
                .toList();

        // Les mots des lignes restées seules, et s'ils existent de l'autre côté.
        // Un mot fréquent absent à droite est un nom qui ne s'écrit pas pareil
        // des deux côtés : un synonyme, une abréviation, une règle à ajouter.
        Set<String> rightTokens = new HashSet<>();
        for (Csv.Row x : r.right().rows()) {
            for (String col : s.names().rightColumns()) rightTokens.addAll(NameMatcher.tokens(x.str(col)));
        }
        Map<String, Integer> leftCounts = new HashMap<>();
        for (Match m : ms) {
            if (m.right() != null) continue;
            String v = m.left().str(s.names().leftColumn());
            if (s.names().pathLike()) v = v.substring(v.lastIndexOf('/') + 1);
            for (String t : new HashSet<>(NameMatcher.tokens(v))) leftCounts.merge(t, 1, Integer::sum);
        }
        List<TokenCount> leftTokens = top(leftCounts, 20).stream()
                .map(e -> new TokenCount(e.getKey(), e.getValue(), rightTokens.contains(e.getKey())))
                .toList();

        // Côté droit, les préfixes des clés restées seules : une convention de
        // nommage (`ch.ge.afc.`, `dsi_`) s'y lit d'un coup d'œil.
        Set<String> used = ms.stream().filter(m -> m.right() != null).map(m -> m.rightKey(s))
                .collect(Collectors.toSet());
        Map<String, Integer> prefixes = new HashMap<>();
        for (Csv.Row x : r.right().rows()) {
            String k = x.str(s.rightKey());
            if (k.isEmpty() || used.contains(k)) continue;
            prefixes.merge(prefix(k), 1, Integer::sum);
        }
        List<TokenCount> rightPrefixes = top(prefixes, 15).stream()
                .map(e -> new TokenCount(e.getKey(), e.getValue(), true)).toList();

        return new NameStats(sure.size(), buckets, points, rejections, wrongOnSure, divergent,
                near, leftTokens, rightPrefixes);
    }

    /** `ch.ge.afc.refonte:AFC_raff2` → `ch.ge.afc.refonte:` ; `dsi_paie_front` → `dsi_`. */
    static String prefix(String key) {
        int colon = key.lastIndexOf(':');
        if (colon > 0) return key.substring(0, colon + 1);
        Matcher m = Pattern.compile("^[A-Za-z0-9]+[._/-]").matcher(key);
        return m.find() ? m.group() : "(sans séparateur)";
    }

    private static List<Map.Entry<String, Integer>> top(Map<String, Integer> counts, int n) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(n).toList();
    }

    // ----------------------------------------------------------------------
    // Sorties du rapport
    // ----------------------------------------------------------------------

    static void print(Report rep) {
        Summary s = rep.summary();
        title("Méthodes retenues");
        s.byMethod().forEach((label, n) -> System.out.printf("  %-34s : %5d%n", label, n));
        System.out.printf("%n  Jointures sûres : %d / %d (%s) · suggestions : %d · sans rien : %d%n",
                s.joined(), s.left(), pct(s.joined(), s.left()), s.suggestions(), s.unmatched());
        System.out.printf("  Lignes de droite jamais retenues : %d / %d%n", s.rightUnmatched(), s.right());
        for (String u : rep.unavailable()) System.out.println(c("  Non évaluée : " + u, YELLOW));

        title("Chaque méthode, seule");
        System.out.println(c("  propose = désigne quelque chose · ambiguë = plusieurs candidats", DIM));
        System.out.println(c("  retenue = a tranché · seule = aucune autre méthode sûre ne propose", DIM));
        System.out.printf("  %-14s %9s %9s %9s %9s%n", "", "propose", "ambiguë", "retenue", "seule");
        for (MethodStats m : rep.methods()) {
            System.out.printf("  %-14s %9d %9d %9d %9d%n", m.id(), m.proposes(), m.ambiguous(),
                    m.wins(), m.alone());
        }

        if (!rep.agreements().isEmpty()) {
            title("Accords entre méthodes");
            for (Agreement a : rep.agreements()) {
                System.out.printf("  %-10s × %-10s : %4d en commun, %4d d'accord, %s en désaccord%n",
                        a.a(), a.b(), a.both(), a.agree(),
                        c(String.valueOf(a.disagree()), a.disagree() == 0 ? GREEN : YELLOW));
            }
            examples(rep.disagreements());
        }

        for (LinkStats l : rep.links()) {
            title("Liens « " + l.id() + " » (informatif, n'apparie rien)");
            System.out.printf("  Lignes de droite avec un lien de dépôt : %d%n", l.rightWithLinks());
            System.out.printf("  Lignes de gauche désignées             : %d%n", l.leftPointedAt());
            System.out.printf("    confirment une jointure sûre         : %d%n", l.confirmSure());
            System.out.printf("    confirment une suggestion            : %d%n", l.confirmSuggestion());
            System.out.printf("    contredisent la jointure             : %d%n", l.contradict());
            System.out.printf("    apparieraient une ligne seule        : %d%n", l.wouldJoin());
        }

        NameStats n = rep.names();
        if (n == null) return;
        title("Ressemblance de noms, jugée sur %d paires sûres".formatted(n.evaluatedOn()));
        System.out.println(c("  Meilleur candidat contre toute la droite, par tranche de score :", DIM));
        for (Bucket b : n.scoreOfBestCandidate()) {
            if (b.correct() + b.wrong() == 0) continue;
            System.out.printf("    %s  juste %4d  faux %4d%n", b.range(), b.correct(), b.wrong());
        }
        System.out.println(c("  Avec la marge actuelle, à chaque seuil :", DIM));
        for (ThresholdPoint p : n.thresholds()) {
            System.out.printf(Locale.ROOT, "    %.1f  proposées %4d  justes %4d  précision %s  rappel %s%n",
                    p.threshold(), p.proposed(), p.correct(),
                    p.precision() == null ? "   —" : pct(p.precision()), pct(p.recall()));
        }
        if (!n.rejections().isEmpty()) {
            System.out.println("  Suggestions refusées : " + n.rejections());
        }
        subtitle("Fausses propositions sur des paires sûres", n.wrongOnSurePairs());
        subtitle("Paires sûres dont les noms divergent (score < 0.3)", n.divergentSurePairs());
        subtitle("Quasi-réussites", n.nearMisses());
        if (!n.unmatchedLeftTokens().isEmpty()) {
            System.out.println(c("\n  Mots fréquents des lignes seules (* = absent à droite) :", DIM));
            System.out.println("    " + n.unmatchedLeftTokens().stream()
                    .map(t -> t.token() + (t.onRight() ? "" : "*") + " " + t.count())
                    .collect(Collectors.joining(", ")));
        }
        if (!n.unmatchedRightPrefixes().isEmpty()) {
            System.out.println(c("  Préfixes des clés de droite jamais retenues :", DIM));
            System.out.println("    " + n.unmatchedRightPrefixes().stream()
                    .map(t -> t.token() + " " + t.count()).collect(Collectors.joining(", ")));
        }
    }

    private static void subtitle(String t, List<Example> ex) {
        if (ex.isEmpty()) return;
        System.out.println(c("\n  " + t + " :", DIM));
        examples(ex);
    }

    private static void examples(List<Example> ex) {
        ex.stream().limit(5).forEach(e -> System.out.println("      " + e.left() + " — " + e.detail()));
        if (ex.size() > 5) System.out.printf("      … %d de plus dans le rapport JSON.%n", ex.size() - 5);
    }

    static void writeReport(Report rep, Path path) throws IOException {
        Path dir = path.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(path.toFile(), rep);
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

    static String score(double s) {
        return String.format(Locale.ROOT, "%.2f", s);
    }

    static String pct(long n, long total) {
        return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
    }

    static String pct(double ratio) {
        return "%3.0f %%".formatted(100 * ratio);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static String orElse(String v, String dflt) {
        return v == null || v.isBlank() ? dflt : v;
    }
}
