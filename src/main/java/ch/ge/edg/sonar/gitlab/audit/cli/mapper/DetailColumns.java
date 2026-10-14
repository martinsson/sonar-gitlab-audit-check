package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Les colonnes de détail d'un appariement, partagées avec CrossAudit. */
public final class DetailColumns {

  private DetailColumns() {}

  public static List<String> header(Result r) {
    List<String> h =
        new ArrayList<>(List.of("candidat_nom", "score_nom", "second_nom", "rejet_nom"));
    for (KeyMethod m : r.available()) {
      h.add("m_" + m.id());
      if (m.kind() == Kind.LINK) h.add(m.id() + "_concorde");
    }
    if (r.matcher() != null) h.add("m_" + Spec.NAMES_ID + "_libre");
    return h;
  }

  public static List<String> cells(Result r, Match p) {
    List<String> row = new ArrayList<>(guessCells(p.guess()));
    String rk = p.rightKey(r.spec());
    for (KeyMethod m : r.available()) {
      List<String> f = p.found().getOrDefault(m.id(), List.of());
      row.add(String.join(" ", f));
      if (m.kind() == Kind.LINK) row.add(concord(f, p, rk));
    }
    if (r.matcher() != null) row.add(freeCell(p.free()));
    return row;
  }

  private static List<String> guessCells(@Nullable NameGuess g) {
    if (g == null) return List.of("", "", "", "");
    String rejection = g.rejection();
    return List.of(
        g.candidate(),
        g.candidate().isEmpty() ? "" : Fmt.score(g.score()),
        g.runnerUp().isEmpty()
            ? ""
            : "%s (%s)".formatted(g.runnerUp(), Fmt.score(g.runnerUpScore())),
        rejection == null ? "" : rejection);
  }

  /**
   * Les liens confirment-ils la paire retenue ? Vide s'ils ne désignent rien ou rien n'est retenu.
   */
  private static String concord(List<String> found, Match p, String rightKey) {
    if (found.isEmpty() || p.right() == null) return "";
    return found.contains(rightKey) ? "oui" : "non";
  }

  private static String freeCell(@Nullable NameGuess f) {
    if (f == null || f.candidate().isEmpty()) return "";
    String rejection = f.rejection();
    return "%s (%s%s)"
        .formatted(f.candidate(), Fmt.score(f.score()), rejection == null ? "" : ", " + rejection);
  }
}
