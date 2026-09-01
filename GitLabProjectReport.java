///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Gitlab.java

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Motif de travail d'un projet GitLab, vu depuis l'API seule.
 *
 * Sonar décrit l'état du code à l'instant de l'analyse. GitLab décrit le
 * procédé qui l'a produit — et c'est le procédé qu'on cherche à qualifier :
 *
 *   1. Cadence     : à quel rythme, par à-coups ou en continu, quand ?
 *   2. Contributeurs : combien de gens, et quelle concentration ?
 *   3. Revue       : les MR sont-elles relues, par qui, en combien de temps ?
 *   4. Constats    : ce qui, dans les trois sections précédentes, mérite qu'on
 *                    aille voir.
 *
 * Le croisement avec Sonar (fréquence de modification × complexité, obsolète
 * vs abandonné, quality gate décorative) viendra se poser par-dessus : cette
 * moitié-ci se tient debout seule, et c'est voulu — un dépôt jamais analysé
 * par Sonar reste parfaitement lisible ici.
 *
 * Usage :
 *   export GITLAB_URL=https://gitlab.example.com      # défaut : gitlab.com
 *   export GITLAB_TOKEN=glpat_xxxxxxxx                # PAT, portée read_api
 *   jbang GitLabProjectReport.java --path groupe/sous-groupe/projet
 *   jbang GitLabProjectReport.java --path groupe/projet --days 365
 */
@Command(name = "GitLabProjectReport", mixinStandardHelpOptions = true,
        description = "Motif de travail d'un projet GitLab : cadence, concentration, revue.")
public class GitLabProjectReport implements Callable<Integer> {

    // ----------------------------------------------------------------------
    // Options
    // ----------------------------------------------------------------------

    @Option(names = "--url", defaultValue = "${env:GITLAB_URL}",
            description = "URL de l'instance (défaut : https://gitlab.com)")
    String url;

    @Option(names = "--token", defaultValue = "${env:GITLAB_TOKEN}",
            description = "jeton d'accès personnel, portée read_api (facultatif sur un projet public)")
    String token;

    @Option(names = "--path", required = true,
            description = "chemin du projet : groupe/sous-groupe/projet, un id numérique, ou une URL")
    String path;

    @Option(names = "--days", defaultValue = "180",
            description = "fenêtre d'observation (défaut : ${DEFAULT-VALUE})")
    int days;

    @Option(names = "--max-commits", defaultValue = "5000",
            description = "plafond de commits rapatriés (défaut : ${DEFAULT-VALUE})")
    int maxCommits;

    @Option(names = "--max-mrs", defaultValue = "500",
            description = "plafond de merge requests rapatriées (défaut : ${DEFAULT-VALUE})")
    int maxMrs;

    @Option(names = "--sample", defaultValue = "30",
            description = "MR échantillonnées pour le délai de revue (défaut : ${DEFAULT-VALUE})")
    int sample;

    @Option(names = "--include-bots",
            description = "compter les bots comme des contributeurs")
    boolean includeBots;

    @Option(names = "--bot-pattern",
            description = "expression régulière identifiant les bots (remplace celle par défaut)")
    String botPattern;

    @Option(names = "--dump-dir", description = "répertoire où consigner les réponses brutes")
    Path dumpDir;

    @Option(names = "--timeout", defaultValue = "30")
    int timeout;

    @Option(names = "--insecure", description = "ignorer la validation TLS")
    boolean insecure;

    @Option(names = "--color", defaultValue = "auto",
            description = "auto | always | never (défaut : ${DEFAULT-VALUE})")
    String colorMode;

    private Gitlab gl;
    private Pattern bots;

