package ch.ge.edg.sonar.gitlab.audit.matching.report;

import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.BOLD;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.DIM;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.GREEN;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.YELLOW;
import static ch.ge.edg.sonar.gitlab.audit.matching.report.Colors.c;

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
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Le rapport d'évaluation sur la console. */
public final class ReportPrinter {

  private final PrintStream out;

  public ReportPrinter(PrintStream out) {
    this.out = out;
  }

  public void print(Report rep) {
    summary(rep);
    methods(rep.methods());
    if (!rep.agreements().isEmpty()) {
      title("Accords entre méthodes");
      for (Agreement a : rep.agreements()) {
        out.printf(
            "  %-10s × %-10s : %4d en commun, %4d d'accord, %s en désaccord%n",
            a.a(),
            a.b(),
            a.both(),
            a.agree(),
            c(String.valueOf(a.disagree()), a.disagree() == 0 ? GREEN : YELLOW));
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
    s.byMethod().forEach((label, n) -> out.printf("  %-34s : %5d%n", label, n));
    out.printf(
        "%n  Jointures sûres : %d / %d (%s) · suggestions : %d · sans rien : %d%n",
        s.joined(), s.left(), Fmt.pct(s.joined(), s.left()), s.suggestions(), s.unmatched());
    out.printf("  Lignes de droite jamais retenues : %d / %d%n", s.rightUnmatched(), s.right());
    for (String u : rep.unavailable()) out.println(c("  Non évaluée : " + u, YELLOW));
  }

  private void methods(List<MethodStats> methods) {
    title("Chaque méthode, seule");
    out.println(c("  propose = désigne quelque chose · ambiguë = plusieurs candidats", DIM));
    out.println(c("  retenue = a tranché · seule = aucune autre méthode sûre ne propose", DIM));
    out.printf("  %-14s %9s %9s %9s %9s%n", "", "propose", "ambiguë", "retenue", "seule");
    for (MethodStats m : methods) {
      out.printf(
          "  %-14s %9d %9d %9d %9d%n", m.id(), m.proposes(), m.ambiguous(), m.wins(), m.alone());
    }
  }

  private void links(LinkStats l) {
    title("Liens « " + l.id() + " » (informatif, n'apparie rien)");
    out.printf("  Lignes de droite avec un lien de dépôt : %d%n", l.rightWithLinks());
    out.printf("  Lignes de gauche désignées             : %d%n", l.leftPointedAt());
    out.printf("    confirment une jointure sûre         : %d%n", l.confirmSure());
    out.printf("    confirment une suggestion            : %d%n", l.confirmSuggestion());
    out.printf("    contredisent la jointure             : %d%n", l.contradict());
    out.printf("    apparieraient une ligne seule        : %d%n", l.wouldJoin());
  }

  private void names(NameStats n) {
    title("Ressemblance de noms, jugée sur %d paires sûres".formatted(n.evaluatedOn()));
    out.println(c("  Meilleur candidat contre toute la droite, par tranche de score :", DIM));
    for (Bucket b : n.scoreOfBestCandidate()) {
      if (b.correct() + b.wrong() == 0) continue;
      out.printf("    %s  juste %4d  faux %4d%n", b.range(), b.correct(), b.wrong());
    }
    out.println(c("  Avec la marge actuelle, à chaque seuil :", DIM));
    for (ThresholdPoint p : n.thresholds()) {
      Double precision = p.precision();
      out.printf(
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
      out.println(c("\n  Mots fréquents des lignes seules (* = absent à droite) :", DIM));
      out.println(
          "    "
              + v.unmatchedLeftTokens().stream()
                  .map(t -> t.token() + (t.onRight() ? "" : "*") + " " + t.count())
                  .collect(Collectors.joining(", ")));
    }
    if (!v.unmatchedRightPrefixes().isEmpty()) {
      out.println(c("  Préfixes des clés de droite jamais retenues :", DIM));
      out.println(
          "    "
              + v.unmatchedRightPrefixes().stream()
                  .map(t -> t.token() + " " + t.count())
                  .collect(Collectors.joining(", ")));
    }
  }

  private void title(String text) {
    out.println();
    out.println(c(text, BOLD));
    out.println(c("-".repeat(Math.min(text.length(), 72)), DIM));
  }

  private void subtitle(String t, List<Example> ex) {
    if (ex.isEmpty()) return;
    out.println(c("\n  " + t + " :", DIM));
    examples(ex);
  }

  private void examples(List<Example> ex) {
    ex.stream().limit(5).forEach(e -> out.println("      " + e.left() + " — " + e.detail()));
    if (ex.size() > 5) out.printf("      … %d de plus dans le rapport JSON.%n", ex.size() - 5);
  }
}
