package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Ce que chaque déploiement a livré, en fonctionnalités, quelle que soit la source des déploiements
 * ({@link Deploy}). Les commits de fusion (deux parents ou plus, fusions de main dans une branche
 * comprises) et les commits {@code ci(release):} sont écartés. Les autres se regroupent,
 * déploiement par déploiement :
 *
 * <ol>
 *   <li>par la clé Jira du titre, {@code feat: [ABC-123] …} ({@link CommitTitle}) ;
 *   <li>sinon, par la clé de la branche dont la fusion dans la branche par défaut a apporté le
 *       commit : il est atteignable depuis le second parent de la fusion et pas depuis le premier ;
 *       la clé vient du nom de la branche ({@code feature/ABC-123_…}, {@code bugfix/ABC-123_…}) ;
 *   <li>sinon, un {@code feat:} ou un {@code fix:} est une fonctionnalité à lui seul, « sans story
 *       » ; un commit d'un autre type (chore, test, refactor, docs, style, build, ci, perf…) ne
 *       compte pas : la page en donne seulement le nombre par type.
 * </ol>
 *
 * <p>Le délai d'une fonctionnalité livrée par un déploiement court de la date d'auteur de son
 * dernier commit livré par ce déploiement à la date du déploiement ; la variante par date de commit
 * est calculée à côté. Une fonctionnalité livrée en plusieurs fois compte une fois par déploiement.
 */
final class DeliveredFeatures {

  private DeliveredFeatures() {}

  /** D'où vient l'identité d'une fonctionnalité, de la plus sûre à la moins sûre. */
  enum Origin {
    KEY("clé du message"),
    BRANCH("clé de la branche de la fusion"),
    ALONE("commit seul");

    private final String label;

