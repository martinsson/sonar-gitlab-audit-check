///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Gitlab.java
//SOURCES Csv.java

import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.CSVWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pré-sélection d'un parc GitLab pour analyse approfondie des pratiques.
 *
 * Le problème : l'analyse en profondeur coûte ~25 appels par projet. Sur 2000
 * projets, c'est 50 000 appels et un rapport que personne ne lit. Cet outil
 * dépense ~90 à 850 appels pour choisir les ~200 projets qui la méritent, et
 * — ce qui compte davantage — rend ce choix auditable.
 *
 * L'entonnoir (cf. GITLAB_ANALYSIS.md §2) :
 *
 *   étape 0  inventaire, exclusions dures      ~20 appels
 *   étape 1  filtre de fraîcheur               0 appel   (+ échantillon de validation)
 *   étape 2  activité réelle en commits        ~40 (GraphQL) ou ~800 (REST)
 *   étape 3  plancher d'activité               0 appel
 *   étape 4  sélection par quotas              0 appel
 *
 * Puis, optionnellement, --deep : les signaux de pratique sur les sélectionnés.
 *
 * Usage :
 *   export GITLAB_URL=https://gitlab.example.com
 *   export GITLAB_TOKEN=glpat-xxxxxxxx        # portée read_api suffit
 *   jbang GitlabActivityAudit.java --group mon/groupe
 *   jbang GitlabActivityAudit.java --top 200 --csv inventaire.csv
 *   jbang GitlabActivityAudit.java --group mon/groupe --deep --pratiques prat.csv
 *
 * Lecture seule de bout en bout : aucune requête autre que GET (et le POST
 * GraphQL de l'étape 2, qui ne fait que lire).
 */
@Command(name = "GitlabActivityAudit", mixinStandardHelpOptions = true,
        sortOptions = false, usageHelpAutoWidth = true,
        description = "Pré-sélection par activité de commits, puis signaux de pratique.",
        synopsisHeading = "",
        customSynopsis = {
            "Usage : GitlabActivityAudit [--group <chemin>] [--deep] [--out-dir <rép>]",
            "                            [autres options]",
        },
        footer = {
            "",
            "Cas d'usage courants :",
            "",
            "  L'audit complet d'un groupe — c'est la commande à retenir :",
            "    GitlabActivityAudit --group mon/groupe --deep --out-dir ./audit",
            "",
            "  La sélection seule, sans la passe coûteuse — pour dimensionner :",
            "    GitlabActivityAudit --group mon/groupe --out-dir ./audit",
            "",
            "  Tout l'instance, budget réduit parce que la passe profonde coûte cher :",
            "    GitlabActivityAudit --deep --top 150 --out-dir ./audit",
            "",
            "  Une liste de projets, sur un an au lieu de 90 jours :",
            "    GitlabActivityAudit --projects liste.txt --since 365 \\",
            "        --deep --out-dir ./audit",
            "",
            "  Sans --out-dir, rien n'est écrit : le rapport s'affiche et repart.",
            "",
            "Deux fichiers en sortie, à lire ensemble : inventaire.csv (le parc et",
            "ce qui en a été écarté) et pratiques.csv (les projets sélectionnés).",
            "Le dictionnaire des colonnes est dans COLUMNS.md, la méthode dans",
            "GITLAB_ANALYSIS.md.",
            "",
            "GITLAB_URL et GITLAB_TOKEN peuvent remplacer --url et --token.",
            "Le jeton n'a besoin que de la portée read_api ; son porteur doit être",
            "Reporter sur les projets à mesurer.",
        })
public class GitlabActivityAudit implements Callable<Integer> {

    // ----------------------------------------------------------------------
    // Options
    // ----------------------------------------------------------------------

    @Option(names = "--url", defaultValue = "${env:GITLAB_URL}",
            description = "URL de l'instance GitLab")
    String url;

    @Option(names = "--token", defaultValue = "${env:GITLAB_TOKEN}",
            description = "jeton personnel glpat-... (portée read_api)")
    String token;

    @Option(names = "--group", description = "chemin complet d'un groupe (sinon : toute l'instance)")
    String group;

    @Option(names = "--include-subgroups", defaultValue = "true",
            description = "descendre dans les sous-groupes (défaut : ${DEFAULT-VALUE})")
    boolean includeSubgroups;

    @Option(names = "--projects", description = "fichier de chemins de projets, un par ligne")
    Path projectsFile;

    @Option(names = "--since", defaultValue = "90",
            description = "fenêtre d'activité en jours (défaut : ${DEFAULT-VALUE})")
    int sinceDays;

    @Option(names = "--top", defaultValue = "200",
            description = "budget d'analyse approfondie (défaut : ${DEFAULT-VALUE})")
    int top;

    @Option(names = "--floor", defaultValue = "-1",
            description = "plancher d'activité en commits ; -1 = déduit des données")
    int floor;

    @Option(names = "--deep", description = "mesurer aussi les pratiques des projets "
            + "sélectionnés (plus long, plus d'appels)")
    boolean deep;

    @Option(names = "--out-dir",
            description = "répertoire de sortie : inventaire.csv, plus pratiques.csv "
                    + "avec --deep")
    Path outDir;

    @Option(names = "--include-archived", description = "inclure les projets archivés")
    boolean includeArchived;

    @Option(names = "--validation-sample", defaultValue = "30",
            description = "projets tirés SOUS le filtre de fraîcheur pour le valider")
    int validationSample;

    @Option(names = "--graphql", defaultValue = "true",
            description = "tenter la route GraphQL à l'étape 2 (repli REST automatique)")
    boolean graphql;

    @Option(names = "--seed", defaultValue = "20260818",
            description = "graine du tirage aléatoire (reproductibilité)")
    long seed;

    @Option(names = "--csv", description = "chemin explicite du CSV d'inventaire "
            + "(sinon : --out-dir)")
    Path csv;

    @Option(names = "--pratiques", description = "chemin explicite du CSV de pratiques "
            + "(implique --deep ; l'inventaire est écrit à côté)")
    Path pratiquesCsv;

    @Option(names = "--bot-pattern", defaultValue = Gitlab.BOT_PATTERN,
            description = "regex identifiant les auteurs automatiques")
    String botPattern;

    @Option(names = "--comma",
            description = "CSV séparés par des virgules, sans BOM (pour un outil, pas Excel)")
    boolean comma;

    @Option(names = "--max-commit-pages", defaultValue = "10",
            description = "pages de commits max par projet (100/page)")
    int maxCommitPages;

    @Option(names = "--timeout", defaultValue = "30",
            description = "délai HTTP en secondes (défaut : ${DEFAULT-VALUE})")
    int timeout;

    @Option(names = "--insecure", description = "ignorer la validation TLS")
    boolean insecure;

    @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
    Path dumpDir;

    @Option(names = "--color", defaultValue = "auto",
            description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
    String colorMode;

    public static void main(String[] args) {
        ConsoleOut.install();
        System.exit(new CommandLine(new GitlabActivityAudit()).execute(args));
    }

    // ----------------------------------------------------------------------
    // Orchestration
    // ----------------------------------------------------------------------

    private Gitlab gl;
    private Pattern bots;
    private OffsetDateTime windowStart;
    private final Counters counters = new Counters();

    @Override
    public Integer call() throws Exception {
        ConsoleOut.colorMode(colorMode);
        if (isBlank(url) || isBlank(token)) {
            System.err.println("GITLAB_URL et GITLAB_TOKEN sont requis "
                    + "(variables d'env ou --url/--token).");
            return 2;
        }
        gl = new Gitlab(url, token, timeout, insecure, dumpDir);
        bots = Pattern.compile(botPattern);
        windowStart = OffsetDateTime.now(ZoneOffset.UTC).minusDays(sinceDays);
        resolveOutputs();

        System.out.println(c("\nInstance : " + gl.base, BOLD));

        if (!preflight()) return 1;

        List<Proj> all = inventory();
        if (all.isEmpty()) {
            System.out.println(c("\n  Aucun projet dans le périmètre. Rien à faire.", YELLOW));
            return 1;
        }

        List<Proj> fresh = recencyGate(all);
        validateRecencyGate(all);
        commitActivity(fresh);
        activityFloor(fresh);
        select(fresh);

        if (csv != null) writeInventory(all);
        if (deep) new DeepPass(all).run();

        summary(all);
        return 0;
    }

    /**
     * L'inventaire n'est pas une sortie facultative du mode --deep : c'est lui
     * qui porte les dénominateurs — projets exclus et pour quel motif, cohorte
     * sous le plancher, témoin aléatoire. Sans lui, pratiques.csv est une liste
     * de projets sans le parc dont ils sont tirés, et les pourcentages qu'on en
     * tire n'ont pas de population.
     *
     * Demander --pratiques sans --csv écrivait donc les pratiques seules. On
     * écrit désormais l'inventaire à côté, dans le même répertoire.
     */
    private void resolveOutputs() throws IOException {
        if (pratiquesCsv != null) deep = true;
        if (outDir != null) {
            Files.createDirectories(outDir);
            if (csv == null) csv = outDir.resolve("inventaire.csv");
            if (deep && pratiquesCsv == null) pratiquesCsv = outDir.resolve("pratiques.csv");
        }
        if (csv == null && pratiquesCsv != null) {
            Path dir = pratiquesCsv.toAbsolutePath().getParent();
            csv = (dir == null ? Path.of("") : dir).resolve("inventaire.csv");
        }
        // Un chemin explicite vers un répertoire inexistant échouait à l'écriture,
        // après la passe complète : tout le travail d'API perdu sur un mkdir.
        for (Path out : new Path[]{csv, pratiquesCsv}) {
            if (out == null) continue;
            Path dir = out.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
        }
    }

    // ----------------------------------------------------------------------
    // Préambule : connectivité, identité, licence
    // ----------------------------------------------------------------------

    private boolean preflight() {
        title("0. Connectivité, identité, licence");

        Gitlab.Response me = gl.get("user", Map.of());
        if (me.unreachable()) {
            line("api/v4/user", Verdict.ERROR, me.errorMessage());
            System.out.println(c("\n  Instance injoignable. Vérifie l'URL, le proxy, le TLS.", RED));
            return false;
        }
        if (me.status() != 200) {
            line("api/v4/user", Verdict.DENIED, "HTTP " + me.status() + " — jeton refusé");
            System.out.println(c("\n  Le jeton n'est pas accepté. La portée read_api suffit,", RED));
            System.out.println(c("  mais un jeton expiré ou révoqué donne le même 401.", RED));
            return false;
        }
        JsonNode u = me.json();
        line("api/v4/user", Verdict.OK, "%s (%s)".formatted(
                text(u, "username"), text(u, "name")));

        // Le plan conditionne la moitié des signaux de pratique : approbations,
        // DORA, règles de push. Le découvrir après coup, c'est réécrire l'analyse.
        Gitlab.Response meta = gl.get("metadata", Map.of());
        if (meta.status() == 200) {
            JsonNode m = meta.json();
            boolean enterprise = m.path("enterprise").asBoolean(false);
            line("api/v4/metadata", enterprise ? Verdict.OK : Verdict.EMPTY,
                    "GitLab %s · %s".formatted(text(m, "version"),
                            enterprise ? "Enterprise" : "Community — DORA et approbations indisponibles"));
            counters.enterprise = enterprise;
        } else {
            line("api/v4/metadata", Verdict.MISSING,
                    "HTTP " + meta.status() + " — plan inconnu, on suppose Community");
        }
        return true;
    }

    // ----------------------------------------------------------------------
    // Étape 0 : inventaire et exclusions dures
    // ----------------------------------------------------------------------

    private List<Proj> inventory() throws IOException {
        title("1. Inventaire (étape 0)");

        List<JsonNode> raw;
        if (projectsFile != null) {
            raw = fromFile();
        } else if (!isBlank(group)) {
            raw = gl.paged("groups/" + enc(group) + "/projects", params(
                    "include_subgroups", String.valueOf(includeSubgroups),
                    "with_shared", "false",
                    "statistics", "true",
                    "archived", includeArchived ? null : "false"));
        } else {
            raw = gl.paged("projects", params(
                    "statistics", "true",
                    "archived", includeArchived ? null : "false",
                    "order_by", "id", "sort", "asc"));
        }

        List<Proj> all = raw.stream().map(Proj::of).collect(Collectors.toList());
        System.out.printf("  Projets retournés               : %d%n", all.size());

        // Les exclusions sont comptées, jamais escamotées. Un parc où 30 % des
        // dépôts sont des miroirs n'est pas le même parc que 30 % d'archivés,
        // et un total sans dénominateur se lit comme complet alors qu'il ne l'est pas.
        for (Proj p : all) {
            if (p.archived && !includeArchived) p.excluded = "archivé";
            else if (p.emptyRepo) p.excluded = "dépôt vide";
            else if (p.mirror) p.excluded = "miroir";
            else if (p.forkedFrom != null && p.lastActivity != null && p.created != null
                    && p.lastActivity.isBefore(p.created.plusDays(1))) p.excluded = "fork non divergé";
            else if (isBlank(p.defaultBranch)) p.excluded = "sans branche par défaut";
        }
        Map<String, Long> byReason = all.stream().filter(p -> p.excluded != null)
                .collect(Collectors.groupingBy(p -> p.excluded, TreeMap::new, Collectors.counting()));
        byReason.forEach((r, n) -> System.out.printf("    exclu — %-22s : %d%n", r, n));

        List<Proj> kept = all.stream().filter(p -> p.excluded == null).toList();
        counters.inScope = all.size();
        counters.kept = kept.size();
        System.out.printf("  Retenus pour analyse            : %d (%s du parc)%n",
                kept.size(), pct(kept.size(), all.size()));
        if (gl.forbidden > 0) {
            System.out.println(c("    %d réponses 403 : le jeton ne voit pas tout le parc."
                    .formatted(gl.forbidden), YELLOW));
            System.out.println(c("    Un trou de permission raccourcit la liste sans lever d'erreur.", DIM));
        }
        return all;
    }

    private List<JsonNode> fromFile() throws IOException {
        List<JsonNode> out = new ArrayList<>();
        for (String raw : Files.readAllLines(projectsFile, StandardCharsets.UTF_8)) {
            String path = raw.trim();
            if (path.isEmpty() || path.startsWith("#")) continue;
            Gitlab.Response r = gl.get("projects/" + enc(path), Map.of("statistics", "true"));
            if (r.status() == 200) out.add(r.json());
            else System.out.printf("    %-40s HTTP %d — ignoré%n", path, r.status());
        }
        return out;
    }

    // ----------------------------------------------------------------------
    // Étape 1 : filtre de fraîcheur, et sa validation
    // ----------------------------------------------------------------------

    /**
     * last_activity_at est inutilisable pour *classer* — un commentaire de ticket
     * le déplace — mais il est exactement bon pour *exclure*, parce qu'il se
     * trompe dans le sens sûr : il est gonflé par les événements non-commit, donc
     * tout projet ayant commité dans la fenêtre a forcément un last_activity_at
     * récent. Le filtre sur-inclut ; il ne sous-inclut pas.
     */
    private List<Proj> recencyGate(List<Proj> all) {
        title("2. Filtre de fraîcheur (étape 1) — 0 appel");

        List<Proj> kept = all.stream().filter(p -> p.excluded == null).toList();
        for (Proj p : kept) {
            if (p.lastActivity == null || p.lastActivity.isBefore(windowStart)) {
                p.excluded = "inactif > %dj".formatted(sinceDays);
            }
        }
        List<Proj> fresh = kept.stream().filter(p -> p.excluded == null).toList();
        counters.fresh = fresh.size();
        System.out.printf("  Actifs sur %d jours              : %d / %d (%s)%n",
                sinceDays, fresh.size(), kept.size(), pct(fresh.size(), kept.size()));
        System.out.println(c("    Coupe la plus rentable du pipeline : gratuite.", DIM));
        return fresh;
    }

    /**
     * La propriété « le filtre ne sous-inclut pas » a une classe de fuite connue :
     * réécritures d'historique, imports et transferts peuvent désynchroniser
     * last_activity_at des dates de commit. On l'échantillonne au lieu de la
     * croire — zéro faux négatif rend la porte gratuite, un chiffre non nul
     * remplace une hypothèse par une mesure.
     */
    private void validateRecencyGate(List<Proj> all) {
        if (validationSample <= 0) return;
        List<Proj> below = all.stream()
                .filter(p -> p.excluded != null && p.excluded.startsWith("inactif"))
                .collect(Collectors.toCollection(ArrayList::new));
        if (below.isEmpty()) return;

        Collections.shuffle(below, new Random(seed));
        List<Proj> sample = below.subList(0, Math.min(validationSample, below.size()));
        int leaks = 0;
        for (Proj p : sample) {
            int n = countCommits(p);
            if (n > 0) {
                leaks++;
                p.leaked = true;
                p.commits = n;
            }
        }
        counters.sampleSize = sample.size();
        counters.sampleLeaks = leaks;
        System.out.printf("  Échantillon de contrôle sous la coupe : %d projets, %d faux négatif%s (%s)%n",
                sample.size(), leaks, leaks > 1 ? "s" : "", pct(leaks, sample.size()));
        if (leaks > 0) {
            System.out.println(c("    Le filtre laisse passer des projets actifs. Chiffre à publier", YELLOW));
            System.out.println(c("    tel quel dans le rapport : la sélection n'est pas exhaustive.", YELLOW));
        } else {
            System.out.println(c("    Aucune fuite mesurée : la porte tient, et elle reste gratuite.", DIM));
        }
    }

    // ----------------------------------------------------------------------
    // Étape 2 : activité réelle, en commits
    // ----------------------------------------------------------------------

    private void commitActivity(List<Proj> fresh) {
        title("3. Activité réelle en commits (étape 2)");

        int refined = graphql ? graphqlLastCommit(fresh) : -1;
        if (refined >= 0) {
            System.out.printf("  Dernier commit daté par GraphQL : %d projets en %d requêtes%n",
                    refined, counters.graphqlCalls);
            long stale = fresh.stream().filter(p -> p.lastCommit != null
                    && p.lastCommit.isBefore(windowStart)).count();
            System.out.printf("    dont aucun commit dans la fenêtre : %d%n", stale);
            System.out.println(c("    (last_activity_at récent sans commit récent : tickets, wiki, CI)", DIM));
        }

        int counted = 0;
        for (Proj p : fresh) {
            // Si GraphQL a daté le dernier commit hors fenêtre, le compte est zéro
            // par construction : inutile de le redemander en REST.
            if (p.lastCommit != null && p.lastCommit.isBefore(windowStart)) {
                p.commits = 0;
                p.commitsBots = 0;
                p.authors = 0;
                continue;
            }
            pageCommits(p);
            counted++;
        }
        counters.commitPassed = counted;
        long active = fresh.stream().filter(p -> p.commits != null && p.commits > 0).count();
        System.out.printf("  Comptés par pagination REST     : %d projets%n", counted);
        System.out.printf("  Avec au moins un commit humain  : %d%n", active);
        long botOnly = fresh.stream().filter(p -> p.commits != null && p.commits == 0
                && p.commitsBots != null && p.commitsBots > 0).count();
        if (botOnly > 0) {
            System.out.printf("  Activité exclusivement robotique : %d%n", botOnly);
            System.out.println(c("    Renovate seul sur un dépôt est un constat, pas une ligne vide.", DIM));
        }
    }

    /**
     * Route GraphQL : ~50 projets par requête via des alias {@code project(fullPath:)}.
     * Donne la date du dernier commit réel sur la branche par défaut — un signal de
     * commit, pas un proxy d'événement — pour deux ordres de grandeur de moins que REST.
     *
     * NON VÉRIFIÉ contre une instance réelle : la forme de Repository.tree.lastCommit
     * varie selon la version de GitLab. En cas d'erreur GraphQL on retombe
     * silencieusement sur REST, qui reste la route certaine.
     */
    private int graphqlLastCommit(List<Proj> fresh) {
        int done = 0;
        for (int i = 0; i < fresh.size(); i += 50) {
            List<Proj> batch = fresh.subList(i, Math.min(i + 50, fresh.size()));
            StringBuilder q = new StringBuilder("{");
            for (int j = 0; j < batch.size(); j++) {
                q.append("p").append(j).append(":project(fullPath:\"")
                        .append(batch.get(j).path.replace("\"", "\\\""))
                        .append("\"){repository{rootRef tree{lastCommit{committedDate}}}} ");
            }
            q.append("}");

            counters.graphqlCalls++;
            Gitlab.Response r = gl.graphql(q.toString());
            JsonNode data = r.status() == 200 ? r.json().path("data") : null;
            if (data == null || data.isMissingNode() || r.json().has("errors")) {
                if (i == 0) {
                    System.out.println(c("  GraphQL indisponible sur cette instance — repli REST.", DIM));
                    return -1;
                }
                break; // partiel : les projets non datés passeront par REST
            }
            for (int j = 0; j < batch.size(); j++) {
                JsonNode n = data.path("p" + j).path("repository").path("tree")
                        .path("lastCommit").path("committedDate");
                if (n.isTextual()) {
                    batch.get(j).lastCommit = parse(n.asText());
                    done++;
                }
            }
        }
        return done;
    }

    /** Compte sans pagination quand X-Total est là ; sinon pagine. */
    private int countCommits(Proj p) {
        Gitlab.Response r = gl.get("projects/" + p.id + "/repository/commits", Map.of(
                "ref_name", p.defaultBranch, "since", iso(windowStart), "per_page", "1"));
        if (r.status() != 200) return 0;
        Integer total = r.intHeader("x-total");
        if (total != null) return total;
        // GitLab omet X-Total quand le comptage serait coûteux. Absent ne veut pas
        // dire zéro : on redemande une page pleine plutôt que de lire un en-tête
        // manquant comme un 0 — c'est exactement la confusion « absent = zéro »
        // qui a déjà coûté cher côté Sonar.
        Gitlab.Response full = gl.get("projects/" + p.id + "/repository/commits", Map.of(
                "ref_name", p.defaultBranch, "since", iso(windowStart), "per_page", "100"));
        return full.status() == 200 && full.json().isArray() ? full.json().size() : 0;
    }

    /**
     * Une seule passe de pagination donne quatre choses : le volume, les auteurs
     * distincts, le taux de commits de merge et l'étalement dans le temps. On la
     * fait une fois et on garde les commits bruts.
     */
    private void pageCommits(Proj p) {
        Set<String> authors = new TreeSet<>();
        int human = 0, bot = 0, merges = 0, reverts = 0;
        Set<Long> days = new HashSet<>();
        OffsetDateTime newest = null;
        boolean truncated = false;

        for (int page = 1; page <= maxCommitPages; page++) {
            Gitlab.Response r = gl.get("projects/" + p.id + "/repository/commits", Map.of(
                    "ref_name", p.defaultBranch, "since", iso(windowStart),
                    "per_page", "100", "page", String.valueOf(page)));
            if (r.status() != 200) {
                // Refus de permission ou erreur : on ne sait pas, et « on ne sait
                // pas » ne doit surtout pas se lire comme « aucune activité ».
                p.unmeasurable = "HTTP " + r.status();
                return;
            }
            JsonNode arr = r.json();
            if (!arr.isArray() || arr.isEmpty()) break;

            for (JsonNode c : arr) {
                String email = text(c, "author_email");
                String name = text(c, "author_name");
                if (Gitlab.isBot(bots, email, name)) { bot++; continue; }
                human++;
                if (REVERT.matcher(orEmpty(text(c, "title"))).find()) reverts++;
                authors.add(Gitlab.identity(email, name));
                if (Gitlab.isMerge(c)) merges++;
                OffsetDateTime d = parse(text(c, "committed_date"));
                if (d != null) {
                    days.add(ChronoUnit.DAYS.between(windowStart, d));
                    if (newest == null || d.isAfter(newest)) newest = d;
                }
            }
            if (arr.size() < 100) break;
            if (page == maxCommitPages) truncated = true;
        }
        p.commits = human;
        p.commitsBots = bot;
        p.reverts = reverts;
        p.authors = authors.size();
        p.mergeCommits = merges;
        p.activeDays = days.size();
        p.truncated = truncated;
        if (p.lastCommit == null) p.lastCommit = newest;
    }

    // ----------------------------------------------------------------------
    // Étape 3 : le plancher d'activité
    // ----------------------------------------------------------------------

    /**
     * Sous un certain volume, les signaux profonds sont de l'arithmétique sur du
     * bruit : 3 commits et 1 MR ne peuvent pas porter un taux de revue, un DORA
     * ni un taux de succès de pipeline — ils renvoient 0 % ou 100 %, et les deux
     * sont vides de sens. Même logique que le garde-fou ncloc < 500 côté Sonar.
     *
     * Le seuil se déduit de la distribution plutôt que de se fixer d'avance :
     * elle est franchement à longue traîne, et la rupture naturelle tombe
     * souvent près du budget visé — le plancher devient alors descriptif au lieu
     * d'être arbitraire.
     */
    private int activityFloor(List<Proj> fresh) {
        title("4. Plancher d'activité (étape 3)");

        List<Integer> volumes = fresh.stream()
                .map(p -> p.commits == null ? 0 : p.commits)
                .sorted(Comparator.reverseOrder()).toList();
        System.out.print("  Distribution des commits (déciles) :");
        for (int d = 1; d <= 9; d += 2) {
            int idx = Math.min(volumes.size() - 1, volumes.size() * d / 10);
            System.out.printf(" D%d=%d", d, volumes.isEmpty() ? 0 : volumes.get(idx));
        }
        System.out.println();

        int chosen = floor;
        if (chosen < 0) {
            chosen = MIN_FLOOR;
            for (int t = MIN_FLOOR; t <= 60; t++) {
                final int threshold = t;
                long n = volumes.stream().filter(v -> v >= threshold).count();
                chosen = t;
                if (n <= top * 3L / 2) break;
            }
            System.out.printf("  Plancher déduit des données     : %d commits sur %d jours%n",
                    chosen, sinceDays);
        } else {
            System.out.printf("  Plancher imposé (--floor)       : %d commits%n", chosen);
        }

        int eligible = 0, unmeasurable = 0;
        for (Proj p : fresh) {
            if (p.commits == null) {
                p.excluded = "activité non mesurable (" + orEmpty(p.unmeasurable) + ")";
                unmeasurable++;
                continue;
            }
            if (p.commits < chosen) {
                p.excluded = (p.commitsBots != null && p.commitsBots > 0 && p.commits == 0)
                        ? "activité robotique seule (%d commits de bots)".formatted(p.commitsBots)
                        : "sous le plancher (%d commits)".formatted(p.commits);
            } else {
                eligible++;
            }
        }
        counters.floor = chosen;
        counters.eligible = eligible;
        System.out.printf("  Éligibles au tirage             : %d%n", eligible);
        System.out.printf("  Cohorte faible activité         : %d%n",
                fresh.size() - eligible - unmeasurable);
        if (unmeasurable > 0) {
            System.out.printf("  Activité non mesurable          : %d%n", unmeasurable);
            System.out.println(c("    403 ou erreur sur les commits. Écartés, pas classés à zéro :", YELLOW));
            System.out.println(c("    « je n'ai pas pu voir » n'est pas « il ne se passe rien ».", YELLOW));
        }
        System.out.println(c("    Cette cohorte n'est pas perdue : elle se rapporte en effectifs,", DIM));
        System.out.println(c("    comme les « jamais analysés » côté Sonar. C'est un constat.", DIM));
        return chosen;
    }

    static final int MIN_FLOOR = 5;

    /**
     * Git écrit « Revert "titre d'origine" », l'interface GitLab reprend la même
     * forme, et les dépôts en commits conventionnels écrivent « revert: … ». Le
     * motif est ancré au début du titre : « revert » au milieu d'une phrase parle
     * du sujet, pas d'une annulation.
     */
    static final java.util.regex.Pattern REVERT =
            java.util.regex.Pattern.compile("^\\s*revert\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

    // ----------------------------------------------------------------------
    // Étape 4 : quotas, pas top-N
    // ----------------------------------------------------------------------

    /**
     * Prendre les 200 premiers par volume de commits, c'est refaire l'erreur du
     * classement Sonar par sqale_index : on récupère les gros monolithes actifs
     * de deux ou trois équipes et on apprend ce qu'on savait déjà.
     *
     * Quatre tranches, donc : le haut de chaque strate de taille, un plafond par
     * namespace, les cas mono-auteur que le classement brut enterre, et un
     * témoin aléatoire.
     */
    private void select(List<Proj> fresh) {
        title("5. Sélection par quotas (étape 4)");

        List<Proj> eligible = fresh.stream().filter(p -> p.excluded == null)
                .collect(Collectors.toCollection(ArrayList::new));
        if (eligible.isEmpty()) {
            System.out.println(c("  Aucun projet au-dessus du plancher.", YELLOW));
            return;
        }
        for (Proj p : eligible) p.bucket = bucketOf(p);

        int coreQuota = Math.max(1, top * 70 / 100);
        int busQuota = Math.max(1, top * 10 / 100);
        int controlQuota = Math.max(1, top * 15 / 100);
        int nsCap = Math.max(3, (int) Math.ceil(top * 0.15));

        Map<String, Integer> perNamespace = new HashMap<>();
        List<Proj> selected = new ArrayList<>();

        // 1. Tête de chaque strate, en tourniquet pour que les petites strates
        //    ne soient pas noyées par les grosses.
        Map<String, List<Proj>> byBucket = eligible.stream()
                .sorted(Comparator.comparingDouble((Proj p) -> p.perWeek(sinceDays)).reversed())
                .collect(Collectors.groupingBy(p -> p.bucket, TreeMap::new, Collectors.toList()));
        Map<String, Integer> cursor = new HashMap<>();
        while (selected.size() < coreQuota) {
            boolean progressed = false;
            for (var e : byBucket.entrySet()) {
                if (selected.size() >= coreQuota) break;
                int i = cursor.getOrDefault(e.getKey(), 0);
                List<Proj> list = e.getValue();
                while (i < list.size()) {
                    Proj p = list.get(i++);
                    if (p.selected) continue;
                    if (perNamespace.getOrDefault(p.namespace, 0) >= nsCap) continue;
                    take(p, "tête de strate " + p.bucket, selected, perNamespace);
                    progressed = true;
                    break;
                }
                cursor.put(e.getKey(), i);
            }
            if (!progressed) break;
        }

        // 2. Mono-auteur à forte activité : un bus factor qui compte vraiment,
        //    et que le classement par volume ne fait jamais remonter.
        eligible.stream()
                .filter(p -> !p.selected && p.authors != null && p.authors <= 1)
                .sorted(Comparator.comparingInt((Proj p) -> p.commits == null ? 0 : p.commits).reversed())
                .limit(busQuota)
                .forEach(p -> take(p, "mono-auteur, forte activité", selected, perNamespace));

        // 3. Témoin aléatoire. C'est la tranche qui a l'air d'un gâchis et qui
        //    n'en est pas une : sans elle, chaque affirmation du rapport final
        //    est conditionnée à une règle de sélection que personne n'a validée.
        List<Proj> rest = eligible.stream().filter(p -> !p.selected)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(rest, new Random(seed));
        rest.stream().limit(controlQuota)
                .forEach(p -> take(p, "témoin aléatoire", selected, perNamespace));

        // Une tranche qui ne trouve pas preneur ne doit pas laisser du budget sur
        // la table : le reliquat retourne au classement, plafond de namespace inclus.
        if (selected.size() < top) {
            for (Proj p : eligible.stream()
                    .sorted(Comparator.comparingDouble((Proj x) -> x.perWeek(sinceDays)).reversed())
                    .toList()) {
                if (selected.size() >= top) break;
                if (p.selected || perNamespace.getOrDefault(p.namespace, 0) >= nsCap) continue;
                take(p, "reliquat de budget", selected, perNamespace);
            }
        }

        counters.selected = selected.size();
        System.out.printf("  Budget                          : %d%n", top);
        System.out.printf("  Sélectionnés                    : %d%n", selected.size());
        selected.stream().collect(Collectors.groupingBy(
                        p -> p.selectionReason.startsWith("tête") ? "tête de strate" : p.selectionReason,
                        TreeMap::new, Collectors.counting()))
                .forEach((r, n) -> System.out.printf("    %-32s : %d%n", r, n));
        System.out.printf("  Namespaces représentés          : %d (plafond %d par namespace)%n",
                perNamespace.size(), nsCap);
        System.out.println(c("    Le témoin permettra d'écrire « couverture X sur les sélectionnés,", DIM));
        System.out.println(c("    Y sur un tirage du reste » — et si X et Y divergent, la règle", DIM));
        System.out.println(c("    de sélection est elle-même le sujet.", DIM));
    }

    private void take(Proj p, String reason, List<Proj> selected, Map<String, Integer> perNamespace) {
        p.selected = true;
        p.selectionReason = reason;
        selected.add(p);
        perNamespace.merge(p.namespace, 1, Integer::sum);
    }

    /** Strates sur le volume historique : une même mesure ne se compare qu'à taille comparable. */
    private static String bucketOf(Proj p) {
        long c = p.totalCommits != null ? p.totalCommits
                : (p.repoSize != null ? p.repoSize / 20_000 : 0);
        if (c < 100) return "A(<100)";
        if (c < 1_000) return "B(<1k)";
        if (c < 10_000) return "C(<10k)";
        return "D(>10k)";
    }

    // ----------------------------------------------------------------------
    // Sorties
    // ----------------------------------------------------------------------

    private void writeInventory(List<Proj> all) throws IOException {
        String[] header = {"id", "path", "name", "namespace", "default_branch", "visibility",
                "created_at", "last_activity_at", "archived", "fork", "mirror",
                "total_commits", "repo_size", "bucket", "last_commit", "commits_window",
                "commits_bots", "authors_window", "merge_commits", "reverts", "active_days",
                "commits_per_week", "tronque", "exclu", "fuite_filtre",
                "selectionne", "motif_selection"};
        try (CSVWriter w = Csv.writer(csv, comma)) {
            w.writeNext(header);
            for (Proj p : all) w.writeNext(p.row(sinceDays));
        }
        System.out.printf("%n  Inventaire : %d lignes.%n", all.size());
    }

    private void summary(List<Proj> all) {
        title("Synthèse");
        System.out.printf("  %d appels API (%d GET, %d GraphQL, %d 429 réessayés).%n",
                gl.calls, gl.calls - counters.graphqlCalls, counters.graphqlCalls, gl.throttled);
        System.out.printf("  Parc %d → retenus %d → frais %d → éligibles %d → sélectionnés %d.%n",
                counters.inScope, counters.kept, counters.fresh, counters.eligible, counters.selected);
        if (counters.sampleSize > 0) {
            System.out.printf("  Filtre de fraîcheur validé sur %d projets : %d faux négatif%s.%n",
                    counters.sampleSize, counters.sampleLeaks,
                    counters.sampleLeaks > 1 ? "s" : "");
        }
        long truncated = all.stream().filter(p -> p.truncated).count();
        if (truncated > 0) {
            System.out.println(c("  %d projets tronqués à %d pages de commits : leur volume est"
                    .formatted(truncated, maxCommitPages), YELLOW));
            System.out.println(c("  un plancher, pas un compte. Ils sont marqués dans le CSV.", YELLOW));
        }
        if (csv == null && pratiquesCsv == null) {
            System.out.println(c("  Aucun fichier écrit : ajoute --out-dir <rép> pour "
                    + "conserver l'inventaire", YELLOW));
            System.out.println(c("  et les pratiques. Le rapport ci-dessus ne se rejoue "
                    + "pas sans les CSV.", YELLOW));
        } else {
            System.out.println("  Fichiers écrits :");
            if (csv != null) System.out.printf("    inventaire : %s%n", csv.toAbsolutePath());
            if (pratiquesCsv != null) {
                System.out.printf("    pratiques  : %s%n", pratiquesCsv.toAbsolutePath());
            }
            System.out.println(c("    Les deux se lisent ensemble : pratiques.csv ne contient "
                    + "que les", DIM));
            System.out.println(c("    sélectionnés, l'inventaire porte le parc dont ils sortent. "
                    + "Colonnes : COLUMNS.md.", DIM));
            System.out.println(c("  " + Csv.openingHint(
                    csv != null ? csv : pratiquesCsv, comma), DIM));
        }
        System.out.println(c("""
                  Rappel : tout classement qui ne dit pas ce qu'il a écarté se lit comme
                  complet alors qu'il ne l'est pas. Le CSV porte le motif d'exclusion de
                  chaque projet, et la sélection porte son motif d'entrée.
                """, DIM));
    }

    // ----------------------------------------------------------------------
    // §4 — signaux de pratique sur les sélectionnés
    // ----------------------------------------------------------------------

    final class DeepPass {
        private final List<Proj> selected;

        DeepPass(List<Proj> all) {
            this.selected = all.stream().filter(p -> p.selected).toList();
            for (Proj p : all) {
                if (p.path != null && !p.path.isEmpty()) {
                    projPathToId.put(p.path, String.valueOf(p.id));
                }
            }
        }

        void run() throws IOException {
            if (selected.isEmpty()) return;
            title("6. Signaux de pratique sur les %d sélectionnés".formatted(selected.size()));

            for (Proj p : selected) {
                Prat r = new Prat();
                p.prat = r;
                protection(p, r);
                reviews(p, r);
                pipelines(p, r);
                delivery(p, r);
                files(p, r);
            }
            coverage();
            if (pratiquesCsv != null) writePratiques();
        }

        /** Binaire, non ambigu, et cela explique le nombre de poussées directes. */
        private void protection(Proj p, Prat r) {
            Gitlab.Response br = gl.get("projects/" + p.id + "/protected_branches", Map.of("per_page", "100"));
            if (br.status() != 200) return;
            for (JsonNode b : br.json()) {
                if (p.defaultBranch.equals(text(b, "name"))) {
                    r.defaultProtected = true;
                    JsonNode lvl = b.path("push_access_levels");
                    r.pushLocked = lvl.size() > 0 && lvl.get(0).path("access_level").asInt(-1) == 0;
                }
            }
        }

        /**
         * Le nombre qui compte serait la part des commits de la branche par défaut
         * arrivés par MR approuvée. Elle n'est pas calculable à coût raisonnable :
         * il faudrait rattacher chaque commit à sa MR. On publie donc les deux
         * termes bruts — MR fusionnées et commits de merge — plutôt qu'un ratio
         * qui aurait l'air d'une mesure sans en être une.
         */
        private void reviews(Proj p, Prat r) {
            List<JsonNode> mrs = new ArrayList<>();
            for (int page = 1; page <= 3; page++) {
                Gitlab.Response m = gl.get("projects/" + p.id + "/merge_requests", Map.of(
                        "state", "merged", "target_branch", p.defaultBranch,
                        "updated_after", iso(windowStart),
                        "per_page", "100", "page", String.valueOf(page)));
                if (m.status() != 200 || !m.json().isArray() || m.json().isEmpty()) break;
                m.json().forEach(mrs::add);
                if (m.json().size() < 100) break;
            }
            r.mergedMrs = mrs.size();
            List<Double> ttm = new ArrayList<>();
            int selfMerged = 0, notes = 0;
            for (JsonNode mr : mrs) {
                OffsetDateTime a = parse(text(mr, "created_at"));
                OffsetDateTime b = parse(text(mr, "merged_at"));
                if (a != null && b != null) ttm.add(ChronoUnit.HOURS.between(a, b) / 24.0);
                int author = mr.path("author").path("id").asInt(-1);
                int merger = mr.path("merged_by").path("id").asInt(
                        mr.path("merge_user").path("id").asInt(-2));
                if (author != -1 && author == merger) selfMerged++;
                notes += mr.path("user_notes_count").asInt(0);
            }
            r.medianTtmDays = median(ttm);
            r.selfMerged = selfMerged;
            r.notesPerMr = mrs.isEmpty() ? null : (double) notes / mrs.size();

            // Approbations : la mesure réelle, réservée aux plans Enterprise. On
            // échantillonne 10 MR par projet — 1 appel par MR sur 200 projets
            // coûterait plus cher que tout le reste de la passe réuni.
            if (counters.enterprise && !mrs.isEmpty()) {
                int sample = Math.min(10, mrs.size()), approved = 0, selfApproved = 0, seen = 0;
                for (int i = 0; i < sample; i++) {
                    JsonNode mr = mrs.get(i);
                    Gitlab.Response ap = gl.get("projects/" + p.id + "/merge_requests/"
                            + mr.path("iid").asInt() + "/approvals", Map.of());
                    if (ap.status() != 200) break;
                    seen++;
                    JsonNode by = ap.json().path("approved_by");
                    if (by.size() > 0) approved++;
                    int author = mr.path("author").path("id").asInt(-1);
                    for (JsonNode u : by) {
                        if (u.path("user").path("id").asInt(-2) == author) selfApproved++;
                    }
                }
                if (seen > 0) {
                    r.approvalSample = seen;
                    r.approvedShare = (double) approved / seen;
                    r.selfApproved = selfApproved;
                }
            }
        }

        private void pipelines(Proj p, Prat r) {
            Gitlab.Response pl = gl.get("projects/" + p.id + "/pipelines", Map.of(
                    "ref", p.defaultBranch, "updated_after", iso(windowStart), "per_page", "100"));
            if (pl.status() != 200 || !pl.json().isArray()) return;
            int n = pl.json().size(), ok = 0;
            for (JsonNode x : pl.json()) if ("success".equals(text(x, "status"))) ok++;
            r.pipelines = n;
            r.pipelineSuccess = n == 0 ? null : (double) ok / n;
            recovery(pl.json(), r);
        }

        /**
         * Combien de temps l'équipe laisse la branche par défaut cassée.
         *
         * Sans environnement déclaré, aucune métrique DORA n'est calculable : il
         * n'existe pas d'événement de mise en production à dater. Ceci n'en est
         * pas une et ne doit pas s'y substituer sous un nom voisin — c'est le
         * temps de retour au vert de la CI, qui se mesure sur les seuls
         * horodatages déjà présents dans la liste ci-dessus, sans un appel de plus.
         *
         * Une série d'échecs consécutifs est UN incident, pas cinq : compter
         * chaque pipeline rouge gonflerait le nombre d'incidents et raccourcirait
         * la médiane, exactement à l'envers de ce qu'on cherche à voir. Les états
         * ni verts ni rouges — annulé, ignoré, en cours, manuel — ne cassent ni ne
         * réparent : ils sont sautés plutôt que lus comme une fin de panne.
         */
        private void recovery(JsonNode arr, Prat r) {
            record Run(OffsetDateTime at, boolean red, boolean green) { }
            List<Run> runs = new ArrayList<>();
            for (JsonNode x : arr) {
                OffsetDateTime at = parse(text(x, "created_at"));
                String st = text(x, "status");
                if (at == null || st == null) continue;
                boolean red = "failed".equals(st);
                boolean green = "success".equals(st);
                if (red || green) runs.add(new Run(at, red, green));
            }
            if (runs.isEmpty()) return;
            runs.sort(Comparator.comparing(Run::at));

            List<Double> hours = new ArrayList<>();
            int incidents = 0, unresolved = 0;
            OffsetDateTime brokenSince = null;
            for (Run run : runs) {
                if (run.red() && brokenSince == null) {
                    brokenSince = run.at();
                    incidents++;
                } else if (run.green() && brokenSince != null) {
                    hours.add(ChronoUnit.MINUTES.between(brokenSince, run.at()) / 60.0);
                    brokenSince = null;
                }
            }
            // Toujours rouge à la fin de la fenêtre : l'incident est réel, sa durée
            // ne l'est pas encore. On le compte sans lui inventer un retour au vert.
            if (brokenSince != null) unresolved = 1;

            r.redIncidents = incidents;
            r.unresolvedRed = unresolved;
            r.recoveryMedianHours = median(hours);
        }

        /**
         * DORA ne vaut que ce que valent les environnements déclarés : un projet
         * qui déploie tous les jours depuis un pipeline sans environnement
         * « production » affiche une fréquence nulle. « Aucun environnement »
         * est un constat de disponibilité de la donnée, pas un mauvais score.
         */
        private void delivery(Proj p, Prat r) {
            Gitlab.Response env = gl.get("projects/" + p.id + "/environments", Map.of("per_page", "1"));
            if (env.status() == 200) {
                Integer t = env.intHeader("x-total");
                r.environments = t != null ? t : env.json().size();
            }
            if (!counters.enterprise || (r.environments != null && r.environments == 0)) return;

            Gitlab.Response df = gl.get("projects/" + p.id + "/dora/metrics", Map.of(
                    "metric", "deployment_frequency",
                    "start_date", iso(windowStart).substring(0, 10),
                    "interval", "monthly"));
            if (df.status() == 200 && df.json().isArray()) {
                double sum = 0;
                for (JsonNode d : df.json()) sum += d.path("value").asDouble(0);
                r.deployments = sum;
            } else if (df.status() == 403 || df.status() == 404) {
                r.doraUnavailable = true;
            }
        }

        private static final List<String> WATCHED = List.of(
                ".gitlab-ci.yml", "README.md", "CODEOWNERS", "Dockerfile", "renovate.json");

        /** Cache de résolution project_path -> ID, rempli au fur et à mesure. */
        private final java.util.Map<String, String> projPathToId = new java.util.HashMap<>();

        /** Cache du contenu brut des fichiers inclus pour éviter les rappels API. */
        private final java.util.Map<String, Gitlab.Response> includeCache = new java.util.HashMap<>();

        private void files(Proj p, Prat r) {
            for (String f : WATCHED) {
                if (gl.exists("projects/" + p.id + "/repository/files/" + enc(f),
                        Map.of("ref", p.defaultBranch))) {
                    r.files.add(f);
                }
            }
            if (!r.files.contains(".gitlab-ci.yml")) return;
            Gitlab.Response raw = gl.get("projects/" + p.id + "/repository/files/"
                    + enc(".gitlab-ci.yml") + "/raw", Map.of("ref", p.defaultBranch));
            if (raw.status() != 200 || raw.body() == null) return;
            String ci = raw.body();
            String ciLower = ci.toLowerCase(Locale.ROOT);
            // Si le mot-clé est déjà dans le fichier brut, pas besoin de résoudre les includes.
            if (ciLower.contains("sonar") || ciLower.contains("sast")
                    || ciLower.contains("secret-detection")
                    || ciLower.contains("dependency-scanning")) {
                r.ciSonar = ciLower.contains("sonar");
                r.ciSecurity = ciLower.contains("sast") || ciLower.contains("secret-detection")
                        || ciLower.contains("dependency-scanning");
                return;
            }
            // Le fichier brut ne contient aucun mot-clé : extraire uniquement les
            // inclusions cross-projet et scanner leur contenu en un seul passage.
            scanProjectIncludesFor(ci, p.defaultBranch, p.id, content -> {
                String lower = content.toLowerCase(Locale.ROOT);
                if (!r.ciSonar && lower.contains("sonar")) r.ciSonar = true;
                if (!r.ciSecurity && (lower.contains("sast")
                        || lower.contains("secret-detection")
                        || lower.contains("dependency-scanning"))) r.ciSecurity = true;
                // Si les deux sont trouvés, on peut arrêter tout de suite
                return r.ciSonar && r.ciSecurity;
            });
        }

        /**
         * Extrait les directives .include: project:/file: du YAML, télécharge
         * chaque fichier inclus une seule fois, et vérifie tous les mots-clés
         * en un seul passage. Cache le contenu des fichiers inclus pour éviter
         * les rappels identiques.
         *
         * @param checker fonction qui retourne true pour arrêter le scan prématurément
         */
        private void scanProjectIncludesFor(String ci, String ref, long projectId,
                                             java.util.function.Function<String, Boolean> checker) {
            java.util.regex.Pattern projectInclude = java.util.regex.Pattern.compile(
                    "-\\s*project:\\s*(\\S+).*ref:\\s*(\\S+).*file:\\s*(\\S+)", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = projectInclude.matcher(ci);
            while (m.find()) {
                String projPath = m.group(1).replaceAll("^['\"]|['\"]$", "");
                String incRef = m.group(2).replaceAll("^['\"]|['\"]$", "");
                String incFile = m.group(3).replaceAll("^['\"]|['\"]$", "");
                String projIdStr = resolveProjectPathCached(projPath);
                if (projIdStr == null) continue;
                String cacheKey = projIdStr + ":" + incFile + ":" + incRef;
                Gitlab.Response fileRaw;
                if (includeCache.containsKey(cacheKey)) {
                    fileRaw = includeCache.get(cacheKey);
                } else {
                    fileRaw = gl.get("projects/" + enc(projIdStr)
                            + "/repository/files/" + enc(incFile) + "/raw",
                            Map.of("ref", incRef));
                    includeCache.put(cacheKey, fileRaw);
                }
                if (fileRaw.status() == 200 && fileRaw.body() != null) {
                    if (checker.apply(fileRaw.body())) return;
                }
            }
        }

        /**
         * Résout le path complet d'un projet GitLab en son ID numérique,
         * avec cache pour éviter de refaire la même requête API.
         */
        private String resolveProjectPathCached(String projPath) {
            if (projPathToId.containsKey(projPath)) {
                String cached = projPathToId.get(projPath);
                return cached.isEmpty() ? null : cached;
            }
            try {
                Long.parseLong(projPath);
                projPathToId.put(projPath, projPath);
                return projPath;
            } catch (NumberFormatException ignored) {
            }
            Gitlab.Response proj = gl.get("projects/" + enc(projPath), Map.of("per_page", "1"));
            if (proj.status() == 200 && proj.body() != null) {
                try {
                    JsonNode node = proj.json();
                    String id = String.valueOf(node.path("id").asLong());
                    projPathToId.put(projPath, id);
                    return id;
                } catch (Exception ignored) {
                }
            }
            projPathToId.put(projPath, "");
            return null;
        }

        /**
         * Le témoin aléatoire sert ici : si la couverture des pratiques diverge
         * fortement entre les sélectionnés et le tirage témoin, c'est la règle de
         * sélection qui est le sujet, pas les pratiques.
         */
        private void coverage() {
            List<Proj> core = selected.stream()
                    .filter(p -> !p.selectionReason.startsWith("témoin")).toList();
            List<Proj> witness = selected.stream()
                    .filter(p -> p.selectionReason.startsWith("témoin")).toList();

            System.out.printf("%n  %-38s %12s %12s%n", "", "sélection", "témoin");
            row("Branche par défaut protégée", core, witness, p -> p.prat.defaultProtected);
            row("Au moins une MR fusionnée", core, witness, p -> p.prat.mergedMrs > 0);
            row("Pipelines sur la branche par défaut", core, witness, p -> p.prat.pipelines > 0);
            row("CI configurée pour Sonar", core, witness, p -> p.prat.ciSonar);
            row("Scan de sécurité dans la CI", core, witness, p -> p.prat.ciSecurity);
            row("CODEOWNERS présent", core, witness, p -> p.prat.files.contains("CODEOWNERS"));
            row("Aucun environnement déclaré", core, witness,
                    p -> p.prat.environments != null && p.prat.environments == 0);

            List<Double> recoveries = core.stream().map(p -> p.prat.recoveryMedianHours)
                    .filter(Objects::nonNull).toList();
            if (!recoveries.isEmpty()) {
                System.out.printf("%n  Retour au vert (médiane des médianes) : %s h sur %d projet%s.%n",
                        dec(median(recoveries)), recoveries.size(), recoveries.size() > 1 ? "s" : "");
                System.out.println(c("    Temps pendant lequel la branche par défaut reste cassée. "
                        + "Ce n'est pas", DIM));
                System.out.println(c("    une métrique DORA : sans environnement déclaré, aucune mise "
                        + "en production", DIM));
                System.out.println(c("    n'est datable, et rien ici ne s'y substitue.", DIM));
            }

            long reverting = core.stream().filter(p -> p.reverts != null && p.reverts > 0).count();
            if (reverting > 0) {
                int total = core.stream().mapToInt(p -> p.reverts == null ? 0 : p.reverts).sum();
                System.out.printf("%n  %d revert%s sur %d projet%s.%n", total, total > 1 ? "s" : "",
                        reverting, reverting > 1 ? "s" : "");
                System.out.println(c("    Signal de qualité direct, indépendant de Sonar et des "
                        + "environnements.", DIM));
            }

            long noReview = core.stream().filter(p -> p.prat.mergedMrs == 0 && p.commits >= 20).count();
            if (noReview > 0) {
                System.out.printf("%n  %d projet%s à ≥20 commits sans une seule MR fusionnée.%n",
                        noReview, noReview > 1 ? "s" : "");
                System.out.println(c("    Le changement atteint la branche par défaut sans revue.", YELLOW));
            }
        }

        private void row(String label, List<Proj> core, List<Proj> witness,
                         java.util.function.Predicate<Proj> test) {
            System.out.printf("  %-38s %12s %12s%n", label,
                    share(core, test), witness.isEmpty() ? "—" : share(witness, test));
        }

        private String share(List<Proj> ps, java.util.function.Predicate<Proj> test) {
            long n = ps.stream().filter(p -> p.prat != null).filter(test).count();
            return "%d (%s)".formatted(n, pct(n, ps.size()));
        }

        private void writePratiques() throws IOException {
            String[] header = {"path", "motif_selection", "bucket", "commits_window", "authors_window",
                    "branche_protegee", "push_verrouille", "mr_fusionnees", "ttm_median_j",
                    "auto_merge", "notes_par_mr", "echantillon_approbations", "part_approuvee",
                    "auto_approbation", "pipelines", "taux_succes", "incidents_rouges",
                    "retour_au_vert_h", "rouge_non_resolu", "environnements",
                    "deploiements", "dora_indispo", "ci_sonar", "ci_securite", "fichiers"};
            try (CSVWriter w = Csv.writer(pratiquesCsv, comma)) {
                w.writeNext(header);
                for (Proj p : selected) w.writeNext(p.pratRow());
            }
            System.out.printf("%n  Pratiques : %d lignes.%n", selected.size());
        }
    }

    // ----------------------------------------------------------------------
    // État par projet
    //
    // Tous les compteurs sont boxés : une mesure absente doit rester null et ne
    // surtout pas se lire comme un zéro. « Pas de commit compté » et « zéro
    // commit » sont deux constats différents, et c'est exactement l'erreur que
    // ce dépôt a déjà payée une fois côté Sonar.
    // ----------------------------------------------------------------------

    static final class Proj {
        long id;
        String path, name, namespace, defaultBranch, visibility;
        OffsetDateTime created, lastActivity, lastCommit;
        boolean archived, emptyRepo, mirror;
        String forkedFrom;
        Long totalCommits, repoSize;

        Integer commits, commitsBots, authors, mergeCommits, activeDays, reverts;
        boolean truncated, leaked, selected;
        String unmeasurable;
        String bucket = "", excluded, selectionReason = "";
        Prat prat;

        static Proj of(JsonNode n) {
            Proj p = new Proj();
            p.id = n.path("id").asLong();
            p.path = text(n, "path_with_namespace");
            p.name = text(n, "name");
            p.namespace = n.path("namespace").path("full_path").asText("");
            p.defaultBranch = text(n, "default_branch");
            p.visibility = text(n, "visibility");
            p.created = parse(text(n, "created_at"));
            p.lastActivity = parse(text(n, "last_activity_at"));
            p.archived = n.path("archived").asBoolean(false);
            p.emptyRepo = n.path("empty_repo").asBoolean(false);
            p.mirror = n.path("mirror").asBoolean(false);
            p.forkedFrom = n.hasNonNull("forked_from_project")
                    ? n.path("forked_from_project").path("path_with_namespace").asText(null) : null;
            JsonNode st = n.path("statistics");
            p.totalCommits = st.hasNonNull("commit_count") ? st.get("commit_count").asLong() : null;
            p.repoSize = st.hasNonNull("repository_size") ? st.get("repository_size").asLong() : null;
            return p;
        }

        double perWeek(int windowDays) {
            if (commits == null || windowDays <= 0) return 0;
            return commits * 7.0 / windowDays;
        }

        String[] row(int windowDays) {
            return new String[]{
                    String.valueOf(id), orEmpty(path), orEmpty(name), orEmpty(namespace),
                    orEmpty(defaultBranch), orEmpty(visibility),
                    iso(created), iso(lastActivity),
                    String.valueOf(archived), forkedFrom == null ? "false" : "true",
                    String.valueOf(mirror),
                    num(totalCommits), num(repoSize), bucket, iso(lastCommit),
                    num(commits), num(commitsBots), num(authors), num(mergeCommits),
                    num(reverts), num(activeDays),
                    commits == null ? "" : "%.2f".formatted(perWeek(windowDays)),
                    String.valueOf(truncated), orEmpty(excluded), String.valueOf(leaked),
                    String.valueOf(selected), orEmpty(selectionReason)};
        }

        String[] pratRow() {
            Prat r = prat == null ? new Prat() : prat;
            return new String[]{
                    orEmpty(path), orEmpty(selectionReason), bucket, num(commits), num(authors),
                    String.valueOf(r.defaultProtected), String.valueOf(r.pushLocked),
                    String.valueOf(r.mergedMrs), dec(r.medianTtmDays), String.valueOf(r.selfMerged),
                    dec(r.notesPerMr), num(r.approvalSample), dec(r.approvedShare),
                    String.valueOf(r.selfApproved), String.valueOf(r.pipelines),
                    dec(r.pipelineSuccess), num(r.redIncidents), dec(r.recoveryMedianHours),
                    num(r.unresolvedRed), num(r.environments), dec(r.deployments),
                    String.valueOf(r.doraUnavailable), String.valueOf(r.ciSonar),
                    String.valueOf(r.ciSecurity), String.join(" ", r.files)};
        }
    }

    static final class Prat {
        boolean defaultProtected, pushLocked, ciSonar, ciSecurity, doraUnavailable;
        int mergedMrs, selfMerged, selfApproved, pipelines;
        Integer approvalSample, environments, redIncidents, unresolvedRed;
        Double medianTtmDays, notesPerMr, approvedShare, pipelineSuccess, deployments,
                recoveryMedianHours;
        final List<String> files = new ArrayList<>();
    }

    static final class Counters {
        boolean enterprise;
        int inScope, kept, fresh, eligible, selected, floor;
        int sampleSize, sampleLeaks, commitPassed, graphqlCalls;
    }

    // ----------------------------------------------------------------------
    // Présentation et utilitaires
    // ----------------------------------------------------------------------

    static final String BOLD = "\033[1m", DIM = "\033[2m", RESET = "\033[0m";
    static final String GREEN = "\033[32m", RED = "\033[31m", YELLOW = "\033[33m";

    enum Verdict {
        OK(GREEN, "OK"), DENIED(RED, "REFUSÉ"), MISSING(YELLOW, "ABSENT"),
        EMPTY(YELLOW, "VIDE"), ERROR(RED, "ERREUR");

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
        System.out.println(c(text, BOLD));
        System.out.println(c("-".repeat(Math.min(text.length(), 72)), DIM));
    }

    static void line(String label, Verdict v, String detail) {
        System.out.printf("  %-32s %s  %s%n", label,
                c("[" + v.tag + "]", v.color), detail == null ? "" : detail);
    }

    /** Map.of refuse les valeurs nulles ; ici une valeur nulle signifie « ne pas envoyer ». */
    static Map<String, String> params(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    static String pct(long n, long total) {
        return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
    }

    static Double median(List<Double> xs) {
        if (xs == null || xs.isEmpty()) return null;
        List<Double> s = new ArrayList<>(xs);
        Collections.sort(s);
        int m = s.size() / 2;
        return s.size() % 2 == 1 ? s.get(m) : (s.get(m - 1) + s.get(m)) / 2;
    }

    static String iso(OffsetDateTime t) {
        return t == null ? "" : t.toString();
    }

    static OffsetDateTime parse(String s) {
        return Gitlab.parse(s);
    }

    static String enc(String s) {
        return Gitlab.enc(s);
    }

    static String text(JsonNode n, String field) {
        return n == null ? "" : n.path(field).asText("");
    }

    static String num(Number n) {
        return n == null ? "" : String.valueOf(n);
    }

    static String dec(Double d) {
        return d == null ? "" : "%.2f".formatted(d);
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    static String truncate(String s, int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max - 1) + "…");
    }
}
