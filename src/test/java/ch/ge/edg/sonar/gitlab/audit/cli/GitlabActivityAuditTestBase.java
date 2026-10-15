package ch.ge.edg.sonar.gitlab.audit.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.console.ConsoleOut;
import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared set-up of the GitlabActivityAudit end-to-end tests: a fake GitLab, a temp directory, and a
 * fixed locale — the audit formats decimals with the default locale.
 */
abstract class GitlabActivityAuditTestBase {

  @TempDir Path tmp;

  GitlabAuditFake fake;
  private Locale savedLocale;

  @BeforeEach
  void setUpBase() {
    savedLocale = Locale.getDefault();
    Locale.setDefault(Locale.ROOT);
    fake = new GitlabAuditFake();
  }

  @AfterEach
  void tearDownBase() {
    fake.close();
    Locale.setDefault(savedLocale);
    ConsoleOut.colorMode("never");
  }

  /** Stdout with the port and temp directory replaced, stderr appended when not empty. */
  String out(Cli.Result r) {
    String s = "exit " + r.exitCode() + "\n" + fake.normalize(r.out(), tmp);
    return r.err().isEmpty() ? s : s + "\n--- stderr ---\n" + fake.normalize(r.err(), tmp);
  }

  /** A CSV as written, BOM shown as a mark and CRLF turned to LF. */
  static String file(Path p) {
    try {
      String s = Files.readString(p, StandardCharsets.UTF_8);
      return s.replace("﻿", "<BOM>").replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  Path write(String name, String content) {
    try {
      Path p = tmp.resolve(name);
      Files.createDirectories(p.getParent());
      return Files.writeString(p, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static void assertOk(Cli.Result r) {
    assertThat(r.exitCode()).as(r.err()).isZero();
  }
}