    Origin(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  /**
   * Une fonctionnalité livrée par un déploiement : ses commits livrés par celui-ci.
   *
   * @param key la clé Jira, ou le SHA court d'un commit seul
   * @param origin la plus sûre des origines de ses commits
   * @param byBranch ceux de ses commits rattachés par la branche de leur fusion
   * @param last son commit à la date d'auteur la plus récente
   * @param authorLead de la date d'auteur de {@code last} au déploiement
   * @param committerLead de la date de commit la plus récente de ses commits au déploiement
   */
  record Feature(
      String key,
      Origin origin,
      List<Delivery.Commit> commits,
      int byBranch,
      Delivery.Commit last,
      @Nullable Duration authorLead,
      @Nullable Duration committerLead) {

    Feature {
      commits = List.copyOf(commits);
    }

    /** Rattachée à un ticket Jira. */
    boolean story() {
      return origin != Origin.ALONE;
    }

    /**
     * D'où vient la fonctionnalité, en clair : « clé du message, 1 par la branche de la fusion ».
     */
    String source() {
      if (origin != Origin.KEY || byBranch == 0) return origin.label();
      return origin.label() + ", " + byBranch + " par la branche de la fusion";
    }
  }

  /**
   * Un déploiement et ce qu'il a livré.
   *
   * @param deployment le déploiement, tel que les mesures communes le lisent
   * @param excluded type → nombre de commits sans story, qui ne comptent pas
   * @param merges ses commits de fusion, écartés
   * @param ciReleases ses commits {@code ci(release):}, écartés
   */
  record Shipment(
      Snapshot.Deployment deployment,
      Delivery delivery,
      List<Feature> features,
      Map<String, Integer> excluded,
      int merges,
      int ciReleases) {

    Shipment {
      features = List.copyOf(features);
      excluded = Map.copyOf(excluded);
    }

    /** Les SHA des commits comptés, fonctionnalité par fonctionnalité. */
    List<String> delivered() {
      return features.stream()
          .flatMap(f -> f.commits().stream())
          .map(Delivery.Commit::sha)
          .toList();
    }
  }

  /** Chaque livraison de l'instantané, de la plus ancienne à la plus récente. */
  static List<Shipment> of(Snapshot s) {
    List<Shipment> out = new ArrayList<>();
    List<Delivery> deliveries = s.deliveries();
    for (int i = 0; i < deliveries.size(); i++) {
      Delivery d = deliveries.get(i);
      out.add(shipment(d.deployment(i + 1, s.project().webUrl()), d, s.project().defaultBranch()));
    }
    return List.copyOf(out);
  }

  /** Les commits écartés faute de story, par type, sur toutes les livraisons. */
  static Map<String, Integer> excluded(List<Shipment> shipments) {
    Map<String, Integer> out = new TreeMap<>();
    for (Shipment t : shipments) {
      t.excluded().forEach((type, n) -> out.merge(type, n, Integer::sum));
    }
    return out;
  }

  /** Les délais par date d'auteur, un par fonctionnalité et par déploiement. */
  static List<Duration> leads(List<Shipment> shipments) {
    List<Duration> out = new ArrayList<>();
    for (Shipment t : shipments) {
      for (Feature f : t.features()) {
        Duration lead = f.authorLead();
        if (lead != null) out.add(lead);
      }
    }
    return out;
  }

  /** Les commits d'une fonctionnalité dans une livraison. */
  private static final class Group {
    private final String key;
    private Origin origin = Origin.ALONE;
    private final List<Delivery.Commit> commits = new ArrayList<>();
    private int byBranch;

    Group(String key) {
      this.key = key;
    }

    void add(Delivery.Commit c, Origin from) {
      commits.add(c);
      if (from == Origin.BRANCH) byBranch++;
      if (from.compareTo(origin) < 0) origin = from;
    }
  }

  private record Placement(String key, Origin origin) {}

  static Shipment shipment(Snapshot.Deployment deployment, Delivery d, String defaultBranch) {
    Map<String, String> branchKeys = branchKeys(d.commits(), defaultBranch);
    Map<String, Group> groups = new LinkedHashMap<>();
    Map<String, Integer> excluded = new TreeMap<>();
    int merges = 0;
    int ci = 0;
    for (Delivery.Commit c : d.commits()) {
      if (c.merge()) {
        merges++;
      } else if (CommitTitle.ciRelease(c.title())) {
        ci++;
      } else {
        CommitTitle title = CommitTitle.of(c.title());
        Placement p = place(c, title, branchKeys.get(c.sha()));
        if (p == null) excluded.merge(title.type(), 1, Integer::sum);
        else groups.computeIfAbsent(p.key(), Group::new).add(c, p.origin());
      }
    }
    List<Feature> features = new ArrayList<>();
    for (Group g : groups.values()) features.add(feature(g, deployment.at()));
    return new Shipment(deployment, d, features, excluded, merges, ci);
  }

  /** La clé du titre ; sinon celle de la branche fusionnée ; sinon un feat: ou un fix: seul. */
  private static @Nullable Placement place(
      Delivery.Commit c, CommitTitle title, @Nullable String branchKey) {
    String key = title.key();
    if (key != null) return new Placement(key, Origin.KEY);
    if (branchKey != null) return new Placement(branchKey, Origin.BRANCH);
    return title.work() ? new Placement(c.shortSha(), Origin.ALONE) : null;
  }

  private static Feature feature(Group g, Instant deployed) {
    Delivery.Commit last = g.commits.getLast();
    Instant authored = null;
    Instant committed = null;
    for (Delivery.Commit c : g.commits) {
      Instant a = c.authoredAt();
      if (a != null && (authored == null || a.isAfter(authored))) {
        authored = a;
        last = c;
      }
      Instant at = c.committedAt();
      if (at != null && (committed == null || at.isAfter(committed))) committed = at;
    }
    return new Feature(
        g.key,
        g.origin,
        g.commits,
        g.byBranch,
        last,
        Metrics.between(authored, deployed),
        Metrics.between(committed, deployed));
  }

  /**
   * Pour chaque commit qu'une fusion dans la branche par défaut a apporté, la clé de la branche
   * fusionnée : le commit est atteignable depuis le second parent de la fusion et pas depuis le
   * premier. Le parcours reste dans les commits livrés : un commit hors d'eux est atteignable
   * depuis le déploiement précédent, et ses ancêtres aussi. Une fusion plus ancienne l'emporte ;
   * une branche sans clé n'en donne pas.
   */
  private static Map<String, String> branchKeys(
      List<Delivery.Commit> commits, String defaultBranch) {
    Map<String, Delivery.Commit> bySha = new HashMap<>();
    for (Delivery.Commit c : commits) bySha.put(c.sha(), c);
    List<Delivery.Commit> merges =
        new ArrayList<>(
            commits.stream()
                .filter(Delivery.Commit::merge)
                .filter(c -> CommitTitle.mergedBranch(c.title(), defaultBranch) != null)
                .toList());
    merges.sort(
        Comparator.comparing(
            Delivery.Commit::committedAt, Comparator.nullsLast(Comparator.naturalOrder())));
    Map<String, String> out = new HashMap<>();
    for (Delivery.Commit m : merges) {
      String branch = CommitTitle.mergedBranch(m.title(), defaultBranch);
      String key = branch == null ? null : CommitTitle.branchKey(branch);
      if (key == null) continue;
      Set<String> before = ancestors(m.parents().getFirst(), bySha);
      for (String sha : ancestors(m.parents().get(1), bySha)) {
        if (!before.contains(sha)) out.putIfAbsent(sha, key);
      }
    }
    return out;
  }

  /** {@code sha} et ses ancêtres, parmi les commits livrés. */
  private static Set<String> ancestors(String sha, Map<String, Delivery.Commit> bySha) {
    Set<String> seen = new HashSet<>();
    List<String> todo = new ArrayList<>(List.of(sha));
    while (!todo.isEmpty()) {
      String next = todo.removeLast();
      Delivery.Commit c = bySha.get(next);
      if (c != null && seen.add(next)) todo.addAll(c.parents());
    }
    return seen;
  }
}
