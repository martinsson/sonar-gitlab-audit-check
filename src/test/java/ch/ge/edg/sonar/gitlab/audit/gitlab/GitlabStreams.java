package ch.ge.edg.sonar.gitlab.audit.gitlab;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Records what a client signals, written as the commands print it: an interrupted paging on the
 * output, a capture that could not be written on the error. Builds clients that never sleep.
 */
final class GitlabStreams implements GitlabListener {

  private final StringBuilder out = new StringBuilder();
  private final StringBuilder err = new StringBuilder();

  /** A client that reports here. */
  Gitlab client(Gitlab.Connection c) throws IOException, GeneralSecurityException {
    return new Gitlab(c, this, pause -> {});
  }

  /** A client whose signals nobody reads. */
  static Gitlab quiet(Gitlab.Connection c) throws IOException, GeneralSecurityException {
    return new GitlabStreams().client(c);
  }

  @Override
  public void pagingInterrupted(String page, int status) {
    out.append("    Pagination interrompue page %s : HTTP %d%n".formatted(page, status));
  }

  @Override
  public void captureNotWritten(String reason) {
    err.append("  (capture non écrite : ").append(reason).append(")\n");
  }

  String out() {
    return out.toString();
  }

  String err() {
    return err.toString();
  }
}