    public static void main(String[] args) {
        ConsoleOut.install();
        System.exit(new CommandLine(new GitLabProjectReport()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        ConsoleOut.colorMode(colorMode);
        if (isBlank(token)) {
            // Un projet public de gitlab.com se lit sans jeton. C'est marginal en
            // audit, mais c'est le seul moyen de vérifier l'outil contre une vraie
            // instance sans en posséder une — et le README dit assez pourquoi on
            // ne vérifie pas contre un bouchon.
            System.out.println(c("  Aucun jeton : lecture anonyme, projets publics seulement.", YELLOW));
        }
        if (isBlank(url)) url = "https://gitlab.com";
        bots = Pattern.compile(isBlank(botPattern) ? Gitlab.BOT_PATTERN : botPattern,
                Pattern.CASE_INSENSITIVE);
        gl = new Gitlab(url, token, timeout, insecure, dumpDir);

        String projectPath = normalizePath(path);
        LocalDateTime since = LocalDateTime.now().minusDays(days);

        Project project = fetchProject(projectPath);
        if (project == null) return 2;

        identity(project);
        Commits commits = commits(project, since);
        Cadence cadence = cadence(commits, since);
        People people = people(project, commits);
        Reviews reviews = reviews(project, since);
        findings(project, commits, cadence, people, reviews);

        System.out.println();
        System.out.println(c("  %d appels API.".formatted(gl.calls()), DIM));
        return 0;
    }

    /**
     * Le chemin est ce que l'utilisateur a sous les yeux : il colle en général
     * une URL de navigateur, parfois avec /-/tree/main au bout. On accepte les
     * trois formes plutôt que d'exiger la seule que l'API comprend.
     */
    static String normalizePath(String raw) {
        String p = raw.trim();
        int scheme = p.indexOf("://");
        if (scheme >= 0) {
            int slash = p.indexOf('/', scheme + 3);
            p = (slash < 0) ? "" : p.substring(slash + 1);
        }
        int dash = p.indexOf("/-/");                 // .../-/tree/main, .../-/issues
        if (dash >= 0) p = p.substring(0, dash);
        p = p.replaceAll("^/+", "").replaceAll("/+$", "");
        if (p.endsWith(".git")) p = p.substring(0, p.length() - 4);
        return p;
    }

    // ----------------------------------------------------------------------
    // 1. Identité
    // ----------------------------------------------------------------------

    private Project fetchProject(String projectPath) {
        Gitlab.Response r = gl.get("projects/" + enc(projectPath), params("statistics", "true"));
        Project p = r.as(Project.class);
        if (p == null || p.id() == null) {
            System.err.println("Projet introuvable : " + projectPath);
            System.err.println("  HTTP " + r.status() + " — " + r.errorMessage());
            if (r.status() == 404) {
                System.err.println("""
                          404 ne distingue pas « n'existe pas » de « pas de droit de lecture » :
                          GitLab masque les projets privés. Vérifier le chemin, puis la portée
                          du jeton (read_api) et l'accès au projet.""");
            }
            return null;
        }
        return p;
    }

    private void identity(Project p) {
        title("1. Le projet");
        System.out.printf("  Chemin                          : %s%n", c(orEmpty(p.pathWithNamespace()), BOLD));
        System.out.printf("  Id / branche par défaut         : %s / %s%n",
                p.id(), orNa(p.defaultBranch()));
        System.out.printf("  Visibilité                      : %s%s%n", orNa(p.visibility()),
                Boolean.TRUE.equals(p.archived()) ? c("   ARCHIVÉ", YELLOW) : "");

        LocalDateTime created = parseLocal(p.createdAt());
        LocalDateTime active = parseLocal(p.lastActivityAt());
        LocalDateTime now = LocalDateTime.now();
        System.out.printf("  Créé le                         : %s%s%n", dateOnly(p.createdAt()),
                created == null ? "" : c("   (il y a %d j)".formatted(ChronoUnit.DAYS.between(created, now)), DIM));
        System.out.printf("  Dernière activité               : %s%s%n", dateOnly(p.lastActivityAt()),
                active == null ? "" : c("   (il y a %d j)".formatted(ChronoUnit.DAYS.between(active, now)), DIM));

        if (p.statistics() != null) {
            System.out.printf("  Commits (toute l'histoire)      : %s%n", orNa(str(p.statistics().commitCount())));
            System.out.printf("  Taille du dépôt                 : %s%n", humanBytes(p.statistics().repositorySize()));
        } else {
            // statistics=true est ignoré silencieusement en dessous de Reporter.
            System.out.println(c("  Statistiques du dépôt           : indisponibles (rôle < Reporter ?)", DIM));
        }
        if (isBlank(p.defaultBranch())) {
            System.out.println(c("  → dépôt sans branche par défaut : vide, ou jamais poussé.", YELLOW));
        }
    }

    // ----------------------------------------------------------------------
    // 2. Cadence
    // ----------------------------------------------------------------------

    /**
     * Les commits sont lus sur la seule branche par défaut. C'est délibéré :
     * `all=true` ramène aussi les branches de travail abandonnées et les
     * commits qui n'ont jamais été fusionnés, ce qui gonfle la cadence d'un
     * travail qui n'a jamais atterri.
     */
    private Commits commits(Project p, LocalDateTime since) {
        if (isBlank(p.defaultBranch())) return new Commits(List.of(), List.of(), false, "pas de branche par défaut");

        Gitlab.Page<Commit> f = gl.paged("projects/" + p.id() + "/repository/commits",
                params("ref_name", p.defaultBranch(),
                        "since", since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "with_stats", "true"),
                Commit.class, maxCommits);

        List<Commit> all = f.items();
        List<Commit> humans = includeBots ? all
                : all.stream().filter(x -> !isBot(x.authorName(), x.authorEmail())).toList();
        List<Commit> botCommits = all.stream()
                .filter(x -> isBot(x.authorName(), x.authorEmail())).toList();
        return new Commits(humans, botCommits, f.truncated(), f.error());
    }

    private Cadence cadence(Commits commits, LocalDateTime since) {
        title("2. Cadence (branche par défaut, %d derniers jours)".formatted(days));

        if (commits.error() != null) {
            line("repository/commits", Verdict.ERROR, commits.error());
            return Cadence.EMPTY;
        }
        List<Commit> cs = commits.humans();
        if (cs.isEmpty()) {
            System.out.println(c("  Aucun commit humain sur la fenêtre.", YELLOW));
            if (!commits.bots().isEmpty()) {
                System.out.printf("  (%d commits de bots sur la même période)%n", commits.bots().size());
            }
            return Cadence.EMPTY;
        }
        if (commits.truncated()) {
            System.out.println(c("  Plafond --max-commits atteint : les chiffres portent sur les %d plus récents."
                    .formatted(maxCommits), YELLOW));
        }

        // parent_ids > 1 = commit de fusion. Il ne porte aucun travail propre : ses
        // lignes sont celles des commits qu'il réunit, et sa date est celle du clic
        // sur « fusionner », par quelqu'un qui n'a pas écrit le code. Tout ce qui
        // suit se mesure donc sur les seuls commits ordinaires.
        List<Commit> real = cs.stream().filter(x -> !x.isMerge()).toList();
        int merges = cs.size() - real.size();
        if (real.isEmpty()) {
            System.out.println(c("  Que des commits de fusion : rien à mesurer sur cette branche.", YELLOW));
            return Cadence.EMPTY;
        }

        List<LocalDateTime> dates = real.stream().map(x -> parseLocal(x.committedDate()))
                .filter(Objects::nonNull).sorted().toList();
        long activeDays = dates.stream().map(LocalDateTime::toLocalDate).distinct().count();

        // Sous plafond, la fenêtre réellement couverte n'est plus --days mais celle
        // qui remonte au plus ancien commit rapatrié. Rapporter le débit aux --days
        // demandés donnerait un taux mécaniquement sous-estimé, et présenté comme
        // une mesure.
        double observed = days;
        if (commits.truncated() && !dates.isEmpty()) {
            observed = Math.max(1, ChronoUnit.DAYS.between(dates.get(0), LocalDateTime.now()));
        }
        double weeks = Math.max(1.0, observed / 7.0);

        System.out.printf("  Commits (hors bots)             : %s%n", c(String.valueOf(cs.size()), BOLD));
        if (!commits.bots().isEmpty()) {
            System.out.printf("  Commits de bots écartés         : %d%n", commits.bots().size());
        }
        if (merges > 0) {
            System.out.printf("  dont commits de fusion          : %d %s%n", merges,
                    c("(écartés de toutes les mesures ci-dessous)", DIM));
        }
        System.out.printf("  Rythme                          : %.1f commits / semaine%s%n",
                real.size() / weeks,
                commits.truncated() ? c("   (sur les %.0f j couverts)".formatted(observed), DIM) : "");
        System.out.printf("  Jours avec au moins un commit   : %d sur %.0f%n", activeDays, observed);

        // L'écart médian dit le rythme ordinaire, le plus long silence dit s'il
        // y a eu abandon puis reprise. La moyenne, elle, ne dit ni l'un ni l'autre.
        List<Long> gaps = new ArrayList<>();
        for (int i = 1; i < dates.size(); i++) {
            gaps.add(ChronoUnit.HOURS.between(dates.get(i - 1), dates.get(i)));
        }
        if (!gaps.isEmpty()) {
            Collections.sort(gaps);
            System.out.printf("  Écart médian entre commits      : %s%n", hours(percentile(gaps, 50)));
            System.out.printf("  Plus long silence               : %s%n", hours(gaps.get(gaps.size() - 1)));
        }

        // L'heure est lue dans le décalage porté par la date elle-même, donc dans
        // le fuseau de la machine qui a commité : c'est l'heure locale de la
        // personne, et non celle du serveur ou de l'auditeur.
        int[] byDow = new int[7];
        int outsideHours = 0, weekend = 0, dated = 0;
        for (Commit x : real) {
            OffsetDateTime o = parseOffset(x.authoredDate());
            if (o == null) continue;
            dated++;
            DayOfWeek dow = o.getDayOfWeek();
            byDow[dow.getValue() - 1]++;
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) weekend++;
            int h = o.getHour();
            if (h < 8 || h >= 19) outsideHours++;
        }
        if (dated > 0) {
            System.out.println();
            System.out.printf("  Répartition hebdomadaire        : %s%n", dowBar(byDow));
            System.out.printf("  Week-end                        : %.0f %%%n", 100.0 * weekend / dated);
            System.out.printf("  Hors 8h-19h (heure de l'auteur) : %.0f %%%n", 100.0 * outsideHours / dated);
        }

        // Volumes. stats est absent si le jeton ne peut pas lire le diff, et
        // reste null plutôt que zéro : « pas mesuré » n'est pas « rien changé ».
        List<Long> sizes = new ArrayList<>();
        long added = 0, deleted = 0;
        for (Commit x : real) {
            if (x.stats() == null) continue;
            long a = orZero(x.stats().additions()), d = orZero(x.stats().deletions());
            added += a;
            deleted += d;
            sizes.add(a + d);
        }
        double bigShare = 0;
        if (!sizes.isEmpty()) {
            Collections.sort(sizes);
            long big = sizes.stream().filter(s -> s > BIG_COMMIT).count();
            bigShare = 100.0 * big / sizes.size();
            System.out.println();
            System.out.printf("  Taille médiane d'un commit      : %.0f lignes%n", percentile(sizes, 50));
            System.out.printf("  9e décile                       : %.0f lignes%n", percentile(sizes, 90));
            System.out.printf("  Commits > %d lignes           : %d (%.0f %%) %s%n",
                    BIG_COMMIT, big, bigShare, c("— imports, code généré, vendoring ?", DIM));
            System.out.printf("  Lignes touchées / lignes nettes : %s%n", rework(added, deleted));
            System.out.println(c("    (le net masque la réécriture : un fichier refait cinq fois"
                    + " ne bouge pas d'une ligne)", DIM));
        } else if (!real.isEmpty()) {
            System.out.println(c("  Volumes de lignes indisponibles (stats absentes de la réponse).", DIM));
        }
        return new Cadence(real.size(), activeDays, bigShare, dated == 0 ? 0 : 100.0 * weekend / dated,
                dates.isEmpty() ? null : dates.get(dates.size() - 1));
    }

    /** Rapport entre lignes touchées et solde net : au-delà de 3, on refait plus qu'on n'ajoute. */
    private static String rework(long added, long deleted) {
        long touched = added + deleted;
        long net = Math.abs(added - deleted);
        if (net == 0) return "%d touchées, solde nul".formatted(touched);
        return "%d / %d = %.1f".formatted(touched, net, (double) touched / net);
    }

    // ----------------------------------------------------------------------
    // 3. Contributeurs
    // ----------------------------------------------------------------------

    private People people(Project p, Commits commits) {
        title("3. Contributeurs et concentration");

        // Hors commits de fusion, ici encore : le mainteneur qui clique « fusionner »
        // n'a pas écrit le code, et compter ses fusions le sacre premier contributeur.
        List<Commit> cs = commits.humans().stream().filter(x -> !x.isMerge()).toList();
        if (cs.isEmpty()) {
            System.out.println(c("  Pas de commit sur la fenêtre : rien à mesurer.", DIM));
            return People.EMPTY;
        }

        // Identité normalisée par Gitlab.identity, la même que côté parc : partie
        // locale de l'adresse, à défaut le nom, les deux ramenés dans le même
        // espace de noms. Sans cette dernière précaution, un commit signé « Dev 0 »
        // sans adresse et un commit de dev0@example.com font deux personnes, et le
        // bus factor double en silence.
        Map<String, Long> byEmail = cs.stream().collect(Collectors.groupingBy(
                x -> Gitlab.identity(x.authorEmail(), x.authorName()),
                Collectors.counting()));

        // L'identité normalisée est illisible : on réaffiche le nom porté par les
        // commits, qui est ce que l'auditeur reconnaîtra.
        Map<String, String> labels = new HashMap<>();
        for (Commit x : cs) {
            if (!isBlank(x.authorName())) {
                labels.putIfAbsent(Gitlab.identity(x.authorEmail(), x.authorName()), x.authorName());
            }
        }
        long distinctEmails = cs.stream().map(x -> orEmpty(x.authorEmail()).trim().toLowerCase())
                .filter(e -> !e.isBlank()).distinct().count();
        long distinctNames = cs.stream().map(x -> orEmpty(x.authorName()).trim().toLowerCase())
                .filter(n -> !n.isBlank()).distinct().count();

        List<Map.Entry<String, Long>> ranked = byEmail.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).toList();
        long total = cs.size();

        System.out.printf("  Auteurs distincts               : %s%n",
                c(String.valueOf(ranked.size()), BOLD));
        // Ce que la normalisation a regroupé, dit plutôt que subi : le compte brut
        // d'adresses est celui qu'on lirait sans elle.
        if (distinctEmails > ranked.size()) {
            System.out.printf("  Adresses distinctes             : %d %s%n", distinctEmails,
                    c("— ramenées à %d personnes".formatted(ranked.size()), DIM));
        }
        // Une identité qui porte plusieurs noms, c'est soit un poste partagé, soit
        // deux homonymes de partie locale sur des domaines différents — auquel cas
        // la normalisation a fusionné deux personnes, et le bus factor est optimiste.
        if (distinctNames > ranked.size()) {
            System.out.printf("  Noms distincts                  : %d %s%n", distinctNames,
                    c("— une identité porte plusieurs noms", DIM));
        }

        // Bus factor : combien de personnes il faut réunir pour couvrir 80 % des
        // commits. Un, c'est une personne dont le départ arrête le projet.
        long cumulative = 0;
        int busFactor = 0;
        for (Map.Entry<String, Long> e : ranked) {
            cumulative += e.getValue();
            busFactor++;
            if (cumulative >= 0.8 * total) break;
        }
        double topShare = 100.0 * ranked.get(0).getValue() / total;
        System.out.printf("  Auteur principal                : %s (%.0f %% des commits)%n",
                label(labels, ranked.get(0).getKey()), topShare);
        System.out.printf("  Personnes couvrant 80 %% du code : %s%n",
                c(String.valueOf(busFactor), busFactor <= 1 ? YELLOW : BOLD));

        int shown = Math.min(5, ranked.size());
        System.out.println();
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Long> e = ranked.get(i);
            System.out.printf("    %-34s %4d  %s%n", label(labels, e.getKey()), e.getValue(),
                    bar(100.0 * e.getValue() / total));
        }
        if (ranked.size() > shown) {
            System.out.println(c("    … et %d autres".formatted(ranked.size() - shown), DIM));
        }

