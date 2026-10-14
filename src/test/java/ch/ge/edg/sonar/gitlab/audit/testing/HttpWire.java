package ch.ge.edg.sonar.gitlab.audit.testing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads one HTTP/1.1 request head and writes one response, for {@link FakeHttpServer}. */
final class HttpWire {

  private HttpWire() {}

  static FakeHttpServer.Request read(InputStream raw) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
    String line = in.readLine();
    if (line == null || line.isBlank()) return null;
    String[] parts = line.split(" ", 3);
    Map<String, String> headers = new LinkedHashMap<>();
    for (String h = in.readLine(); h != null && !h.isEmpty(); h = in.readLine()) {
      int c = h.indexOf(':');
      if (c > 0)
        headers.put(h.substring(0, c).trim().toLowerCase(Locale.ROOT), h.substring(c + 1).trim());
    }
    return new FakeHttpServer.Request(parts[0], parts.length > 1 ? parts[1] : "/", headers);
  }

  static void write(OutputStream out, FakeHttpServer.Response r) throws IOException {
    byte[] body = r.body().getBytes(StandardCharsets.UTF_8);
    List<String> lines = new ArrayList<>();
    lines.add("HTTP/1.1 " + r.status() + " X");
    r.headers().forEach((k, v) -> lines.add(k + ": " + v));
    lines.add("Content-Length: " + body.length);
    lines.add("Connection: close");
    String head = String.join("\r\n", lines) + "\r\n\r\n";
    out.write(head.getBytes(StandardCharsets.UTF_8));
    out.write(body);
    out.flush();
  }
}
