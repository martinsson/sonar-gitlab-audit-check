package ch.ge.edg.sonar.gitlab.audit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * A tiny HTTP/1.1 server on a loopback socket, standing in for SonarQube or GitLab in tests.
 *
 * <p>Routes match on the path (without the query string); the first route whose path equals the
 * request path wins, then the first whose prefix matches. Unrouted requests get a 404. Every
 * request is recorded so tests can assert on what was asked. One connection, one request: the
 * response always says {@code Connection: close}. Once {@link #close()} returns, nothing listens on
 * the port any more, so a test can use the closed server's URL as an unreachable instance.
 */
public final class FakeHttpServer implements AutoCloseable {

  /** A request as the server received it. */
  public record Request(String method, String target, Map<String, String> headers) {

    /** The path, without the query string. */
    public String path() {
      int q = target.indexOf('?');
      return q < 0 ? target : target.substring(0, q);
    }

    /** One query parameter, URL-decoded, or null. */
    public @Nullable String param(String name) {
      return Query.parse(target).get(name);
    }

    /** A header, by case-insensitive name, or null. */
    public @Nullable String header(String name) {
      return headers.get(name.toLowerCase(Locale.ROOT));
    }
  }

  /** What the server answers. */
  public record Response(int status, String body, Map<String, String> headers) {

    public static Response json(String body) {
      return new Response(200, body, Map.of("Content-Type", "application/json"));
    }

    public static Response status(int status, String body) {
      return new Response(status, body, Map.of("Content-Type", "application/json"));
    }

    public Response withHeader(String name, String value) {
      Map<String, String> h = new LinkedHashMap<>(headers);
      h.put(name, value);
      return new Response(status, body, h);
    }
  }

  private record Route(String path, boolean prefix, Function<Request, Response> handler) {}

  private final ServerSocket socket;
  private final List<Route> routes = new CopyOnWriteArrayList<>();
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final Thread acceptor;

  public FakeHttpServer() {
    try {
      socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    acceptor = new Thread(this::acceptLoop, "fake-http");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  /** Base URL, without trailing slash, e.g. {@code http://127.0.0.1:54321}. */
  public String url() {
    return "http://127.0.0.1:" + socket.getLocalPort();
  }

  /** Exact path match. */
  public FakeHttpServer on(String path, Function<Request, Response> handler) {
    routes.add(new Route(path, false, handler));
    return this;
  }

  /** Exact path match, constant JSON body. */
  public FakeHttpServer json(String path, String body) {
    return on(path, r -> Response.json(body));
  }

  /** Prefix path match. */
  public FakeHttpServer onPrefix(String prefix, Function<Request, Response> handler) {
    routes.add(new Route(prefix, true, handler));
    return this;
  }

  /** Every request received so far, in order. */
  public List<Request> requests() {
    return List.copyOf(requests);
  }

  /** The requests whose path equals {@code path}. */
  public List<Request> requests(String path) {
    return requests.stream().filter(r -> r.path().equals(path)).toList();
  }

  Response dispatch(Request req) {
    for (Route r : routes) {
      if (!r.prefix() && r.path().equals(req.path())) return r.handler().apply(req);
    }
    for (Route r : routes) {
      if (r.prefix() && req.path().startsWith(r.path())) return r.handler().apply(req);
    }
    return Response.status(404, "{\"errors\":[{\"msg\":\"no route " + req.path() + "\"}]}");
  }

  private void acceptLoop() {
    while (!socket.isClosed()) {
      try {
        Socket s = socket.accept();
        Thread t = new Thread(() -> serve(s), "fake-http-conn");
        t.setDaemon(true);
        t.start();
      } catch (IOException e) {
        return;
      }
    }
  }

  private void serve(Socket s) {
    try (s) {
      Request req = HttpWire.read(s.getInputStream());
      if (req == null) return;
      requests.add(req);
      HttpWire.write(s.getOutputStream(), dispatch(req));
    } catch (IOException ignored) {
      // The client went away; nothing to answer.
    }
  }

  /**
   * Stops listening, and waits for the accepting thread to end. Closing the socket alone is not
   * enough: on Linux the kernel keeps the listening socket until that thread wakes up, and a
   * connection made in between is accepted and answered. On a loaded machine that takes long enough
   * for the next request of the test.
   */
  @Override
  public void close() {
    try {
      socket.close();
      if (!acceptor.join(Duration.ofSeconds(5))) {
        throw new IllegalStateException("the fake server still accepts after 5 s");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
