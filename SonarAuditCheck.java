///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Diagnostic préalable à un audit de parc SonarQube.
 *
 *   1. L'API répond-elle et mon token est-il valide ?
 *   2. Quelles requêtes puis-je réellement faire (parmi celles qui comptent) ?
 *   3. Combien de projets je vois, et combien m'échappent ?
 *   4. Quels signaux d'activité sont disponibles sans passer par Git ?
 *
 * Usage :
 *   export SONAR_URL=https://sonar.example.com
 *   export SONAR_TOKEN=squ_xxxxxxxx
 *   jbang SonarAuditCheck.java --csv projets.csv
 *   jbang SonarAuditCheck.java --organization my-org        # SonarQube Cloud
 *   jbang SonarAuditCheck.java --dump-dir ./captures        # captures brutes
 */
@Command(name = "SonarAuditCheck", mixinStandardHelpOptions = true,
        sortOptions = false, usageHelpAutoWidth = true,
        description = "Diagnostic d'accès Sonar avant audit de parc.",
        synopsisHeading = "",
        customSynopsis = {
            "Usage : SonarAuditCheck [--csv <fichier>] [options]",
        },
        footer = {
            "",
            "Cas d'usage courants :",
            "",
            "  Vérifier l'accès avant de commencer — aucun fichier écrit :",
            "    SonarAuditCheck",
            "",
            "  Le diagnostic et l'inventaire du parc, en un passage :",
            "    SonarAuditCheck --csv inventaire.csv",
            "",
            "  Puis le classement, qui ne rappelle pas l'API :",
            "    SonarRank --in inventaire.csv --out classement.csv",
            "",
            "  SonarQube Cloud :",
            "    SonarAuditCheck --organization mon-org --csv inventaire.csv",
            "",
            "SONAR_URL et SONAR_TOKEN peuvent remplacer --url et --token. Le jeton",
            "doit être de type 'User' (squ_...), pas un jeton d'analyse.",
            "Colonnes du CSV : COLUMNS.md. Méthode de classement : ANALYSIS.md.",
        })
public class SonarAuditCheck implements Callable<Integer> {

    // ----------------------------------------------------------------------
    // Options
    // ----------------------------------------------------------------------

    @Option(names = "--url", defaultValue = "${env:SONAR_URL}",
            description = "URL de l'instance")
    String url;

    @Option(names = "--token", defaultValue = "${env:SONAR_TOKEN}",
            description = "User token squ_...")
    String token;

    @Option(names = "--organization", defaultValue = "${env:SONAR_ORG}",
            description = "SonarQube Cloud")
    String organization;

    @Option(names = "--project", description = "projet témoin (sinon : le premier visible)")
    String project;

    @Option(names = "--csv", description = "chemin du CSV d'inventaire à écrire")
    Path csv;

    @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
    Path dumpDir;

    @Option(names = "--stale-days", defaultValue = "90",
            description = "seuil d'obsolescence (défaut : ${DEFAULT-VALUE})")
    int staleDays;

    @Option(names = "--activity-days", defaultValue = "90",
            description = "fenêtre d'activité (défaut : ${DEFAULT-VALUE})")
    int activityDays;

    @Option(names = "--timeout", defaultValue = "30")
    int timeout;

    @Option(names = "--insecure", description = "ignorer la validation TLS")
    boolean insecure;

