package ch.ge.edg.sonar.gitlab.audit.matching.report;

import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.DIM;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.YELLOW;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.matching.Fmt;
import ch.ge.edg.sonar.gitlab.audit.matching.Report;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Agreement;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Bucket;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Example;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.LinkStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.MethodStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.NameStats;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Summary;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.ThresholdPoint;
import ch.ge.edg.sonar.gitlab.audit.matching.Report.Vocabulary;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Le rapport d'évaluation sur la console. */
public final class ReportPrinter {

  private final Console out;

  public ReportPrinter(Console out) {
    this.out = out;
  }

  public void print(Report rep) {
    summary(rep);
    methods(rep.methods());
    if (!rep.agreements().isEmpty()) {
      title("Accords entre méthodes");
      for (Agreement a : rep.agreements()) {
        out.out()
            .printf(
                "  %-10s × %-10s : %4d en commun, %4d d'accord, %s en désaccord%n",
                a.a(),
                a.b(),
                a.both(),
                a.agree(),
                out.color(String.valueOf(a.disagree()), a.disagree() == 0 ? GREEN : YELLOW));
      }
      examples(rep.disagreements());
    }
    rep.links().forEach(this::links);
    NameStats n = rep.names();
    if (n != null) names(n);
  }

  private void summary(Report rep) {
    Summary s = rep.summary();
    title("Méthodes retenues");
    s.byMethod().forEach((label, n) -> out.out().printf("  %-34s : %5d%n", label, n));
    out.out()
        .printf(
            "%n  Jointures sûres : %d / %d (%s) · suggestions : %d · sans rien : %d%n",
            s.joined(), s.left(), Fmt.pct(s.joined(), s.left()), s.suggestions(), s.unmatched());
    out.out()
        .printf("  Lignes de droite jamais retenues : %d / %d%n", s.rightUnmatched(), s.right());
    for (String u : rep.unavailable()) out.println(out.color("  Non évaluée : " + u, YELLOW));
  }

  private void methods(List<MethodStats> methods) {
    title("Chaque méthode, seule");
    out.println(
        out.color("  propose = désigne quelque chose · ambiguë = plusieurs candidats", DIM));
    out.println(
        out.color("  retenue = a tranché · seule = aucune autre méthode sûre ne propose", DIM));
    out.out().printf("  %-14s %9s %9s %9s %9s%n", "", "propose", "ambiguë", "retenue", "seule");
    for (MethodStats m : methods) {
      out.out()
          .printf(
              "  %-14s %9d %9d %9d %9d%n",
              m.id(), m.proposes(), m.ambiguous(), m.wins(), m.alone());
    }
  }

  private void links(LinkStats l) {
    title("Liens « " + l.id() + " » (informatif, n'apparie rien)");
    out.out().printf("  Lignes de droite avec un lien de dépôt : %d%n", l.rightWithLinks());
    out.out().printf("  Lignes de gauche désignées             : %d%n", l.leftPointedAt());
    out.out().printf("    confirment une jointure sûre         : %d%n", l.confirmSure());
    out.out().printf("    confirment une suggestion            : %d%n", l.confirmSuggestion());
    out.out().printf("    contredisent la jointure             : %d%n", l.contradict());
    out.out().printf("    apparieraient une ligne seule        : %d%n", l.wouldJoin());
  }

  private void names(NameStats n) {
    title("Ressemblance de noms, jugée sur %d paires sûres".formatted(n.evaluatedOn()));
    out.println(
        out.color("  Meilleur candidat contre toute la droite, par tranche de score :", DIM));
    for (Bucket b : n.scoreOfBestCandidate()) {
      if (b.correct() + b.wrong() == 0) continue;
      out.out().printf("    %s  juste %4d  faux %4d%n", b.range(), b.correct(), b.wrong());
    }
    out.println(out.color("  Avec la marge actuelle, à chaque seuil :", DIM));
    for (ThresholdPoint p : n.thresholds()) {
      Double precision = p.precision();
      out.out()
          .printf(
              Locale.ROOT,
              "    %.1f  proposées %4d  justes %4d  précision %s  rappel %s%n",
              p.threshold(),
              p.proposed(),
              p.correct(),
              precision == null ? "   —" : Fmt.pct(precision),
              Fmt.pct(p.recall()));
    }
    if (!n.rejections().isEmpty()) {
      out.println("  Suggestions refusées : " + n.rejections());
    }
    subtitle("Fausses propositions sur des paires sûres", n.wrongOnSurePairs());
    subtitle("Paires sûres dont les noms divergent (score < 0.3)", n.divergentSurePairs());
    subtitle("Quasi-réussites", n.nearMisses());
    vocabulary(n.vocabulary());
  }

  private void vocabulary(Vocabulary v) {
    if (!v.unmatchedLeftTokens().isEmpty()) {
      out.println(out.color("\n  Mots fréquents des lignes seules (* = absent à droite) :", DIM));
      out.println(
          "    "
              + v.unmatchedLeftTokens().stream()
                  .map(t -> t.token() + (t.onRight() ? "" : "*") + " " + t.count())
                  .collect(Collectors.joining(", ")));
    }
    if (!v.unmatchedRightPrefixes().isEmpty()) {
      out.println(out.color("  Préfixes des clés de droite jamais retenues :", DIM));
      out.println(
          "    "
              + v.unmatchedRightPrefixes().stream()
                  .map(t -> t.token() + " " + t.count())
                  .collect(Collectors.joining(", ")));
    }
  }

  private void title(String text) {
    out.println();
    out.println(out.color(text, BOLD));
    out.println(out.color("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  private void subtitle(String t, List<Example> ex) {
    if (ex.isEmpty()) return;
    out.println(out.color("\n  " + t + " :", DIM));
    examples(ex);
  }

  private void examples(List<Example> ex) {
    ex.stream().limit(5).forEach(e -> out.println("      " + e.left() + " — " + e.detail()));
    if (ex.size() > 5)
      out.out().printf("      … %d de plus dans le rapport JSON.%n", ex.size() - 5);
  }
}
