///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//SOURCES ConsoleOut.java
//SOURCES Csv.java
//SOURCES NameMatcher.java

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

        if (!sonar.has("alm") || !gitlab.has("id")) {
            System.out.println(c("\n  Liaison DevOps non lisible : " + (!sonar.has("alm")
                    ? "l'inventaire Sonar ne porte pas de colonne alm (SonarAuditCheck"
                    + "\n  récent, sans --no-bindings)."
                    : "pratiques.csv ne porte pas de colonne id (GitlabActivityAudit récent)."), YELLOW));
        }

        List<Pair> pairs = join(sonar, gitlab);
        matchRate(pairs, sonar, gitlab);
        crossChecks(pairs, sonar);
        findings(pairs);
        if (out != null) write(pairs);
        return 0;
    }

    // ----------------------------------------------------------------------
    // La jointure
    // ----------------------------------------------------------------------

    /**
     * Les méthodes, de la plus sûre à la moins sûre. L'ordre est celui du
     * tableau de GITLAB_ANALYSIS.md §6, et il est appliqué par priorité
     * décroissante : un projet apparié par sa clé de CI ne sera jamais réapparié
     * par ressemblance de nom.
     */
    enum How {
        CI_KEY("clé lue dans la CI", Confidence.EXACT),
        ALM_BINDING("liaison DevOps Sonar → GitLab", Confidence.EXACT),
        PATH_NORMALISED("clé normalisée = chemin GitLab", Confidence.DERIVED),
        NAME_FUZZY("noms ressemblants (TF-IDF)", Confidence.SUGGESTION),
        NONE("aucune", Confidence.NONE);

        final String label;
        final Confidence confidence;

        How(String label, Confidence confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }

    enum Confidence { EXACT, DERIVED, SUGGESTION, NONE }

    /**
     * Un projet GitLab et, s'il en a un, son homologue Sonar.
     *
     * {@code name} : le rapprochement de noms tenté, quand les méthodes sûres
     * ont échoué — y compris quand il a été écarté, pour dire pourquoi.
     * {@code bound} et {@code linked} : les projets Sonar dont la liaison ou
     * les liens désignent ce dépôt, quelle que soit la méthode retenue — c'est
     * ce qui permet de juger ces deux sources.
     */
    record Pair(Csv.Row gl, Csv.Row sq, How how, NameGuess name,
                List<String> bound, List<String> linked) {

        boolean joined() {
            return sq != null && how.confidence != Confidence.SUGGESTION;
        }
    }

    /** Un rapprochement de noms : le candidat, son score, le suivant, et le refus éventuel. */
    record NameGuess(String candidate, double score, String runnerUp, double runnerUpScore,
                     String rejection) { }

    private List<Pair> join(Csv.Table sonar, Csv.Table gitlab) {
        Map<String, Csv.Row> byKey = new HashMap<>();
        Map<String, Csv.Row> byNormalisedKey = new HashMap<>();
        // Plusieurs projets Sonar peuvent désigner le même dépôt : un monodépôt
        // lié plusieurs fois, ou un lien recopié. On les garde tous.
        Map<String, List<String>> boundTo = new HashMap<>();
        Map<String, List<String>> linkedTo = new HashMap<>();
        for (Csv.Row s : sonar.rows()) {
            String key = s.str("key");
            if (key.isEmpty()) continue;
            byKey.putIfAbsent(key, s);
            byNormalisedKey.putIfAbsent(normalise(key), s);
            if ("gitlab".equals(s.str("alm")) && !s.str("alm_repository").isEmpty()) {
                boundTo.computeIfAbsent(s.str("alm_repository"), k -> new ArrayList<>()).add(key);
            }
            for (String path : linkedPaths(s.str("liens"))) {
                List<String> keys = linkedTo.computeIfAbsent(path, k -> new ArrayList<>());
                if (!keys.contains(key)) keys.add(key);
            }
        }

        List<Pair> pairs = new ArrayList<>();
        List<Integer> unresolved = new ArrayList<>();
        Set<String> taken = new HashSet<>();
        for (Csv.Row g : gitlab.rows()) {
            String cle = g.str("cle_sonar");
            String path = g.str("path");
            List<String> bound = boundTo.getOrDefault(g.str("id"), List.of());
            List<String> linked = linkedTo.getOrDefault(path.toLowerCase(Locale.ROOT), List.of());

            // 1. La clé que le scanner envoie vraiment. C'est la seule méthode qui
            //    lit ce que le pipeline a reçu comme instruction, au lieu de
            //    déduire d'un nom ce qu'il a bien pu faire.
            Csv.Row hit = cle.isEmpty() ? null : byKey.get(cle);
            How how = How.CI_KEY;
            // 2. Ce que SonarQube a enregistré lui-même en important le dépôt :
            //    l'identifiant du projet GitLab. Aucun nom n'y intervient. Sur un
            //    monodépôt lié plusieurs fois, le premier projet ; les autres
            //    restent nommés dans liaison_sonar.
            if (hit == null && !bound.isEmpty()) {
                hit = byKey.get(bound.get(0));
                how = How.ALM_BINDING;
            }
            // 3. Le chemin GitLab, normalisé comme une clé Sonar l'est
            //    habituellement — séparateurs uniformisés, casse ignorée.
            if (hit == null) {
                hit = byNormalisedKey.get(normalise(path));
                how = How.PATH_NORMALISED;
            }
            if (hit != null) {
                taken.add(hit.str("key"));
                pairs.add(new Pair(g, hit, how, null, bound, linked));
            } else {
                unresolved.add(pairs.size());
                pairs.add(new Pair(g, null, How.NONE, null, bound, linked));
            }
        }

        // 4. Les noms, en dernier et sur ce qui reste seulement.
        guessByName(pairs, unresolved, sonar, taken);
        return pairs;
    }

    /**
     * La ressemblance de noms, sur les projets GitLab restés sans jointure et
     * les projets Sonar que personne n'a pris.
     *
     * Trois garde-fous, parce qu'une suggestion fausse coûte le temps d'un
     * humain qui la vérifie :
     *
     *   seuil        en dessous de --seuil-nom, rien n'est proposé ;
     *   marge        deux candidats presque ex æquo, c'est une ambiguïté, pas
     *                un choix — rien n'est proposé non plus ;
     *   un pour un   un projet Sonar ne se propose qu'une fois, au meilleur
     *                score ; les autres apprennent qui l'a pris.
     *
     * Côté GitLab, le dernier segment du chemin compte double : le namespace
     * aide à départager, il ne doit pas suffire à rapprocher.
     */
    private void guessByName(List<Pair> pairs, List<Integer> unresolved,
                             Csv.Table sonar, Set<String> taken) {
        if (unresolved.isEmpty()) return;
        List<Csv.Row> free = sonar.rows().stream()
                .filter(s -> !s.str("key").isEmpty() && !taken.contains(s.str("key")))
                .toList();
        if (free.isEmpty()) return;

        List<Map<String, Double>> srcTf = new ArrayList<>();
        List<Set<String>> srcNum = new ArrayList<>();
        for (int i : unresolved) {
            String path = pairs.get(i).gl().str("path");
            int cut = path.lastIndexOf('/');
            Map<String, Double> tf = new HashMap<>();
            NameMatcher.addTokens(tf, path.substring(cut + 1), 2.0);
            if (cut > 0) NameMatcher.addTokens(tf, path.substring(0, cut), 1.0);
            srcTf.add(tf);
            srcNum.add(NameMatcher.numbers(path.substring(cut + 1)));
        }
        List<Map<String, Double>> dstTf = new ArrayList<>();
        List<Set<String>> dstNum = new ArrayList<>();
        for (Csv.Row s : free) {
            Map<String, Double> tf = new HashMap<>();
            NameMatcher.addTokens(tf, s.str("key"), 1.0);
            NameMatcher.addTokens(tf, s.str("name"), 1.0);
            dstTf.add(tf);
            dstNum.add(NameMatcher.numbers(s.str("name").isEmpty() ? s.str("key") : s.str("name")));
        }
        NameMatcher matcher = new NameMatcher(srcTf, srcNum, dstTf, dstNum);

        record Proposal(int pair, NameMatcher.Scored best, NameGuess guess) { }
        List<Proposal> proposals = new ArrayList<>();
        for (int k = 0; k < unresolved.size(); k++) {
            int i = unresolved.get(k);
            List<NameMatcher.Scored> ranked = matcher.rank(k);
            if (ranked.isEmpty()) {
                if (matcher.hasNumberVeto(k)) {
                    set(pairs, i, null, new NameGuess("", 0, "", 0, "numéros différents"));
                }
                continue;
            }
            NameMatcher.Scored best = ranked.get(0);
            NameMatcher.Scored second = ranked.size() > 1 ? ranked.get(1) : null;
            String rejection = best.score() < nameThreshold ? "sous le seuil"
                    : second != null && best.score() - second.score() < nameMargin ? "ambigu"
                    : null;
            NameGuess guess = new NameGuess(free.get(best.index()).str("key"), best.score(),
                    second == null ? "" : free.get(second.index()).str("key"),
                    second == null ? 0 : second.score(), rejection);
            if (rejection == null) proposals.add(new Proposal(i, best, guess));
            else set(pairs, i, null, guess);
        }

        proposals.sort(Comparator.comparingDouble((Proposal p) -> p.best().score()).reversed());
        Map<String, String> claimedBy = new HashMap<>();
        for (Proposal p : proposals) {
            Csv.Row s = free.get(p.best().index());
            String winner = claimedBy.putIfAbsent(s.str("key"), pairs.get(p.pair()).gl().str("path"));
            if (winner == null) {
                set(pairs, p.pair(), s, p.guess());
            } else {
                NameGuess g = p.guess();
                set(pairs, p.pair(), null, new NameGuess(g.candidate(), g.score(), g.runnerUp(),
                        g.runnerUpScore(), "déjà proposé à " + winner));
            }
        }
    }

    private static void set(List<Pair> pairs, int i, Csv.Row sq, NameGuess guess) {
        Pair p = pairs.get(i);
        pairs.set(i, new Pair(p.gl(), sq, sq == null ? How.NONE : How.NAME_FUZZY, guess,
                p.bound(), p.linked()));
    }

    /**
     * Les chemins GitLab que désignent les liens saisis d'un projet Sonar.
     * `https://hote/groupe/projet.git`, `git@hote:groupe/projet`,
     * `https://hote/groupe/projet/-/tree/main` donnent tous `groupe/projet`.
     * L'hôte est ignoré : l'inventaire GitLab ne le porte pas.
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

    /**
     * Les clés Sonar s'écrivent {@code groupe:projet}, {@code groupe_projet} ou
     * {@code groupe-projet} selon qui les a créées ; le chemin GitLab s'écrit
     * {@code groupe/projet}. Tout séparateur devient le même, la casse tombe.
     */
    private static String normalise(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    // ----------------------------------------------------------------------
    // Le taux d'appariement, qui est un résultat et pas une note d'exécution
    // ----------------------------------------------------------------------

    private void matchRate(List<Pair> pairs, Csv.Table sonar, Csv.Table gitlab) {
        Map<How, Long> byHow = new LinkedHashMap<>();
        for (How h : How.values()) {
            byHow.put(h, pairs.stream().filter(p -> p.how() == h).count());
        }
        long joined = pairs.stream().filter(Pair::joined).count();

        title("1. Appariement");
        for (How h : How.values()) {
            long n = byHow.get(h);
            if (n == 0) continue;
            System.out.printf("  %-34s : %4d  (%s)%n", h.label, n,
                    switch (h.confidence) {
                        case EXACT -> "exact";
                        case DERIVED -> "dérivé";
                        case SUGGESTION -> c("suggestion — à confirmer", YELLOW);
                        case NONE -> "—";
                    });
        }
        System.out.printf("%n  Appariés pour l'analyse         : %d / %d projets GitLab (%s)%n",
                joined, pairs.size(), pct(joined, pairs.size()));
        System.out.println(c("    Les suggestions ne sont pas comptées ici : elles sortent dans", DIM));
        System.out.println(c("    le CSV pour qu'un humain tranche, pas dans un pourcentage.", DIM));

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

    /**
     * Ce que valent la liaison et les liens, jugés contre les autres méthodes.
     *
     * Les liens sont informatifs : saisis à la main, jamais vérifiés par
     * l'instance, ils ne font aucune jointure. Ce paragraphe dit s'ils
     * mériteraient d'en faire — combien existent, combien confirment une
     * jointure déjà faite, combien la contredisent, combien en créeraient une.
     */
    private void crossChecks(List<Pair> pairs, Csv.Table sonar) {
        if (!sonar.has("alm")) return;
        title("1b. Liaison et liens, contre les autres méthodes");

        long boundSonar = sonar.rows().stream().filter(s -> "gitlab".equals(s.str("alm"))).count();
        long unreadable = sonar.rows().stream().filter(s -> s.str("alm").startsWith("illisible")).count();
        System.out.printf("  Projets Sonar liés à GitLab             : %d / %d%n",
                boundSonar, sonar.rows().size());
        if (unreadable > 0) {
            System.out.println(c("    %d liaison(s) illisible(s) : droit manquant, pas absence"
                    .formatted(unreadable), YELLOW));
        }
        List<Pair> contradicted = pairs.stream()
                .filter(p -> p.how() != How.ALM_BINDING && p.sq() != null && !p.bound().isEmpty())
                .filter(p -> !p.bound().contains(p.sq().str("key")))
                .toList();
        long multi = pairs.stream().filter(p -> p.bound().size() > 1).count();
        System.out.printf("  Liaison contredisant la jointure retenue : %s%n",
                c(String.valueOf(contradicted.size()), contradicted.isEmpty() ? GREEN : YELLOW));
        contradicted.stream().limit(5).forEach(p -> System.out.printf("      %s — %s, liaison → %s%n",
                p.gl().str("path"), p.sq().str("key"), String.join(", ", p.bound())));
        if (multi > 0) {
            System.out.printf("  Dépôts liés à plusieurs projets Sonar    : %d (monodépôts ?)%n", multi);
        }

        long withLinks = sonar.rows().stream().filter(s -> !linkedPaths(s.str("liens")).isEmpty()).count();
        long pointing = pairs.stream().filter(p -> !p.linked().isEmpty()).count();
        long confirm = 0, contradict = 0, wouldJoin = 0, confirmGuess = 0;
        for (Pair p : pairs) {
            if (p.linked().isEmpty()) continue;
            if (p.sq() == null) wouldJoin++;
            else if (!p.linked().contains(p.sq().str("key"))) contradict++;
            else if (p.how() == How.NAME_FUZZY) confirmGuess++;
            else confirm++;
        }
        System.out.println();
        System.out.printf("  Projets Sonar avec un lien vers un dépôt : %d / %d%n",
                withLinks, sonar.rows().size());
        System.out.printf("  Projets GitLab désignés par un lien      : %d / %d%n", pointing, pairs.size());
        if (pointing > 0) {
            System.out.printf("    confirment une jointure sûre           : %d%n", confirm);
            System.out.printf("    confirment une suggestion de nom       : %d%n", confirmGuess);
            System.out.printf("    contredisent la jointure retenue       : %d%n", contradict);
            System.out.printf("    apparieraient un projet sans jointure  : %d%n", wouldJoin);
        }
        System.out.println(c("    Informatif : les liens ne font aucune jointure. Beaucoup de", DIM));
        System.out.println(c("    « apparieraient » et peu de « contredisent » plaident pour en", DIM));
        System.out.println(c("    faire une méthode ; l'inverse, pour les laisser là.", DIM));
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

    private void write(List<Pair> pairs) throws IOException {
        Path dir = out.toAbsolutePath().getParent();
        if (dir != null) Files.createDirectories(dir);

        List<String> header = new ArrayList<>(List.of("methode_jointure", "confiance",
                "candidat_nom", "score_nom", "second_nom", "rejet_nom",
                "liaison_sonar", "lien_sonar", "lien_concorde"));
        GL_COLUMNS.forEach(c -> header.add("gl_" + c));
        SQ_COLUMNS.forEach(c -> header.add("sq_" + c));

        try (CSVWriter w = Csv.writer(out, comma)) {
            w.writeNext(header.toArray(String[]::new));
            for (Pair p : pairs) {
                NameGuess g = p.name();
                List<String> row = new ArrayList<>(List.of(
                        p.how().label, p.how().confidence.name().toLowerCase(Locale.ROOT),
                        g == null ? "" : g.candidate(),
                        g == null || g.candidate().isEmpty() ? "" : score(g.score()),
                        g == null || g.runnerUp().isEmpty() ? ""
                                : "%s (%s)".formatted(g.runnerUp(), score(g.runnerUpScore())),
                        g == null || g.rejection() == null ? "" : g.rejection(),
                        String.join(" ", p.bound()),
                        String.join(" ", p.linked()),
                        p.linked().isEmpty() || p.sq() == null ? ""
                                : p.linked().contains(p.sq().str("key")) ? "oui" : "non"));
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

    static String score(double s) {
        return String.format(Locale.ROOT, "%.2f", s);
    }

    static String pct(long n, long total) {
        return total == 0 ? "—" : "%.0f %%".formatted(100.0 * n / total);
    }
}
