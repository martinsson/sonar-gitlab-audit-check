package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.testing.Cli;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

/**
 * Shared set-up of the end-to-end tests: a fresh {@link SonarAuditCheckFake} per test, and the
 * default time zone pinned to UTC (the tool converts Sonar dates to the local zone; a DST change
 * inside a series would otherwise move slopes and day counts).
 */
abstract class SonarAuditCheckTestBase {

  @TempDir Path tmp;

  final SonarAuditCheckFake fake = new SonarAuditCheckFake();
  private TimeZone zone;

  @BeforeEach
  void utc() {
    zone = TimeZone.getDefault();
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

  @AfterEach
  void restore() {
    TimeZone.setDefault(zone);
    fake.close();
  }

  Path csv() {
    return tmp.resolve("inventaire.csv");
  }

  Path historyCsv() {
    return tmp.resolve("inventaire-historique.csv");
  }

  /** Starts the fake and runs with --csv inventaire.csv plus {@code extra}. */
  Cli.Result runCsv(String... extra) {
    fake.start();
    List<String> args = new ArrayList<>(List.of("--csv", csv().toString()));
    args.addAll(Arrays.asList(extra));
    return fake.run(tmp, args.toArray(String[]::new));
  }

  /** Output with its URL, dates and temp dir replaced by stable tokens. */
  String stable(String text) {
    return fake.normalize(text).replace(tmp.toString(), "<TMP>");
  }

  /** An Excel-style CSV: BOM, semicolons, CRLF; returned normalized, without BOM and with LF. */
  String excelCsv(Path file) throws IOException {
    String raw = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(raw).startsWith("﻿").endsWith("\r\n");
    assertThat(raw.replace("\r\n", "")).doesNotContain("\n");
    return fake.normalize(raw.substring(1).replace("\r\n", "\n"));
  }

  /** The inventory row of one project, as written (Excel form), without its quotes. */
  List<String> row(String key) throws IOException {
    for (String line : excelCsv(csv()).lines().toList()) {
      List<String> cells = cells(line);
      if (cells.getFirst().equals(key)) return cells;
    }
    throw new AssertionError("no row " + key);
  }

  /** Column values by header name, for the row of {@code key}. */
  String cell(String key, String column) throws IOException {
    List<String> header = cells(excelCsv(csv()).lines().findFirst().orElseThrow());
    return row(key).get(header.indexOf(column));
  }

  private static List<String> cells(String line) {
    List<String> out = new ArrayList<>();
    for (String c : line.substring(1, line.length() - 1).split("\";\"", -1)) {
      out.add(c.replace("\"\"", "\""));
    }
    return out;
  }
}
