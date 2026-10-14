package ch.ge.edg.sonar.gitlab.audit.testing;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Splits the query string of a request target into decoded parameters. */
final class Query {

  private Query() {}

  static Map<String, String> parse(String target) {
    Map<String, String> params = new LinkedHashMap<>();
    int q = target.indexOf('?');
    if (q < 0) return params;
    for (String pair : target.substring(q + 1).split("&")) {
      if (pair.isEmpty()) continue;
      int eq = pair.indexOf('=');
      String k = eq < 0 ? pair : pair.substring(0, eq);
      String v = eq < 0 ? "" : pair.substring(eq + 1);
      params.put(decode(k), decode(v));
    }
    return params;
  }

  private static String decode(String s) {
    return URLDecoder.decode(s, StandardCharsets.UTF_8);
  }
}
