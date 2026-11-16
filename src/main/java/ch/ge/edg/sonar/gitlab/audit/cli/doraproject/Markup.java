package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import static ch.ge.edg.sonar.gitlab.audit.cli.doraproject.Show.html;

/** Le HTML de dora.html en train de s'écrire : des lignes, et les tableaux de la page. */
final class Markup {

  private final StringBuilder out = new StringBuilder();

  void table(String... headers) {
    StringBuilder h = new StringBuilder("<div class=\"scroll\"><table><thead><tr>");
    for (String header : headers) h.append("<th>").append(html(header)).append("</th>");
    line(h.append("</tr></thead><tbody>").toString());
  }

  void row(String... cells) {
    line("<tr>" + String.join("", cells) + "</tr>");
  }

  void endTable() {
    line("</tbody></table></div>");
  }

  void empty(String text) {
    line("<div class=\"scroll empty\">" + html(text) + "</div>");
  }

  static String td(String text) {
    return "<td>" + html(text) + "</td>";
  }

  static String num(int n) {
    return "<td class=\"n\">" + n + "</td>";
  }

  void line(String text) {
    out.append(text).append('\n');
  }

  @Override
  public String toString() {
    return out.toString();
  }
}