    @Option(names = "--color", defaultValue = "auto",
            description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
    String colorMode;

    public static void main(String[] args) {
        ConsoleOut.install();
        System.exit(new CommandLine(new SonarAuditCheck()).execute(args));
    }

    // ----------------------------------------------------------------------
    // Orchestration
    // ----------------------------------------------------------------------

    private Sonar sq;

    @Override
    public Integer call() throws Exception {
        ConsoleOut.colorMode(colorMode);
        if (isBlank(url) || isBlank(token)) {
            System.err.println("SONAR_URL et SONAR_TOKEN sont requis "
                    + "(variables d'env ou --url/--token).");
            return 2;
        }
        sq = new Sonar(url, token, organization, timeout, insecure, dumpDir);

        System.out.println(c("\nInstance : " + sq.base, BOLD));

        CurrentUser me = checkConnectivity();
        if (me == null) return 1;

        String sample = (project != null) ? project : firstVisibleProject();

        checkCapabilities(sample);
        inventory();
        if (sample != null) activitySignals(sample, activityDays);

        summary();
        return 0;
    }

    private String firstVisibleProject() {
        ProjectSearch p = sq.get("api/components/search_projects", params("ps", "1"))
                .as(ProjectSearch.class);
        return (p != null && notEmpty(p.components())) ? p.components().get(0).key() : null;
    }

    private void summary() {
        title("Synthèse");
        System.out.printf("  %d appels API effectués.%n", sq.calls);
        System.out.println(c("""
                  Rappel : search_projects filtre silencieusement sur ce que le token
                  peut voir. Une permission manquante ne produit pas d'erreur, seulement
                  un classement incomplet — et les projets manquants sont souvent ceux
                  qui auraient le plus besoin d'aide.
                """, DIM));
    }

    // ----------------------------------------------------------------------
    // 1. Connectivité et identité
    // ----------------------------------------------------------------------

    private CurrentUser checkConnectivity() {
        title("1. Connectivité et identité");

        Response status = sq.get("api/system/status", params());
        if (status.unreachable()) {
            line("api/system/status", Verdict.ERROR, status.errorMessage());
            System.out.println();
            System.out.println(c("  Impossible de joindre l'instance. "
                    + "Vérifie l'URL, le proxy, le TLS.", RED));
            return null;
        }
        SystemStatus st = status.as(SystemStatus.class);
        if (st != null) {
            line("api/system/status", Verdict.OK,
                    "%s · v%s · %s".formatted(st.status(), st.version(), orEmpty(st.id())));
        } else {
            line("api/system/status", status.verdict(), "HTTP " + status.status());
        }

        Validation v = sq.get("api/authentication/validate", params()).as(Validation.class);
        boolean valid = v != null && Boolean.TRUE.equals(v.valid());
        line("api/authentication/validate", valid ? Verdict.OK : Verdict.DENIED,
                valid ? "token valide" : "token invalide ou expiré");
        if (!valid) {
            System.out.println();
            System.out.println(c("  Le token n'est pas accepté. Vérifie qu'il s'agit bien d'un", RED));
            System.out.println(c("  token de type 'User' (squ_...) et non d'un token d'analyse.", RED));
            return null;
        }

        Response r = sq.get("api/users/current", params());
        CurrentUser me = r.as(CurrentUser.class);
        if (me == null) {
            line("api/users/current", r.verdict(), r.errorMessage());
            return new CurrentUser(null, null, null, null);
        }

        List<String> perms = me.globalPermissions();
        line("api/users/current", Verdict.OK,
                "%s (%s)".formatted(me.login(), orEmpty(me.name())));
        System.out.println();
        System.out.println("  Permissions globales : "
                + c(perms.isEmpty() ? "aucune" : String.join(", ", perms), DIM));
        if (notEmpty(me.groups())) {
            System.out.println("  Groupes              : "
                    + c(String.join(", ", me.groups().subList(0, Math.min(8, me.groups().size()))), DIM));
        }
        if (perms.contains("admin")) {
            System.out.println();
            System.out.println(c("  Note : ce token a 'Administer System'. Il verra tout, donc ce", YELLOW));
            System.out.println(c("  diagnostic ne reflétera pas le périmètre d'un compte d'audit", YELLOW));
            System.out.println(c("  restreint. Pour un audit récurrent, préfère un compte dédié.", YELLOW));
        }
        return me;
    }

    // ----------------------------------------------------------------------
    // 2. Capacités réelles
    // ----------------------------------------------------------------------

    /** Les métriques qui ciblent la dette en cours de création, pas le stock. */
    private static final String METRICS = String.join(",",
            "ncloc", "coverage", "duplicated_lines_density",
            "sqale_index", "sqale_debt_ratio", "sqale_rating",
            "reliability_rating", "security_rating", "alert_status",
            "new_coverage", "new_lines", "new_violations",
            "new_duplicated_lines_density", "security_hotspots_reviewed");

    private void checkCapabilities(String sample) {
        title("2. Capacités du token sur les endpoints utiles");

        if (sample == null) {
            System.out.println(c("  Aucun projet visible : "
                    + "impossible de tester les endpoints projet.", YELLOW));
            return;
        }
        System.out.println("  Projet témoin : " + c(sample, DIM) + "\n");

        probe("Inventaire projets (search_projects)", "api/components/search_projects",
                ProjectSearch.class, p -> total(p.paging()) + " projets visibles",
                params("ps", "1"));

        probe("Mesures en masse (measures/search)", "api/measures/search",
                MeasuresSearch.class, p -> size(p.measures()) + " mesures",
                params("projectKeys", sample, "metricKeys", METRICS));

        probe("Historique de mesures (search_history)", "api/measures/search_history",
                SearchHistory.class, p -> total(p.paging()) + " points",
                params("component", sample, "metrics", "ncloc,coverage,sqale_index", "ps", "5"));

        probe("Issues + facettes (issues/search)", "api/issues/search",
                IssuesSearch.class, p -> p.issueCount() + " issues",
                params("componentKeys", sample, "ps", "1", "facets", "severities"));

        probe("Contournements (WONTFIX / FALSE-POSITIVE)", "api/issues/search",
                IssuesSearch.class, p -> p.issueCount() + " issues neutralisées",
                params("componentKeys", sample, "resolutions", "WONTFIX,FALSE-POSITIVE", "ps", "1"));

        probe("Exclusions (settings/values)", "api/settings/values",
                SettingsValues.class, p -> size(p.settings()) + " réglage(s) d'exclusion",
                params("component", sample,
                        "keys", "sonar.exclusions,sonar.coverage.exclusions,sonar.cpd.exclusions"));

        probe("Quality gate du projet", "api/qualitygates/get_by_project",
                QualityGateResponse.class, p -> p.gateName(),
                params("project", sample));

        probe("Quality profiles du projet", "api/qualityprofiles/search",
                QualityProfiles.class, p -> size(p.profiles()) + " profil(s)",
                params("project", sample));

        probe("Historique d'analyses (project_analyses)", "api/project_analyses/search",
                Analyses.class, p -> total(p.paging()) + " analyses",
                params("project", sample, "ps", "5"));

        probe("Arbre de composants (components/tree)", "api/components/tree",
                ProjectSearch.class, p -> total(p.paging()) + " fichiers",
                params("component", sample, "qualifiers", "FIL", "ps", "1"));

        probe("Total réel des projets (admin uniquement)", "api/projects/search",
                ProjectSearch.class, p -> total(p.paging()) + " projets au total",
                params("ps", "1"));

        probeBlame(sample);
    }

    /** Blame SCM : nécessite 'See Source Code' en plus de 'Browse'. */
    private void probeBlame(String sample) {
        String fileKey = firstFileOf(sample);
        if (fileKey == null) {
            line("Blame SCM (sources/scm)", Verdict.EMPTY, "aucun fichier accessible pour tester");
            return;
        }
        probe("Blame SCM (sources/scm)", "api/sources/scm",
                ScmResponse.class, ScmResponse::describe,
                params("key", fileKey, "from", "1", "to", "20"));
    }

    private String firstFileOf(String projectKey) {
        ProjectSearch tree = sq.get("api/components/tree",
                        params("component", projectKey, "qualifiers", "FIL", "ps", "1"))
                .as(ProjectSearch.class);
        return (tree != null && notEmpty(tree.components())) ? tree.components().get(0).key() : null;
    }

    /** Un probe = un appel, un verdict OK/REFUSE/ABSENT, et un détail si ça a marché. */
    private <T> T probe(String label, String path, Class<T> type,
                        Function<T, String> hint, Map<String, String> params) {
        Response r = sq.get(path, params);
        Verdict v = r.verdict();
        String detail = "";
        T parsed = null;
        if (v == Verdict.OK) {
            parsed = r.as(type);
            detail = (parsed == null) ? "réponse illisible" : safely(hint, parsed);
        } else {
            detail = "HTTP %d %s".formatted(r.status(), r.errorMessage());
        }
        line(label, v, detail);
        return parsed;
    }

    private static <T> String safely(Function<T, String> f, T value) {
        try {
            return orEmpty(f.apply(value));
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ----------------------------------------------------------------------
    // 3. Inventaire des projets
    // ----------------------------------------------------------------------

    private void inventory() throws IOException {
        title("3. Inventaire des projets visibles");

        List<Component> projects = fetchAllProjects();
        System.out.println("  Projets visibles avec ce token : "
                + c(String.valueOf(projects.size()), BOLD));

        reportScopeGap(projects.size());
        reportFreshness(projects);

        Map<String, Map<String, String>> measures = fetchMeasures(projects);
        reportMissingCoverage(projects, measures);

        if (csv != null) {
            writeCsv(projects, measures);
            System.out.println();
            System.out.println("  CSV écrit : " + c(csv.toString(), BOLD));
        }
    }

    private List<Component> fetchAllProjects() {
        List<Component> all = new ArrayList<>();
        int page = 1, pageSize = 500, guard = 40;
        while (page <= guard) {
            Response r = sq.get("api/components/search_projects",
                    params("ps", String.valueOf(pageSize), "p", String.valueOf(page),
                            "f", "analysisDate,leakPeriodDate"));
            ProjectSearch p = r.as(ProjectSearch.class);
            if (p == null) {
                System.out.println(c("  Échec de la pagination page %d : HTTP %d"
                        .formatted(page, r.status()), RED));
                break;
            }
            List<Component> batch = orEmptyList(p.components());
            all.addAll(batch);
            int total = totalOf(p.paging());
            if (batch.isEmpty() || all.size() >= total) break;
            page++;
        }
        if (page > guard) System.out.println(c("  Pagination interrompue à 20 000 projets.", YELLOW));
        return all;
    }

    /** L'écart entre ce que je vois et ce qui existe : le point aveugle de l'audit. */
    private void reportScopeGap(int visible) {
        ProjectSearch p = sq.get("api/projects/search", params("ps", "1")).as(ProjectSearch.class);
        // paging absent = réponse illisible, pas « zéro projet » : ne rien affirmer.
        Integer real = (p == null || p.paging() == null) ? null : p.paging().total();
        if (real == null) {
            System.out.println(c("""
                    \s Total réel indisponible (nécessite 'Administer System').
                        Fais comparer ce chiffre à un admin : l'écart est le point aveugle
                        de ton audit, et il n'apparaît dans aucune réponse d'erreur.""", YELLOW));
            return;
        }
        int gap = real - visible;
        System.out.println("  Projets réellement présents    : " + c(String.valueOf(real), BOLD));
        if (gap > 0 && real > 0) {
            System.out.println(c(("  → %d projet(s) hors de ton périmètre. Ton classement sera "
                    + "tronqué%n    de %.0f%% sans aucun message d'erreur.")
                    .formatted(gap, 100.0 * gap / real), RED));
        } else {
            System.out.println(c("  → Périmètre complet.", GREEN));
        }
    }

    private void reportFreshness(List<Component> projects) {
        LocalDateTime now = LocalDateTime.now();
        List<Component> never = new ArrayList<>(), stale = new ArrayList<>(), fresh = new ArrayList<>();
        for (Component p : projects) {
            LocalDateTime d = parseDate(p.analysisDate());
            if (d == null) never.add(p);
            else if (ChronoUnit.DAYS.between(d, now) > staleDays) stale.add(p);
            else fresh.add(p);
        }
        System.out.println();
        System.out.printf("  Analysés il y a < %d j        : %d%n", staleDays, fresh.size());
        System.out.printf("  Analysés il y a > %d j        : %s%n", staleDays,
                c(String.valueOf(stale.size()), YELLOW));
        System.out.printf("  Jamais analysés                : %s%n",
                c(String.valueOf(never.size()), YELLOW));
        if (!never.isEmpty()) {
            System.out.println(c("    (projets créés puis abandonnés, "
                    + "ou analyse jamais configurée)", DIM));
        }
    }

    /**
     * Les valeurs restent des String : c'est le format que renvoie l'API, et
     * l'absence de clé se distingue ainsi naturellement d'une valeur à zéro.
     */
    private Map<String, Map<String, String>> fetchMeasures(List<Component> projects) {
        Map<String, Map<String, String>> byKey = new LinkedHashMap<>();
        List<String> keys = projects.stream().map(Component::key).toList();
        for (int i = 0; i < keys.size(); i += 100) {
            List<String> chunk = keys.subList(i, Math.min(i + 100, keys.size()));
            MeasuresSearch m = sq.get("api/measures/search",
                            params("projectKeys", String.join(",", chunk), "metricKeys", METRICS))
                    .as(MeasuresSearch.class);
            if (m == null) continue;
            for (Measure measure : orEmptyList(m.measures())) {
                byKey.computeIfAbsent(measure.component(), k -> new HashMap<>())
                        .put(measure.metric(), measure.effectiveValue());
            }
        }
        return byKey;
    }

    private void reportMissingCoverage(List<Component> projects,
                                       Map<String, Map<String, String>> measures) {
        long missing = projects.stream()
                .filter(p -> !measures.getOrDefault(p.key(), Map.of()).containsKey("coverage"))
                .count();
        System.out.printf("  Sans aucune donnée de couverture: %s%n",
                c(String.valueOf(missing), YELLOW));
        if (missing > 0) {
            System.out.println(c("    (coverage absente ≠ coverage à 0 : "
                    + "ces projets sont invisibles", DIM));
            System.out.println(c("     dans tout tri par couverture, "
                    + "et souvent les plus à risque)", DIM));
        }
    }

    // ----------------------------------------------------------------------
    // CSV
    // ----------------------------------------------------------------------

    private static final List<String> METRIC_COLUMNS = List.of(
            "ncloc", "coverage", "new_coverage",
            "duplicated_lines_density", "new_duplicated_lines_density",
            "sqale_debt_ratio", "sqale_rating", "new_violations", "new_lines",
            "reliability_rating", "security_rating", "security_hotspots_reviewed",
            "alert_status");

    private void writeCsv(List<Component> projects,
                          Map<String, Map<String, String>> measures) throws IOException {
        List<String> header = new ArrayList<>(
                List.of("key", "name", "analysisDate", "days_since_analysis"));
        header.addAll(METRIC_COLUMNS);

        LocalDateTime now = LocalDateTime.now();
        try (CSVWriter w = new CSVWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            w.writeNext(header.toArray(String[]::new));
            for (Component p : projects) {
                w.writeNext(csvRow(p, measures.getOrDefault(p.key(), Map.of()), now));
            }
        }
    }

    private String[] csvRow(Component p, Map<String, String> m, LocalDateTime now) {
        LocalDateTime d = parseDate(p.analysisDate());
        List<String> row = new ArrayList<>(List.of(
                orEmpty(p.key()),
                orEmpty(p.name()),
                orEmpty(p.analysisDate()),
                d == null ? "" : String.valueOf(ChronoUnit.DAYS.between(d, now))));
        METRIC_COLUMNS.forEach(metric -> row.add(orEmpty(m.get(metric))));
        return row.toArray(String[]::new);
    }

    // ----------------------------------------------------------------------
    // 4. Signaux d'activité sans Git
    // ----------------------------------------------------------------------

    private void activitySignals(String sample, int days) {
        title("4. Signaux d'activité disponibles sans Git (sur %s)".formatted(sample));
        String since = LocalDateTime.now().minusDays(days)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        reportAnalysisCadence(sample, since, days);
        reportAuthorConcentration(sample, since, days);
        reportNewCodeVolume(sample);
        reportDebtPerLine(sample, since, days);
        reportBlame(sample);
    }

    /** Cadence d'analyses = cadence de livraison (biais : les builds nocturnes la gonflent). */
    private void reportAnalysisCadence(String sample, String since, int days) {
        Response r = sq.get("api/project_analyses/search",
                params("project", sample, "ps", "500", "from", since));
        Analyses a = r.as(Analyses.class);
        if (a == null) {
            line("api/project_analyses/search", r.verdict(), "HTTP " + r.status());
            return;
        }
        List<Analysis> analyses = orEmptyList(a.analyses());
        long versions = analyses.stream()
                .flatMap(x -> orEmptyList(x.events()).stream())
                .filter(e -> "VERSION".equals(e.category()))
                .map(Event::name).distinct().count();

        System.out.printf("  Analyses sur %d j            : %s%n", days,
                c(String.valueOf(analyses.size()), BOLD));
        System.out.printf("  Versions livrées                : %d%n", versions);

        List<LocalDateTime> dates = analyses.stream()
                .map(x -> parseDate(x.date())).filter(Objects::nonNull).sorted().toList();
        if (dates.size() > 1) {
            long span = Math.max(1, ChronoUnit.DAYS.between(dates.get(0), dates.get(dates.size() - 1)));
            System.out.printf("  Cadence moyenne                 : 1 analyse / %.1f j%n",
                    (double) span / dates.size());
        }
    }

    /** La facette 'author' donne le nombre de contributeurs sans toucher à Git. */
    private void reportAuthorConcentration(String sample, String since, int days) {
        Response r = sq.get("api/issues/search",
                params("componentKeys", sample, "createdAfter", since, "facets", "author", "ps", "1"));
        IssuesSearch is = r.as(IssuesSearch.class);
        if (is == null) {
            line("Facette 'author' sur issues", r.verdict(), "HTTP " + r.status());
            return;
        }
        List<FacetValue> authors = is.facetValues("author");
        int total = Math.max(1, authors.stream().mapToInt(f -> orZero(f.count())).sum());

        System.out.println();
        System.out.printf("  Auteurs distincts (%d j)      : %s%n", days,
                c(String.valueOf(authors.size()), BOLD));
        if (authors.isEmpty()) return;

        FacetValue top = authors.get(0);
        double share = (double) orZero(top.count()) / total;
        System.out.printf("  Auteur principal                : %s (%.0f%% des issues récentes)%n",
                top.val(), share * 100);
        if (share > 0.7 && authors.size() > 1) {
            System.out.println(c("    → forte concentration : proxy de bus factor faible", YELLOW));
        }
    }

    private void reportNewCodeVolume(String sample) {
        ComponentMeasures cm = sq.get("api/measures/component",
                        params("component", sample,
                                "metricKeys", "new_lines,new_violations,new_coverage,ncloc"))
                .as(ComponentMeasures.class);
        if (cm == null || cm.component() == null) return;

        Map<String, String> ms = orEmptyList(cm.component().measures()).stream()
                .collect(Collectors.toMap(Measure::metric, m -> orEmpty(m.effectiveValue()),
                        (a, b) -> a));
        System.out.println();
        System.out.printf("  Lignes neuves (new code)        : %s%n", orNa(ms.get("new_lines")));
        System.out.printf("  Violations neuves               : %s%n", orNa(ms.get("new_violations")));
        System.out.printf("  Couverture du code neuf         : %s%n", orNa(ms.get("new_coverage")));
    }

    /** Δdette / Δlignes : la dette ajoutée par ligne écrite, le vrai signal de vélocité. */
    private void reportDebtPerLine(String sample, String since, int days) {
        SearchHistory h = sq.get("api/measures/search_history",
                        params("component", sample, "metrics", "ncloc,sqale_index",
                                "ps", "1000", "from", since))
                .as(SearchHistory.class);
        if (h == null) return;

        List<HistoryPoint> ncloc = h.pointsFor("ncloc");
        List<HistoryPoint> debt = h.pointsFor("sqale_index");
        if (ncloc.size() < 2 || debt.size() < 2) return;

        long dNcloc = delta(ncloc);
        long dDebt = delta(debt);
        System.out.println();
        System.out.printf("  Δ lignes sur %d j            : %+d%n", days, dNcloc);
        System.out.printf("  Δ dette sur %d j (min)       : %+d%n", days, dDebt);
        if (dNcloc > 0) {
            System.out.println("  Dette ajoutée par ligne écrite  : "
                    + c("%+.2f min/LOC".formatted((double) dDebt / dNcloc), BOLD));
            System.out.println(c("    (négatif = l'équipe rembourse ; > 10 = signal fort)", DIM));
        }
    }

    private static long delta(List<HistoryPoint> points) {
        return asLong(points.get(points.size() - 1).value()) - asLong(points.get(0).value());
    }

    /** Sonar stocke l'auteur et la date du dernier commit par ligne : churn sans Git. */
    private void reportBlame(String sample) {
        String fileKey = firstFileOf(sample);
        ScmResponse scm = (fileKey == null) ? null
                : sq.get("api/sources/scm", params("key", fileKey, "from", "1", "to", "200"))
                        .as(ScmResponse.class);

        if (scm == null || !notEmpty(scm.scm())) {
            System.out.println();
            System.out.println(c("""
                    \s Blame SCM inaccessible : il manque 'See Source Code' au token.
                      Sans lui, ni le churn ni l'âge du code ne sont calculables ici.""", YELLOW));
            return;
        }
        // Chaque entrée est positionnelle : [ligne, auteur, date, révision].
        // Attention : Sonar ne renvoie PAS une entrée par ligne — les lignes
        // consécutives partageant un même changeset sont regroupées sous la
        // première. Le compte porte donc sur des changesets, pas sur des lignes.
        List<LocalDateTime> dates = scm.scm().stream()
                .map(row -> row.size() > 2 ? parseDate(row.get(2)) : null)
                .filter(Objects::nonNull).sorted().toList();
        Set<String> authors = scm.scm().stream()
                .filter(row -> row.size() > 1 && !isBlank(row.get(1)))
                .map(row -> row.get(1)).collect(Collectors.toSet());

        // 200 + des lignes, mais tous les auteurs vides : Sonar a indexé le fichier
        // sans jamais recevoir de métadonnées SCM. C'est le cas 'fetch-depth: 1' —
        // un constat d'audit à part entière, pas une absence de résultat.
        if (authors.isEmpty()) {
            System.out.println();
            System.out.println(c("""
                    \s Blame SCM vide : le scanner tourne sans métadonnées SCM
                      (clone shallow en CI, type 'fetch-depth: 1'). Sonar ne peut donc
                      ni attribuer les issues à un auteur, ni calculer correctement le
                      new code. C'est un constat d'audit à part entière.""", YELLOW));
            return;
        }

        System.out.println();
        System.out.println(c("  Blame SCM accessible via l'API :", GREEN));
        System.out.printf("    Fichier témoin                : %s%n", shortName(fileKey));
        System.out.printf("    Auteurs distincts (%d changesets): %d%n",
                scm.scm().size(), authors.size());
        if (!dates.isEmpty()) {
            System.out.printf("    Dernière modification         : %s%n",
                    dates.get(dates.size() - 1).toLocalDate());
            System.out.printf("    Plus ancienne ligne           : %s%n",
                    dates.get(0).toLocalDate());
        }
        System.out.println(c("    → churn et âge du code calculables sans accès Git", DIM));
    }

    // ----------------------------------------------------------------------
    // Client HTTP
    // ----------------------------------------------------------------------

    static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Statut + corps brut. Le corps est toujours présent, y compris sur erreur. */
    record Response(int status, String body) {

        boolean unreachable() {
            return status == 0;
        }

        Verdict verdict() {
            return switch (status) {
                case 200 -> Verdict.OK;
                case 401, 403 -> Verdict.DENIED;
                case 404 -> Verdict.MISSING;
                default -> Verdict.ERROR;
            };
        }

        /** null si le statut n'est pas 200 ou si le corps n'est pas le JSON attendu. */
        <T> T as(Class<T> type) {
            if (status != 200 || body == null) return null;
            try {
                return MAPPER.readValue(body, type);
            } catch (IOException e) {
                return null;
            }
        }

        String errorMessage() {
            if (body == null) return "";
            try {
                ErrorResponse e = MAPPER.readValue(body, ErrorResponse.class);
                if (notEmpty(e.errors())) {
                    return truncate(e.errors().stream().map(ErrorMessage::msg)
                            .filter(Objects::nonNull).collect(Collectors.joining("; ")), 90);
                }
            } catch (IOException ignored) {
                // corps non-JSON : on le montre tel quel
            }
            return truncate(body.replace("\n", " "), 90);
        }
    }

    static final class Sonar {
        final String base;
        private final String token;
        private final String organization;
        private final Duration timeout;
        private final Path dumpDir;
        private final HttpClient client;
        int calls = 0;

        Sonar(String url, String token, String organization,
              int timeoutSeconds, boolean insecure, Path dumpDir) throws Exception {
            this.base = url.replaceAll("/+$", "");
            this.token = token;
            this.organization = isBlank(organization) ? null : organization;
            this.timeout = Duration.ofSeconds(timeoutSeconds);
            this.dumpDir = dumpDir;
            if (dumpDir != null) Files.createDirectories(dumpDir);

            HttpClient.Builder b = HttpClient.newBuilder()
                    .connectTimeout(this.timeout)
                    .followRedirects(HttpClient.Redirect.NORMAL);
            if (insecure) {
                System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
                b.sslContext(trustEverything());
            }
            this.client = b.build();
        }

        Response get(String path, Map<String, String> params) {
            Map<String, String> all = new LinkedHashMap<>(params);
            if (organization != null && needsOrganization(path)) {
                all.putIfAbsent("organization", organization);
            }
            String uri = base + "/" + path.replaceAll("^/+", "") + queryString(all);
            calls++;
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                        .GET()
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                dump(path, res.body());
                return new Response(res.statusCode(), res.body());
            } catch (Exception e) {
                // ConnectException.getMessage() est souvent null : sans le nom de la
                // classe, le diagnostic « impossible de joindre » n'indique pas
                // s'il s'agit d'un refus, d'un timeout ou d'un échec TLS.
                String msg = isBlank(e.getMessage())
                        ? e.getClass().getSimpleName()
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                return new Response(0, msg);
            }
        }

        /**
         * Endpoints qui exigent (ou acceptent) 'organization' sur SonarQube Cloud.
         *
         * api/projects/search en fait partie : sans le paramètre, Cloud répond 400 et
         * le diagnostic conclut à tort « nécessite Administer System » — soit un faux
         * point aveugle sur le calcul le plus important de l'outil.
         *
         * La liste reste volontairement courte : les endpoints portés par un composant
         * (measures/*, components/tree, sources/scm, project_analyses, settings/values)
         * n'acceptent pas le paramètre, et l'ajouter provoquerait l'erreur qu'on cherche
         * à éviter. Le paramètre n'est de toute façon envoyé que si --organization est
         * fourni, ce qui ne concerne que Cloud.
         *
         * NON VÉRIFIÉ sur une instance Cloud réelle : sonarcloud.io était injoignable
         * depuis l'environnement de test. Déduit de la documentation de l'API.
         */
        private static boolean needsOrganization(String path) {
            return path.startsWith("api/components/search_projects")
                    || path.startsWith("api/qualityprofiles")
                    || path.startsWith("api/projects/search")
                    || path.startsWith("api/issues/search")
                    || path.startsWith("api/qualitygates/get_by_project");
        }

        /**
         * Les captures brutes ne sont pas de l'échafaudage : elles servent à générer
         * les records, puis à diffuser les dérives de version d'une instance à l'autre.
         */
        private void dump(String path, String body) {
            if (dumpDir == null) return;
            String name = "%03d-%s.json".formatted(calls, path.replaceAll("[^A-Za-z0-9]+", "_"));
            try {
                Files.writeString(dumpDir.resolve(name), body == null ? "" : body);
            } catch (IOException e) {
                System.err.println("  (capture non écrite : " + e.getMessage() + ")");
            }
        }

        private static String queryString(Map<String, String> params) {
            if (params.isEmpty()) return "";
            return params.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&", "?", ""));
        }

        private static String encode(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }

        private static SSLContext trustEverything() throws Exception {
            TrustManager[] trustAll = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) { }
                public void checkServerTrusted(X509Certificate[] c, String a) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        }
    }

    // ----------------------------------------------------------------------
    // Records
    //
    // Tous les champs numériques sont boxés : une mesure absente doit rester
    // null et ne surtout pas se lire comme un zéro — « pas de couverture »
    // et « 0 % de couverture » sont deux diagnostics différents.
    // ----------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SystemStatus(String id, String version, String status) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Validation(Boolean valid) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CurrentUser(String login, String name, Permissions permissions, List<String> groups) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Permissions(List<String> global) { }

        List<String> globalPermissions() {
            return (permissions == null) ? List.of() : orEmptyList(permissions.global());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Paging(Integer pageIndex, Integer pageSize, Integer total) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Component(String key, String name, String qualifier,
                     String analysisDate, String leakPeriodDate) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProjectSearch(Paging paging, List<Component> components) { }

    /**
     * Attention : {@code value} est une chaîne, pas un nombre — l'API renvoie
     * « 12.5 », pas 12.5. Et les métriques new_* logent leur valeur sous
     * {@code period} (ou {@code periods} sur les versions plus anciennes).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Measure(String component, String metric, String value,
                   Period period, List<Period> periods) {

        String effectiveValue() {
            if (value != null) return value;
            if (period != null) return period.value();
            return notEmpty(periods) ? periods.get(0).value() : null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Period(Integer index, String value, Boolean bestValue) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MeasuresSearch(List<Measure> measures) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ComponentMeasures(ComponentWithMeasures component) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record ComponentWithMeasures(String key, List<Measure> measures) { }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchHistory(Paging paging, List<HistoryMeasure> measures) {

        List<HistoryPoint> pointsFor(String metric) {
            return orEmptyList(measures).stream()
                    .filter(m -> metric.equals(m.metric()))
                    .findFirst()
                    .map(m -> orEmptyList(m.history()).stream()
                            .filter(h -> !isBlank(h.value())).toList())
                    .orElse(List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoryMeasure(String metric, List<HistoryPoint> history) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HistoryPoint(String date, String value) { }

    /** issues/search expose le total à la racine, pas dans paging comme les autres. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record IssuesSearch(Integer total, Paging paging, List<Facet> facets) {

        String issueCount() {
            if (total != null) return String.valueOf(total);
            return (paging == null || paging.total() == null) ? "?" : String.valueOf(paging.total());
        }

        List<FacetValue> facetValues(String property) {
            return orEmptyList(facets).stream()
                    .filter(f -> property.equals(f.property()))
                    .findFirst().map(f -> orEmptyList(f.values())).orElse(List.of());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Facet(String property, List<FacetValue> values) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FacetValue(String val, Integer count) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SettingsValues(List<Setting> settings) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Setting(String key, String value, List<String> values, Boolean inherited) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QualityGateResponse(QualityGate qualityGate) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record QualityGate(String id, String name, Boolean isDefault) { }

        String gateName() {
            return (qualityGate == null) ? "" : orEmpty(qualityGate.name());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record QualityProfiles(List<Profile> profiles) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Profile(String key, String name, String language, String languageName) { }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Analyses(Paging paging, List<Analysis> analyses) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Analysis(String key, String date, String projectVersion, List<Event> events) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Event(String key, String category, String name) { }

    /**
     * sources/scm renvoie des tableaux positionnels : [ligne, auteur, date, révision].
     * Une entrée par changeset, pas par ligne : les lignes consécutives issues du
     * même commit sont regroupées sous la première.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScmResponse(List<List<String>> scm) {

        /** Un changeset sans auteur = fichier indexé sans métadonnées SCM. */
        String describe() {
            int n = size(scm);
            if (n == 0) return "aucun changeset";
            boolean anyAuthor = orEmptyList(scm).stream()
                    .anyMatch(row -> row.size() > 1 && !isBlank(row.get(1)));
            return anyAuthor ? n + " changeset(s)" : n + " changeset(s), aucun auteur (clone shallow ?)";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(List<ErrorMessage> errors) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorMessage(String msg) { }

    // ----------------------------------------------------------------------
    // Présentation
    // ----------------------------------------------------------------------

    static final String BOLD = "\033[1m", DIM = "\033[2m", RESET = "\033[0m";
    static final String GREEN = "\033[32m", RED = "\033[31m", YELLOW = "\033[33m";

    enum Verdict {
        OK(GREEN, "OK      "),
        DENIED(RED, "REFUSE  "),
        MISSING(YELLOW, "ABSENT  "),
        EMPTY(YELLOW, "VIDE    "),
        ERROR(RED, "ERREUR  ");

        final String color, tag;

        Verdict(String color, String tag) {
            this.color = color;
            this.tag = tag;
        }
    }

    static String c(String text, String color) {
        return ConsoleOut.color(text, color);
    }

    static void title(String text) {
        System.out.println();
        System.out.println(c("=== " + text + " ", BOLD)
                + c("=".repeat(Math.max(0, 66 - text.length())), BOLD));
    }

    static void line(String label, Verdict v, String detail) {
        System.out.printf("  %s %-42s %s%n", c(v.tag, v.color), label,
                isBlank(detail) ? "" : c(detail, DIM));
    }

    // ----------------------------------------------------------------------
    // Petits utilitaires
    // ----------------------------------------------------------------------

    /**
     * Sonar date les analyses avec un décalage : « 2026-08-16T05:43:07+0200 ».
     * Tronquer à 19 caractères jette ce décalage et compare ensuite une heure
     * locale à une heure distante — d'où des âges faux de quelques heures, et un
     * « il y a -1 jour » sur une analyse toute fraîche. On lit donc le décalage
     * quand il est présent et on ramène tout à l'heure locale.
     */
    static LocalDateTime parseDate(String s) {
        if (s == null || s.length() < 19) return null;
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (RuntimeException ignored) {
            // pas de décalage (ou format inattendu) : on retombe sur l'heure nue
        }
        try {
            return LocalDateTime.parse(s.substring(0, 19), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Map<String, String> params(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    static long asLong(String s) {
        try {
            return (long) Double.parseDouble(s);
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    static int orZero(Integer i) { return i == null ? 0 : i; }

    static int totalOf(Paging p) { return (p == null || p.total() == null) ? 0 : p.total(); }

    static String total(Paging p) { return (p == null || p.total() == null) ? "?" : String.valueOf(p.total()); }

    static int size(Collection<?> c) { return c == null ? 0 : c.size(); }

    static boolean isBlank(String s) { return s == null || s.isBlank(); }

    static boolean notEmpty(Collection<?> c) { return c != null && !c.isEmpty(); }

    static <T> List<T> orEmptyList(List<T> l) { return l == null ? List.of() : l; }

    static String orEmpty(String s) { return s == null ? "" : s; }

    static String orNa(String s) { return isBlank(s) ? "n/a" : s; }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String shortName(String key) {
        String tail = key.substring(key.lastIndexOf(':') + 1);
        return truncate(tail, 50);
    }
}
