///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Sonar.java
//SOURCES Csv.java

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            "  Capturer, puis rejouer hors ligne — ni URL ni jeton au rejeu :",
            "    SonarAuditCheck --csv inventaire.csv --dump-dir ./captures",
            "    SonarAuditCheck --replay-dir ./captures --csv rejoue.csv",
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

    @Option(names = "--comma",
            description = "CSV séparé par des virgules, sans BOM (pour un outil, pas Excel)")
    boolean comma;

    @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
    Path dumpDir;

    @Option(names = "--replay-dir",
            description = "rejouer un --dump-dir au lieu d'appeler l'instance (hors ligne)")
    Path replayDir;

    @Option(names = "--concurrency", defaultValue = "8",
            description = "appels en vol à la fois ; 1 = séquentiel "
                    + "(défaut : ${DEFAULT-VALUE})")
    int concurrency;

    @Option(names = "--main-branch-only",
            description = "ne lire que la branche principale, comme search_projects "
                    + "(sans chercher le dernier scan sur les autres branches)")
    boolean mainBranchOnly;

    @Option(names = "--no-bindings",
            description = "ne pas lire la liaison GitLab ni les liens de chaque projet "
                    + "(deux appels de moins par projet ; CrossAudit perd sa jointure exacte "
                    + "côté Sonar)")
    boolean noBindings;

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
        // En rejeu, l'instance n'est jamais appelée : exiger une URL et un jeton
        // interdirait le seul mode qui tourne sans elle.
        if (replayDir == null && (isBlank(url) || isBlank(token))) {
            System.err.println("SONAR_URL et SONAR_TOKEN sont requis "
                    + "(variables d'env ou --url/--token).");
            return 2;
        }
        if (replayDir != null && !Files.isDirectory(replayDir)) {
            System.err.println("--replay-dir : " + replayDir + " n'est pas un répertoire.");
            return 2;
        }
        sq = new Sonar(url, token, organization, timeout, insecure, dumpDir, replayDir);

        System.out.println(c("\nInstance : "
                + (sq.replaying() ? "rejeu de " + replayDir : sq.base), BOLD));

        CurrentUser me = checkConnectivity();
        if (me == null) return 1;

        String sample = (project != null) ? project : firstVisibleProject();

        // Avant la première mesure : savoir ce que cette instance sait nommer.
        negotiateMetrics();
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
        System.out.printf("  %d appels API effectués%s.%n", sq.calls(),
                concurrency > 1 && !sq.replaying() ? " (%d en vol)".formatted(concurrency) : "");
        // Une reprise silencieuse est une mesure qu'on ne sait pas justifier :
        // si l'instance a refusé puis accepté, le rapport doit le dire.
        if (sq.throttled() > 0 || sq.retried() > 0) {
            System.out.println(c("  dont %d ralenti(s) (429) et %d repris (5xx)."
                    .formatted(sq.throttled(), sq.retried()), DIM));
        }
        // En rejeu, une capture manquante est la seule panne possible — et elle
        // ressemblerait sinon à un parc plus petit qu'il n'est.
        if (sq.replaying() && sq.missingCaptures() > 0) {
            System.out.println(c("  %d capture(s) manquante(s) : le rejeu est incomplet."
                    .formatted(sq.missingCaptures()), YELLOW));
        }
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

        Sonar.Response status = sq.get("api/system/status", params());
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
            line("api/system/status", verdict(status), "HTTP " + status.status());
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

        Sonar.Response r = sq.get("api/users/current", params());
        CurrentUser me = r.as(CurrentUser.class);
        if (me == null) {
            line("api/users/current", verdict(r), r.errorMessage());
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

    /**
     * Les mesures demandées à SonarQube, et — c'est le même objet — les colonnes
     * du CSV. Il y avait ici deux listes à tenir d'accord, une pour l'appel et
     * une pour l'écriture : {@code sqale_index} figurait dans la première et pas
     * dans la seconde, donc il était demandé à chaque projet du parc puis jeté.
     * Une mesure absente du CSV se lit comme une mesure que SonarQube ne donne
     * pas, ce qui est faux et coûte cher : c'est la dette en valeur absolue,
     * celle sur laquelle GITLAB_ANALYSIS.md raisonne.
     *
     * L'ordre est celui du CSV. Quatre groupes :
     *
     *   1. taille et forme      — de quoi stratifier, et lire tout le reste ;
     *   2. tests et couverture  — voir la note ci-dessous ;
     *   3. dette et défauts     — les comptes derrière les notes A–E ;
     *   4. code neuf            — ce qui se crée, plutôt que ce qui est porté.
     *
     * **Pourquoi autant autour de la couverture.** {@code coverage} seule ne
     * distingue pas les deux situations qui appellent des actions opposées :
     * une équipe qui n'a pas de tests, et une équipe qui en a mais dont le
     * rapport de couverture n'arrive pas jusqu'à SonarQube. {@code tests} et
     * {@code lines_to_cover} les séparent — 0 % avec 400 tests est un problème
     * de tuyauterie CI, 0 % avec 0 test est un problème d'ingénierie — et
     * {@code uncovered_lines} donne enfin un volume à l'effort, là où un
     * pourcentage sur un projet de 200 lignes et un sur un monolithe se
     * ressemblent.
     *
     * **Pourquoi les comptes en plus des notes.** {@code *_rating} est ordinal :
     * un 4 ne vaut pas deux 2, et la moyenne d'une colonne de notes n'a pas de
     * sens. {@code bugs}, {@code vulnerabilities}, {@code code_smells} se
     * somment, se comparent, se rapportent à {@code ncloc}.
     */
    private static final List<String> METRIC_COLUMNS = List.of(
            // 1. Taille et forme
            "ncloc", "files", "complexity", "cognitive_complexity",
            "comment_lines_density",
            "duplicated_lines_density", "new_duplicated_lines_density",
            // 2. Tests et couverture
            "coverage", "line_coverage", "branch_coverage", "new_coverage",
            "lines_to_cover", "uncovered_lines",
            "tests", "test_failures", "test_errors", "skipped_tests",
            "test_success_density",
            // 3. Dette et défauts
            "sqale_index", "sqale_debt_ratio", "sqale_rating",
            "bugs", "reliability_rating",
            "vulnerabilities", "security_rating",
            "security_hotspots", "security_hotspots_reviewed",
            "code_smells", "violations",
            // 4. Code neuf
            "new_lines", "new_violations", "new_bugs", "new_vulnerabilities",
            "new_code_smells",
            "alert_status");

    /**
     * Ce qu'on demandera réellement : {@link #METRIC_COLUMNS} restreint aux
     * métriques que *cette* instance connaît.
     *
     * {@code api/measures/search} refuse la requête entière — 400 — dès qu'une
     * seule clé lui est inconnue. Une métrique retirée ou renommée d'une version
     * de SonarQube à l'autre ferait donc perdre les treize autres, et l'audit
     * afficherait un parc sans aucune mesure plutôt qu'une mesure de moins. On
     * demande la liste une fois, et on n'envoie que l'intersection.
     */
    private List<String> supportedMetrics = METRIC_COLUMNS;
    private List<String> unsupportedMetrics = List.of();

    private String metricKeys() {
        return String.join(",", supportedMetrics);
    }

    /**
     * Un appel, paginé, avant toute mesure. Si l'instance ne répond pas, on garde
     * la liste complète : le comportement d'avant, pas une dégradation de plus.
     */
    private void negotiateMetrics() {
        Set<String> known = new HashSet<>();
        for (int page = 1; page <= 10; page++) {
            MetricsSearch m = sq.get("api/metrics/search",
                    params("ps", "500", "p", String.valueOf(page))).as(MetricsSearch.class);
            if (m == null || m.metrics() == null || m.metrics().isEmpty()) break;
            m.metrics().forEach(x -> known.add(x.key()));
            Paging pg = m.paging();
            if (pg != null && pg.total() != null && known.size() >= pg.total()) break;
        }
        if (known.isEmpty()) return;
        supportedMetrics = METRIC_COLUMNS.stream().filter(known::contains).toList();
        unsupportedMetrics = METRIC_COLUMNS.stream().filter(k -> !known.contains(k)).toList();
        if (!unsupportedMetrics.isEmpty()) {
            System.out.printf("  Métriques inconnues de cette instance : %s%n",
                    c(String.join(", ", unsupportedMetrics), YELLOW));
            System.out.println(c("    Retirées de la requête. Demandées quand même, "
                    + "elles feraient échouer", DIM));
            System.out.println(c("    l'appel entier et le parc ressortirait sans "
                    + "aucune mesure.", DIM));
        }
    }

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
                params("projectKeys", sample, "metricKeys", metricKeys()));

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

        probe("Liaison DevOps (alm_settings/get_binding)", "api/alm_settings/get_binding",
                AlmBinding.class, b -> b.alm() == null ? "aucune liaison"
                        : "%s, dépôt %s".formatted(b.alm(), orEmpty(b.repository())),
                params("project", sample));

        probe("Liens du projet (project_links/search)", "api/project_links/search",
                ProjectLinks.class, l -> size(l.links()) + " lien(s)",
                params("projectKey", sample));

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
        Sonar.Response r = sq.get(path, params);
        Verdict v = verdict(r);
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
        projects = resolveLatestBranch(projects);
        reportFreshness(projects);

        Map<String, Map<String, String>> measures = fetchMeasures(projects);
        reportMissingCoverage(projects, measures);

        Map<String, Attachments> attachments = fetchAttachments(projects);

        if (csv != null) {
            writeCsv(projects, measures, attachments);
            System.out.println();
            System.out.println("  CSV écrit : " + c(csv.toString(), BOLD));
            System.out.println(c(Csv.openingHint(csv, comma), DIM));
        }
    }

    private List<Component> fetchAllProjects() {
        List<Component> all = new ArrayList<>();
        int page = 1, pageSize = 500, guard = 40;
        while (page <= guard) {
            Sonar.Response r = sq.get("api/components/search_projects",
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

    /**
     * search_projects et measures/search ne connaissent que la branche principale.
     * Un projet dont seule une autre branche (develop, …) est analysée y apparaît
     * sans date ni mesure, exactement comme un projet jamais analysé. On demande
     * donc ses branches à chaque projet et on retient celle dont l'analyse est la
     * plus récente — c'est le dernier scan qui intéresse l'audit, pas la branche.
     */
    private List<Component> resolveLatestBranch(List<Component> projects) {
        if (mainBranchOnly) return projects;
        // Un appel par projet : c'est la passe la plus chère de l'outil, et la
        // seule dont le coût grandit avec le parc. Elle se recouvre, sinon un
        // parc de 500 projets attend 500 fois le réseau, l'un après l'autre.
        Progress bar = new Progress("  Branches lues", projects.size());
        List<Component> out = sq.map(projects, concurrency, p -> {
            Branch b = latestBranch(p.key());
            bar.tick();
            return b == null ? p : p.onBranch(b.name(), Boolean.TRUE.equals(b.isMain()),
                    b.analysisDate());
        });
        bar.done();

        int nonMain = 0, rescued = 0;
        for (int i = 0; i < out.size(); i++) {
            Component p = out.get(i);
            if (p.onOtherBranch()) {
                nonMain++;
                if (projects.get(i).analysisDate() == null) rescued++;
            }
        }
        System.out.println("  Dernier scan sur une branche non principale : "
                + c(String.valueOf(nonMain), BOLD));
        if (rescued > 0) {
            System.out.println(c("    dont %d sans aucune analyse de la branche principale"
                    .formatted(rescued), DIM));
            System.out.println(c("    (invisibles dans search_projects et measures/search)", DIM));
        }
        return out;
    }

    /** Branche dont la dernière analyse est la plus récente, ou null si indisponible. */
    private Branch latestBranch(String key) {
        BranchList bl = sq.get("api/project_branches/list", params("project", key))
                .as(BranchList.class);
        if (bl == null) return null;
        return orEmptyList(bl.branches()).stream()
                .filter(b -> parseDate(b.analysisDate()) != null)
                .max(Comparator.comparing(b -> parseDate(b.analysisDate())))
                .orElse(null);
    }

    /** Mesures d'une branche précise : measures/search ne sait lire que la principale. */
    private Map<String, String> branchMeasures(String key, String branch) {
        Map<String, String> out = new HashMap<>();
        ComponentMeasures cm = sq.get("api/measures/component",
                        params("component", key, "branch", branch, "metricKeys", metricKeys()))
                .as(ComponentMeasures.class);
        if (cm == null || cm.component() == null) return out;
        for (Measure m : orEmptyList(cm.component().measures())) {
            out.put(m.metric(), m.effectiveValue());
        }
        return out;
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
        List<String> keys = projects.stream()
                .filter(p -> !p.onOtherBranch()).map(Component::key).toList();
        for (int i = 0; i < keys.size(); i += 10) {
            List<String> chunk = keys.subList(i, Math.min(i + 10, keys.size()));
            MeasuresSearch m = sq.get("api/measures/search",
                            params("projectKeys", String.join(",", chunk), "metricKeys", metricKeys()))
                    .as(MeasuresSearch.class);
            if (m == null) continue;
            for (Measure measure : orEmptyList(m.measures())) {
                byKey.computeIfAbsent(measure.component(), k -> new HashMap<>())
                        .put(measure.metric(), measure.effectiveValue());
            }
        }
        // measures/search ignore les branches : chaque projet lu ailleurs que sur
        // la principale coûte son propre appel. Même raison qu'au-dessus de les
        // faire se recouvrir.
        List<Component> others = projects.stream().filter(Component::onOtherBranch).toList();
        if (!others.isEmpty()) {
            Progress bar = new Progress("  Mesures de branche", others.size());
            List<Map<String, String>> read = sq.map(others, concurrency, p -> {
                Map<String, String> m = branchMeasures(p.key(), p.branch());
                bar.tick();
                return m;
            });
            bar.done();
            for (int i = 0; i < others.size(); i++) byKey.put(others.get(i).key(), read.get(i));
        }
        return byKey;
    }

    /**
     * Ce que SonarQube sait lui-même du dépôt : la liaison DevOps, posée quand
     * le projet a été importé depuis GitLab, et les liens saisis à la main.
     *
     * La liaison donne l'identifiant du projet GitLab — la seule jointure qui
     * ne dépend d'aucun nom. Elle n'existe que pour les projets créés ou liés
     * par l'intégration : un projet né d'un `-Dsonar.projectKey` en CI n'en a
     * pas, et c'est précisément ce que ce comptage mesure.
     *
     * Les liens sont saisis par quelqu'un, pas vérifiés par l'instance : ils
     * sortent dans le CSV pour que CrossAudit mesure s'ils servent, pas pour
     * faire une jointure.
     */
    private Map<String, Attachments> fetchAttachments(List<Component> projects) {
        if (noBindings || projects.isEmpty()) return Map.of();
        Progress bar = new Progress("  Liaisons et liens", projects.size());
        List<Attachments> read = sq.map(projects, concurrency, p -> {
            Attachments a = attachments(p.key());
            bar.tick();
            return a;
        });
        bar.done();

        Map<String, Attachments> byKey = new HashMap<>();
        Map<String, Long> byAlm = new TreeMap<>();
        long refused = 0, withLinks = 0;
        for (int i = 0; i < projects.size(); i++) {
            Attachments a = read.get(i);
            byKey.put(projects.get(i).key(), a);
            if (a.refused()) refused++;
            else if (!a.alm().isEmpty()) byAlm.merge(a.alm(), 1L, Long::sum);
            if (!a.links().isEmpty()) withLinks++;
        }
        System.out.println();
        long bound = byAlm.values().stream().mapToLong(Long::longValue).sum();
        System.out.printf("  Liés à une plateforme DevOps   : %s / %d%s%n",
                c(String.valueOf(bound), BOLD), projects.size(),
                byAlm.isEmpty() ? "" : "  " + byAlm);
        if (refused > 0) {
            System.out.println(c("    dont %d liaison(s) illisible(s) : lecture refusée, pas absente"
                    .formatted(refused), YELLOW));
        }
        System.out.printf("  Avec au moins un lien saisi    : %d / %d%n", withLinks, projects.size());
        return byKey;
    }

    private Attachments attachments(String key) {
        Sonar.Response r = sq.get("api/alm_settings/get_binding", params("project", key));
        String alm;
        String repository = "";
        if (r.ok()) {
            AlmBinding b = r.as(AlmBinding.class);
            // En rejeu, un 404 capturé revient en 200 avec son corps d'erreur :
            // une liaison sans plateforme est une absence, pas une lecture ratée.
            alm = b == null || b.alm() == null ? "" : b.alm().toLowerCase(Locale.ROOT);
            repository = b == null ? "" : orEmpty(b.repository());
        } else if (r.status() == 404) {
            alm = "";
        } else {
            alm = Attachments.REFUSED + " (HTTP " + r.status() + ")";
        }

        ProjectLinks pl = sq.get("api/project_links/search", params("projectKey", key))
                .as(ProjectLinks.class);
        String links = pl == null ? "" : orEmptyList(pl.links()).stream()
                .filter(l -> !isBlank(l.url()))
                .map(l -> (isBlank(l.type()) ? "autre" : l.type()) + " " + l.url().trim())
                .collect(Collectors.joining(" | "));
        return new Attachments(alm, repository, links);
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

    private static final List<String> BRANCH_COLUMNS = List.of(
            "branch", "is_main_branch", "main_branch_analysisDate");

    private static final List<String> ATTACHMENT_COLUMNS = List.of(
            "alm", "alm_repository", "liens");

    private void writeCsv(List<Component> projects,
                          Map<String, Map<String, String>> measures,
                          Map<String, Attachments> attachments) throws IOException {
        List<String> header = new ArrayList<>(
                List.of("key", "name", "analysisDate", "days_since_analysis"));
        header.addAll(METRIC_COLUMNS);
        header.addAll(BRANCH_COLUMNS);
        header.addAll(ATTACHMENT_COLUMNS);

        LocalDateTime now = LocalDateTime.now();
        // Le même écrivain que les trois autres outils : un inventaire qui
        // s'ouvre de travers dans Excel passe pour un outil cassé, et personne
        // ne fait le détour par l'assistant d'import. Les outils qui relisent ce
        // fichier reniflent le séparateur, donc le défaut lisible ne leur coûte
        // rien ; --comma reste là pour qui veut l'autre forme.
        try (CSVWriter w = Csv.writer(csv, comma)) {
            w.writeNext(header.toArray(String[]::new));
            for (Component p : projects) {
                w.writeNext(csvRow(p, measures.getOrDefault(p.key(), Map.of()),
                        attachments.get(p.key()), now));
            }
        }
    }

    private String[] csvRow(Component p, Map<String, String> m, Attachments a,
                            LocalDateTime now) {
        LocalDateTime d = parseDate(p.analysisDate());
        List<String> row = new ArrayList<>(List.of(
                orEmpty(p.key()),
                orEmpty(p.name()),
                orEmpty(p.analysisDate()),
                d == null ? "" : String.valueOf(ChronoUnit.DAYS.between(d, now))));
        METRIC_COLUMNS.forEach(metric -> row.add(orEmpty(m.get(metric))));
        row.add(orEmpty(p.branch()));
        row.add(p.isMain() == null ? "" : String.valueOf(p.isMain()));
        row.add(orEmpty(p.mainBranchAnalysisDate()));
        // Colonnes vides avec --no-bindings : non lues, ce que l'en-tête ne dit
        // pas mais que la console a dit.
        row.add(a == null ? "" : a.alm());
        row.add(a == null ? "" : a.repository());
        row.add(a == null ? "" : a.links());
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
        Sonar.Response r = sq.get("api/project_analyses/search",
                params("project", sample, "ps", "500", "from", since));
        Analyses a = r.as(Analyses.class);
        if (a == null) {
            line("api/project_analyses/search", verdict(r), "HTTP " + r.status());
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
        Sonar.Response r = sq.get("api/issues/search",
                params("componentKeys", sample, "createdAfter", since, "facets", "author", "ps", "1"));
        IssuesSearch is = r.as(IssuesSearch.class);
        if (is == null) {
            line("Facette 'author' sur issues", verdict(r), "HTTP " + r.status());
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

        Velocity v = velocity(h.pointsFor("ncloc"), h.pointsFor("sqale_index"));
        System.out.println();
        if (v == null) {
            System.out.println(c("  Vélocité de dette indisponible : moins de deux analyses "
                    + "chiffrées sur la fenêtre.", DIM));
            return;
        }
        // La fenêtre demandée et la fenêtre mesurée ne coïncident jamais : les
        // points sont ceux des analyses, pas ceux du calendrier. On affiche
        // l'écart réellement mesuré, sinon le lecteur rapporte à 90 jours une
        // variation qui en couvre 12.
        System.out.printf("  Δ lignes sur %d j (%d j mesurés) : %+d%n",
                days, v.spanDays(), v.deltaNcloc());
        System.out.printf("  Δ dette sur %d j (min)       : %+d%n", days, v.deltaDebt());
        if (v.deltaNcloc() > 0) {
            System.out.println("  Dette ajoutée par ligne écrite  : "
                    + c("%+.2f min/LOC".formatted(v.debtPerLine()), BOLD));
            System.out.println(c("    (négatif = l'équipe rembourse ; > 10 = signal fort)", DIM));
        }
    }

    /**
     * Ce que l'historique dit, séparé de la façon de le dire.
     *
     * {@code spanDays} n'est pas décoratif : deux analyses espacées de douze
     * jours dans une fenêtre de quatre-vingt-dix produisent un Δ parfaitement
     * réel et parfaitement trompeur si on le lit comme un trimestre.
     */
    record Velocity(long deltaNcloc, long deltaDebt, long spanDays,
                    String from, String to) {

        double debtPerLine() { return (double) deltaDebt / deltaNcloc; }
    }

    /**
     * {@code null} dès qu'un des deux bouts manque — et surtout pas zéro.
     *
     * L'ancienne version lisait la première et la dernière valeur avec un
     * parseur qui rendait 0 sur tout ce qu'il ne comprenait pas. Un projet dont
     * l'historique est illisible ressortait donc « +0 ligne, +0 minute de
     * dette » : le portrait exact d'une équipe irréprochable. C'est la confusion
     * absent / zéro que ce dépôt documente partout ailleurs, et elle devient
     * dangereuse le jour où ce calcul alimente une colonne de CSV plutôt qu'une
     * ligne de console.
     */
    static Velocity velocity(List<HistoryPoint> ncloc, List<HistoryPoint> debt) {
        Long dNcloc = delta(ncloc);
        Long dDebt = delta(debt);
        if (dNcloc == null || dDebt == null) return null;

        LocalDateTime from = parseDate(ncloc.get(0).date());
        LocalDateTime to = parseDate(ncloc.get(ncloc.size() - 1).date());
        long span = (from == null || to == null) ? 0 : ChronoUnit.DAYS.between(from, to);
        return new Velocity(dNcloc, dDebt, span,
                orEmpty(ncloc.get(0).date()), orEmpty(ncloc.get(ncloc.size() - 1).date()));
    }

    /** Écart entre le premier et le dernier point chiffré, ou null s'il en manque un. */
    static Long delta(List<HistoryPoint> points) {
        if (points == null || points.size() < 2) return null;
        Double first = numeric(points.get(0).value());
        Double last = numeric(points.get(points.size() - 1).value());
        if (first == null || last == null) return null;
        return (long) (last - first);
    }

    /** {@code null} sur une valeur absente ou illisible : jamais 0. */
    static Double numeric(String s) {
        if (isBlank(s)) return null;
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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

    /**
     * Les trois derniers champs ne viennent pas de l'API : resolveLatestBranch()
     * les pose quand il substitue la branche la plus récente à la principale.
     * {@code analysisDate} porte alors la date de cette branche, et
     * {@code mainBranchAnalysisDate} ce que search_projects avait répondu.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Component(String key, String name, String qualifier,
                     String analysisDate, String leakPeriodDate,
                     String branch, Boolean isMain, String mainBranchAnalysisDate) {

        Component onBranch(String branch, boolean isMain, String branchAnalysisDate) {
            return new Component(key, name, qualifier, branchAnalysisDate, leakPeriodDate,
                    branch, isMain, analysisDate);
        }

        boolean onOtherBranch() { return branch != null && Boolean.FALSE.equals(isMain); }
    }

    /** Pour GitLab, {@code repository} est l'identifiant numérique du projet. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record AlmBinding(String key, String alm, String repository, String slug, String url,
                      Boolean monorepo) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProjectLinks(List<ProjectLink> links) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProjectLink(String type, String name, String url) { }

    /** Ce que l'inventaire écrit de la liaison et des liens d'un projet. */
    record Attachments(String alm, String repository, String links) {
        static final String REFUSED = "illisible";

        boolean refused() { return alm.startsWith(REFUSED); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Branch(String name, Boolean isMain, String type, String analysisDate) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BranchList(List<Branch> branches) { }

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
    record MetricsSearch(Paging paging, List<Metric> metrics) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Metric(String key, String name, String type) { }

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

    /**
     * Une passe qui dure des minutes sans rien écrire ne se distingue pas d'un
     * blocage. Sur un terminal on réécrit la même ligne ; ailleurs — CI, sortie
     * redirigée — on se tait, parce qu'un fichier de log n'a que faire de 500
     * lignes de compteur.
     */
    static final class Progress {
        private final String label;
        private final int total;
        private final java.util.concurrent.atomic.AtomicInteger done =
                new java.util.concurrent.atomic.AtomicInteger();
        private final boolean live = ConsoleOut.colors();

        Progress(String label, int total) {
            this.label = label;
            this.total = total;
            if (live && total > 0) System.out.printf("%s 0/%d\r", label, total);
        }

        void tick() {
            int n = done.incrementAndGet();
            if (live && (n % 25 == 0 || n == total)) {
                System.out.printf("%s %d/%d\r", label, n, total);
                System.out.flush();
            }
        }

        void done() {
            if (live && total > 0) System.out.printf("%s %d/%d%n", label, done.get(), total);
        }
    }

    /**
     * Le verdict est une affaire de présentation, pas de transport : le client
     * rapporte un statut, l'audit décide de ce qu'il en dit.
     */
    static Verdict verdict(Sonar.Response r) {
        return switch (r.status()) {
            case 200 -> Verdict.OK;
            case 401, 403 -> Verdict.DENIED;
            case 404 -> Verdict.MISSING;
            default -> Verdict.ERROR;
        };
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