        // Actifs récemment vs actifs sur la fenêtre : l'écart, ce sont les gens
        // partis. Un fichier dont tous les auteurs ont quitté le projet n'a plus
        // de propriétaire, quel que soit son état de santé.
        int recentDays = Math.min(90, days);
        LocalDateTime recent = LocalDateTime.now().minusDays(recentDays);
        long activeRecently = cs.stream()
                .filter(x -> { LocalDateTime d = parseLocal(x.committedDate()); return d != null && d.isAfter(recent); })
                .map(x -> Gitlab.identity(x.authorEmail(), x.authorName())).distinct().count();
        System.out.println();
        System.out.printf("  %-32s: %d sur %d%n",
                "Actifs sur " + recentDays + " j", activeRecently, ranked.size());

        if (!commits.bots().isEmpty()) {
            Set<String> botNames = commits.bots().stream().map(Commit::authorName)
                    .filter(s -> !isBlank(s)).collect(Collectors.toCollection(TreeSet::new));
            System.out.println(c("  Écartés comme bots              : " + String.join(", ", botNames), DIM));
        }

        // Vue « toute l'histoire », en un appel : elle situe la fenêtre dans la
        // durée de vie du dépôt. Ses comptes incluent les commits de fusion —
        // ils ne sont donc pas comparables terme à terme avec ceux du dessus.
        Gitlab.Page<Contributor> f = gl.paged("projects/" + p.id() + "/repository/contributors",
                params("order_by", "commits", "sort", "desc"), Contributor.class, 500);
        if (f.error() == null && !f.items().isEmpty()) {
            System.out.printf("  Contributeurs de tout temps     : %d %s%n", f.items().size(),
                    c("(commits de fusion inclus)", DIM));
        }
        return new People(ranked.size(), busFactor, topShare, activeRecently);
    }

    // ----------------------------------------------------------------------
    // 4. Merge requests
    // ----------------------------------------------------------------------

    private Reviews reviews(Project p, LocalDateTime since) {
        title("4. Merge requests et revue");

        Gitlab.Page<MergeRequest> f = gl.paged("projects/" + p.id() + "/merge_requests",
                params("scope", "all", "state", "all",
                        "created_after", since.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        "order_by", "created_at", "sort", "desc"),
                MergeRequest.class, maxMrs);

        if (f.error() != null) {
            line("merge_requests", Verdict.ERROR, f.error());
            return Reviews.EMPTY;
        }
        List<MergeRequest> all = f.items();
        if (all.isEmpty()) {
            System.out.println(c("  Aucune MR créée sur la fenêtre.", YELLOW));
            System.out.println(c("""
                    \s   Le travail arrive donc directement sur la branche par défaut.
                      Ce n'est pas une absence de données : c'est l'absence de revue.""", DIM));
            return new Reviews(0, 0, 0, 0, 0, 0, 0, 0, true);
        }
        if (f.truncated()) {
            System.out.println(c("  Plafond --max-mrs atteint : chiffres portant sur les %d plus récentes."
                    .formatted(maxMrs), YELLOW));
        }

        List<MergeRequest> merged = all.stream().filter(m -> "merged".equals(m.state())).toList();
        long closed = all.stream().filter(m -> "closed".equals(m.state())).count();
        long opened = all.stream().filter(m -> "opened".equals(m.state())).count();

        double observed = days;
        if (f.truncated()) {
            LocalDateTime oldest = all.stream().map(m -> parseLocal(m.createdAt()))
                    .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            if (oldest != null) observed = Math.max(1, ChronoUnit.DAYS.between(oldest, LocalDateTime.now()));
        }
        double weeks = Math.max(1.0, observed / 7.0);

        System.out.printf("  MR créées                       : %s (%.1f / semaine)%s%n",
                c(String.valueOf(all.size()), BOLD), all.size() / weeks,
                f.truncated() ? c("   (sur les %.0f j couverts)".formatted(observed), DIM) : "");
        System.out.printf("  Fusionnées / fermées / ouvertes : %d / %d / %d%n",
                merged.size(), closed, opened);

        // Délai de bout en bout. La médiane décrit le quotidien, le 9e décile
        // décrit ce qui coince — et c'est là que se loge la dysfonction.
        List<Long> lead = new ArrayList<>();
        for (MergeRequest m : merged) {
            LocalDateTime a = parseLocal(m.createdAt()), b = parseLocal(m.mergedAt());
            if (a != null && b != null && !b.isBefore(a)) lead.add(ChronoUnit.HOURS.between(a, b));
        }
        double leadMedian = 0, leadP90 = 0;
        if (!lead.isEmpty()) {
            Collections.sort(lead);
            leadMedian = percentile(lead, 50);
            leadP90 = percentile(lead, 90);
            System.out.printf("  Délai création → fusion         : médiane %s, 9e décile %s%n",
                    hours(leadMedian), hours(leadP90));
        }

        // user_notes_count est fourni par la liste : la profondeur d'échange ne
        // coûte donc aucun appel supplémentaire. Zéro note = personne n'a rien dit.
        long silent = all.stream().filter(m -> orZero(m.userNotesCount()) == 0).count();
        double silentShare = 100.0 * silent / all.size();
        System.out.printf("  MR sans aucun commentaire       : %d (%.0f %%)%n", silent, silentShare);

        // Fusionnée par son auteur : la revue n'a pas eu lieu, ou n'a pas pesé.
        // merge_user est absent des versions plus anciennes ; on ne compte alors
        // que les MR où l'information existe, et on le dit.
        long withMerger = merged.stream().filter(m -> m.mergedByUsername() != null).count();
        long selfMerged = merged.stream()
                .filter(m -> m.mergedByUsername() != null && m.authorUsername() != null)
                .filter(m -> m.mergedByUsername().equals(m.authorUsername())).count();
        double selfShare = withMerger == 0 ? -1 : 100.0 * selfMerged / withMerger;
        if (merged.isEmpty()) {
            System.out.println(c("  Auto-fusion                     : aucune MR fusionnée sur la fenêtre", DIM));
        } else if (withMerger == 0) {
            System.out.println(c("  Auto-fusion                     : non mesurable (merge_user absent)", DIM));
        } else {
            System.out.printf("  Fusionnées par leur auteur      : %d sur %d (%.0f %%)%n",
                    selfMerged, withMerger, selfShare);
        }

        long squashed = all.stream().filter(m -> Boolean.TRUE.equals(m.squash())).count();
        double squashShare = 100.0 * squashed / all.size();
        System.out.printf("  Squash à la fusion              : %.0f %%%s%n", squashShare,
                squashShare >= 50 ? c("   (écrase l'historique par commit)", DIM) : "");

        double reviewLatency = sampleReviewLatency(p, merged);

        return new Reviews(all.size(), merged.size(), leadMedian, leadP90, silentShare,
                selfShare, reviewLatency, squashShare, false);
    }

    /**
     * Le délai jusqu'à la première relecture demande un appel par MR : on
     * échantillonne, et on affiche la taille de l'échantillon plutôt que de
     * laisser croire à une mesure exhaustive.
     */
    private double sampleReviewLatency(Project p, List<MergeRequest> merged) {
        if (merged.isEmpty() || sample <= 0) return -1;
        List<MergeRequest> subset = merged.subList(0, Math.min(sample, merged.size()));

        List<Long> latencies = new ArrayList<>();
        int unreviewed = 0, measured = 0;
        String error = null;
        for (MergeRequest m : subset) {
            Gitlab.Page<Note> f = gl.paged("projects/" + p.id() + "/merge_requests/" + m.iid() + "/notes",
                    params("sort", "asc", "order_by", "created_at"), Note.class, 100);
            // Un refus se répète : on s'arrête au premier plutôt que de brûler
            // l'échantillon entier en 401, et surtout on le dit. Une section qui
            // disparaît sans un mot ressemble à une absence de sujet.
            if (f.error() != null) { error = f.error(); break; }
            measured++;
            // Les notes système (« a assigné », « a poussé ») ne sont pas des
            // relectures : sans ce filtre, toute MR paraît relue dans la seconde.
            Optional<LocalDateTime> first = f.items().stream()
                    .filter(n -> !Boolean.TRUE.equals(n.system()))
                    .filter(n -> n.authorUsername() != null && !n.authorUsername().equals(m.authorUsername()))
                    .map(n -> parseLocal(n.createdAt()))
                    .filter(Objects::nonNull).min(Comparator.naturalOrder());
            LocalDateTime created = parseLocal(m.createdAt());
            if (first.isEmpty()) unreviewed++;
            else if (created != null) latencies.add(Math.max(0, ChronoUnit.HOURS.between(created, first.get())));
        }
        System.out.println();
        if (error != null) {
            line("merge_requests/:iid/notes", Verdict.DENIED, error);
            System.out.println(c("    Délai de revue et part de MR non relues : non mesurés.", YELLOW));
            System.out.println(c("    Les notes exigent un jeton, même sur un projet public.", YELLOW));
            return -1;
        }
        if (measured == 0) return -1;

        System.out.printf("  Échantillon de revue            : %d MR fusionnées%n", measured);
        System.out.printf("  Fusionnées sans relecture       : %d (%.0f %%) %s%n", unreviewed,
                100.0 * unreviewed / measured, c("— aucun commentaire d'un tiers", DIM));
        if (latencies.isEmpty()) return -1;
        Collections.sort(latencies);
        double median = percentile(latencies, 50);
        System.out.printf("  Délai avant première relecture  : médiane %s, 9e décile %s%n",
                hours(median), hours(percentile(latencies, 90)));
        return median;
    }

    // ----------------------------------------------------------------------
    // 5. Constats
    // ----------------------------------------------------------------------

    /**
     * Rien ici n'est calculé : la section relit ce que les précédentes ont
     * mesuré et n'en retient que ce qui sort de l'ordinaire. Les seuils sont
     * grossiers et assumés comme tels — ils servent à orienter un entretien,
     * pas à noter une équipe.
     */
    private void findings(Project p, Commits commits, Cadence cadence, People people, Reviews reviews) {
        title("5. Constats");
        List<String> found = new ArrayList<>();

        if (Boolean.TRUE.equals(p.archived())) {
            found.add("Projet archivé : lecture historique seulement.");
        }
        if (cadence.lastCommit() != null) {
            long idle = ChronoUnit.DAYS.between(cadence.lastCommit(), LocalDateTime.now());
            if (idle > 90) found.add("Dernier commit il y a %d j : dépôt dormant.".formatted(idle));
        }
        LocalDateTime activity = parseLocal(p.lastActivityAt());
        if (cadence.lastCommit() != null && activity != null
                && ChronoUnit.DAYS.between(cadence.lastCommit(), LocalDateTime.now()) > 180
                && ChronoUnit.DAYS.between(activity, LocalDateTime.now()) < 90) {
            found.add("Le dépôt bouge encore (MR, tickets) mais plus la branche par défaut :"
                    + " le travail vit ailleurs, ou personne ne fusionne plus.");
        }
        if (cadence.commits() > 0 && reviews.noMrs()) {
            found.add("%d commits, aucune MR : le code arrive sans revue sur la branche par défaut."
                    .formatted(cadence.commits()));
        }
        if (reviews.selfMergedShare() > 50) {
            found.add("%.0f %% des MR sont fusionnées par leur auteur : la revue est facultative."
                    .formatted(reviews.selfMergedShare()));
        }
        if (reviews.mrs() > 0 && reviews.silentShare() > 60) {
            found.add("%.0f %% des MR n'ont aucun commentaire : la revue est un tampon."
                    .formatted(reviews.silentShare()));
        }
        if (reviews.leadP90() > 30 * 24) {
            found.add("9e décile du délai de fusion à %s : une MR sur dix traîne un mois."
                    .formatted(hours(reviews.leadP90())));
        }
        if (people.authors() > 0 && people.busFactor() <= 1) {
            found.add("Une seule personne signe 80 %% des commits (%.0f %% pour la première)."
                    .formatted(people.topShare()));
        }
        if (people.authors() > 2 && people.activeRecently() <= 1) {
            found.add("%d auteurs sur la fenêtre, %d actif sur 90 j : l'équipe s'est vidée."
                    .formatted(people.authors(), people.activeRecently()));
        }
        if (cadence.bigCommitShare() > 10) {
            found.add("%.0f %% des commits dépassent %d lignes : imports, code généré ou gros lots."
                    .formatted(cadence.bigCommitShare(), BIG_COMMIT));
        }
        if (cadence.weekendShare() > 25) {
            found.add("%.0f %% des commits le week-end.".formatted(cadence.weekendShare()));
        }
        if (reviews.squashShare() > 80) {
            found.add("Squash quasi systématique : l'historique par commit n'existe plus,"
                    + " la cadence ci-dessus mesure le bouton « fusionner ».");
        }
        if (!commits.bots().isEmpty()
                && commits.bots().size() > commits.humans().size()) {
            found.add("Plus de commits de bots que d'humains : dépôt surtout maintenu par l'outillage.");
        }

        if (found.isEmpty()) {
            System.out.println(c("  Rien de saillant sur les seuils retenus.", GREEN));
            return;
        }
        for (String s : found) System.out.println(c("  → " + s, YELLOW));
        System.out.println();
        System.out.println(c("""
                \s Ces constats décrivent un procédé, pas une qualité de code. Le croisement
                  avec Sonar — fréquence de modification contre complexité, quality gate
                  contre MR fusionnées — reste à faire.""", DIM));
    }

    // ----------------------------------------------------------------------
    // Bots
    // ----------------------------------------------------------------------

    private boolean isBot(String name, String email) {
        return Gitlab.isBot(bots, email, name);
    }

    // ----------------------------------------------------------------------
    // Records
    //
    // Tout champ numérique est boxé, pour la même raison que côté Sonar :
    // une donnée absente doit rester null et ne pas se lire comme un zéro.
    // Les noms JSON de GitLab sont en snake_case et déclarés explicitement —
    // une stratégie de nommage globale se contenterait de rendre null les
    // champs mal devinés, sans la moindre erreur.
    // ----------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Project(Long id,
                   String name,
                   @JsonProperty("path_with_namespace") String pathWithNamespace,
                   @JsonProperty("default_branch") String defaultBranch,
                   String visibility,
                   Boolean archived,
                   @JsonProperty("created_at") String createdAt,
                   @JsonProperty("last_activity_at") String lastActivityAt,
                   Statistics statistics) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Statistics(@JsonProperty("commit_count") Long commitCount,
                          @JsonProperty("repository_size") Long repositorySize) { }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Commit(String id,
                  @JsonProperty("short_id") String shortId,
                  String title,
                  @JsonProperty("author_name") String authorName,
                  @JsonProperty("author_email") String authorEmail,
                  @JsonProperty("authored_date") String authoredDate,
                  @JsonProperty("committed_date") String committedDate,
                  @JsonProperty("parent_ids") List<String> parentIds,
                  Stats stats) {

        /** Deux parents ou plus : commit de fusion, dont les lignes appartiennent à d'autres. */
        boolean isMerge() { return Gitlab.isMerge(parentIds); }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Stats(Integer additions, Integer deletions, Integer total) { }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Contributor(String name, String email, Integer commits,
                       Integer additions, Integer deletions) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MergeRequest(Long iid,
                        String state,
                        String title,
                        @JsonProperty("created_at") String createdAt,
                        @JsonProperty("merged_at") String mergedAt,
                        @JsonProperty("closed_at") String closedAt,
                        @JsonProperty("target_branch") String targetBranch,
                        Boolean draft,
                        Boolean squash,
                        @JsonProperty("user_notes_count") Integer userNotesCount,
                        User author,
                        @JsonProperty("merge_user") User mergeUser,
                        @JsonProperty("merged_by") User mergedBy) {

        String authorUsername() { return author == null ? null : author.username(); }

        /** merged_by est déprécié au profit de merge_user : on lit les deux. */
        String mergedByUsername() {
            if (mergeUser != null && mergeUser.username() != null) return mergeUser.username();
            return mergedBy == null ? null : mergedBy.username();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Note(Long id,
                Boolean system,
                @JsonProperty("created_at") String createdAt,
                User author) {

        String authorUsername() { return author == null ? null : author.username(); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(Long id, String username, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GitLabError(String message, String error) { }

    // Agrégats internes, passés à la section « Constats ».

    record Commits(List<Commit> humans, List<Commit> bots, boolean truncated, String error) { }

    record Cadence(int commits, long activeDays, double bigCommitShare,
                   double weekendShare, LocalDateTime lastCommit) {
        static final Cadence EMPTY = new Cadence(0, 0, 0, 0, null);
    }

    record People(int authors, int busFactor, double topShare, long activeRecently) {
        static final People EMPTY = new People(0, 0, 0, 0);
    }

    record Reviews(int mrs, int merged, double leadMedian, double leadP90,
                   double silentShare, double selfMergedShare, double reviewLatency,
                   double squashShare, boolean noMrs) {
        static final Reviews EMPTY = new Reviews(0, 0, 0, 0, 0, 0, 0, 0, false);
    }

    // ----------------------------------------------------------------------
    // Présentation
    // ----------------------------------------------------------------------

    static final String BOLD = "\033[1m", DIM = "\033[2m", RESET = "\033[0m";
    static final String GREEN = "\033[32m", RED = "\033[31m", YELLOW = "\033[33m";
    static final int BIG_COMMIT = 2000;

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

    static String bar(double percent) {
        int n = (int) Math.round(percent / 5);
        return "#".repeat(Math.max(0, Math.min(20, n)));
    }

    static String dowBar(int[] byDow) {
        String[] labels = {"L", "M", "M", "J", "V", "S", "D"};
        int max = Arrays.stream(byDow).max().orElse(1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            int h = max == 0 ? 0 : (int) Math.round(5.0 * byDow[i] / max);
            sb.append(labels[i]).append(":").append("▁▂▃▅▆█".charAt(Math.min(5, h))).append(' ');
        }
        return sb.toString().trim();
    }

    // ----------------------------------------------------------------------
    // Utilitaires
    // ----------------------------------------------------------------------

    /**
     * GitLab date en ISO-8601 avec décalage : « 2026-08-18T09:12:33.000+02:00 »
     * ou en Z. On garde l'OffsetDateTime tel quel là où le décalage porte du
     * sens — l'heure de la journée est celle de la personne qui a commité —
     * et on ramène à l'heure locale pour les calculs d'âge.
     */
    static OffsetDateTime parseOffset(String s) {
        return Gitlab.parse(s);
    }

    static LocalDateTime parseLocal(String s) {
        OffsetDateTime o = parseOffset(s);
        return o == null ? null : o.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    static String dateOnly(String s) {
        LocalDateTime d = parseLocal(s);
        return d == null ? "n/a" : d.toLocalDate().toString();
    }

    /** Interpolation linéaire sur une liste déjà triée. */
    static double percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) return 0;
        if (sorted.size() == 1) return sorted.get(0);
        double rank = (p / 100.0) * (sorted.size() - 1);
        int lo = (int) Math.floor(rank), hi = (int) Math.ceil(rank);
        return sorted.get(lo) + (rank - lo) * (sorted.get(hi) - sorted.get(lo));
    }

    static String hours(double h) {
        if (h * 60 < 1) return "< 1 min";
        if (h < 1) return "%.0f min".formatted(h * 60);
        if (h < 48) return "%.1f h".formatted(h);
        return "%.1f j".formatted(h / 24);
    }

    static String humanBytes(Long bytes) {
        if (bytes == null) return "n/a";
        double v = bytes;
        String[] units = {"o", "Ko", "Mo", "Go"};
        int i = 0;
        while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
        return "%.1f %s".formatted(v, units[i]);
    }

    static String label(Map<String, String> labels, String identity) {
        return truncate(labels.getOrDefault(identity, identity), 32);
    }

    static Map<String, String> params(String... pairs) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
        return m;
    }

    static String enc(String s) {
        return Gitlab.enc(s);
    }

    static int orZero(Integer i) { return i == null ? 0 : i; }

    static String str(Object o) { return o == null ? null : String.valueOf(o); }

    static boolean isBlank(String s) { return s == null || s.isBlank(); }

    static String orEmpty(String s) { return s == null ? "" : s; }

    static String orNa(String s) { return isBlank(s) ? "n/a" : s; }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
