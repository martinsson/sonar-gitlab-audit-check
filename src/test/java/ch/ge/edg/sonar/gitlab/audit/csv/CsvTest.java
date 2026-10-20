package ch.ge.edg.sonar.gitlab.audit.csv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opencsv.CSVWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvTest {

  @TempDir Path dir;

  private Path file(String content) throws IOException {
    Path f = dir.resolve("in.csv");
    Files.writeString(f, content, StandardCharsets.UTF_8);
    return f;
  }

  private static Csv.Row row(Map<String, Integer> index, @Nullable String... cells) {
    return new Csv.Row(index, cells);
  }

  // ---------------------------------------------------------------- writer

  @Test
  void excelWriterPutsABomSemicolonsQuotesAndCrlf() throws IOException {
    Path f = dir.resolve("out.csv");
    try (CSVWriter w = Csv.writer(f, false)) {
      w.writeNext(new String[] {"key", "nom"});
      w.writeNext(new String[] {"alpha", "é;\"x\""});
    }
    byte[] bytes = Files.readAllBytes(f);
    assertThat(bytes[0]).isEqualTo((byte) 0xEF);
    assertThat(bytes[1]).isEqualTo((byte) 0xBB);
    assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    assertThat(new String(bytes, StandardCharsets.UTF_8))
        .isEqualTo("﻿\"key\";\"nom\"\r\n\"alpha\";\"é;\"\"x\"\"\"\r\n");
  }

  @Test
  void commaWriterHasNoBomCommasAndLf() throws IOException {
    Path f = dir.resolve("out.csv");
    try (CSVWriter w = Csv.writer(f, true)) {
      w.writeNext(new String[] {"key", "nom"});
      w.writeNext(new String[] {"alpha", "a,b"});
    }
    assertThat(Files.readString(f, StandardCharsets.UTF_8))
        .isEqualTo("\"key\",\"nom\"\n\"alpha\",\"a,b\"\n");
  }

  @Test
  void whatTheWriterWritesTheReaderReadsBackInBothFormats() throws IOException {
    for (boolean comma : new boolean[] {false, true}) {
      Path f = dir.resolve("rt" + comma + ".csv");
      try (CSVWriter w = Csv.writer(f, comma)) {
        w.writeNext(new String[] {"key", "ncloc"});
        w.writeNext(new String[] {"alpha", "1,5"});
      }
      Csv.Table t = Csv.read(f);
      assertThat(t.index()).containsOnlyKeys("key", "ncloc");
      assertThat(t.rows()).hasSize(1);
      assertThat(t.rows().get(0).str("key")).isEqualTo("alpha");
      assertThat(t.rows().get(0).num("ncloc")).isEqualTo(1.5);
    }
  }

  // ---------------------------------------------------------------- read

  @Test
  void anEmptyFileIsAnEmptyTable() throws IOException {
    Csv.Table t = Csv.read(file(""));
    assertThat(t.index()).isEmpty();
    assertThat(t.rows()).isEmpty();
  }

  @Test
  void aFileWithOnlyANewlineHasOneEmptyColumnAndNoRow() throws IOException {
    Csv.Table t = Csv.read(file("\n"));
    assertThat(t.index()).isEqualTo(Map.of("", 0));
    assertThat(t.rows()).isEmpty();
  }

  @Test
  void semicolonWinsWhenTheHeaderHasMoreSemicolonsThanCommas() throws IOException {
    Csv.Table t = Csv.read(file("﻿key ; a,b;c\nalpha;x,y;z\n"));
    assertThat(t.index()).isEqualTo(Map.of("key", 0, "a,b", 1, "c", 2));
    assertThat(t.rows().get(0).str("a,b")).isEqualTo("x,y");
  }

  @Test
  void commaWinsOnATieAndTheBomIsStripped() throws IOException {
    Csv.Table t = Csv.read(file("﻿key,a;b\nalpha,x;y\n"));
    assertThat(t.index()).isEqualTo(Map.of("key", 0, "a;b", 1));
    assertThat(t.has("key")).isTrue();
    assertThat(t.has("﻿key")).isFalse();
  }

  @Test
  void headerWithoutBomIsKeptAsIs() throws IOException {
    Csv.Table t = Csv.read(file("key,ncloc\nalpha,3\n"));
    assertThat(t.index()).isEqualTo(Map.of("key", 0, "ncloc", 1));
  }

  @Test
  void blankLinesAreSkippedButRowsOfEmptyCellsAreKept() throws IOException {
    Csv.Table t = Csv.read(file("key,ncloc\n\nalpha,1\n   \n,\nbeta,2\n"));
    assertThat(t.rows()).extracting(r -> r.str("key")).containsExactly("alpha", "", "beta");
  }

  @Test
  void quotedMultilineCellsAreReadWhole() throws IOException {
    Csv.Table t = Csv.read(file("key,txt\nalpha,\"a\nb\"\n"));
    assertThat(t.rows().get(0).str("txt")).isEqualTo("a\nb");
  }

  @Test
  void anUnterminatedQuoteFailsWithAnIoException() throws IOException {
    Path f = file("key,txt\nalpha,\"never closed\n");
    assertThatThrownBy(() -> Csv.read(f)).isInstanceOf(IOException.class);
  }

  @Test
  void aMissingFileFails() {
    assertThatThrownBy(() -> Csv.read(dir.resolve("absent.csv"))).isInstanceOf(IOException.class);
  }

  // ---------------------------------------------------------------- Table

  @Test
  void requireAcceptsPresentColumnsAndNamesTheFirstMissingOne() throws IOException {
    Path f = file("key,ncloc\n");
    Csv.Table t = Csv.read(f);
    t.require(f, "SonarAuditCheck");
    t.require(f, "SonarAuditCheck", "key", "ncloc");
    assertThatThrownBy(() -> t.require(f, "SonarAuditCheck", "key", "coverage", "zzz"))
        .isInstanceOf(IOException.class)
        .hasMessage(
            "colonne « coverage » absente de "
                + f
                + " — ce fichier vient-il bien de SonarAuditCheck ?");
  }

  // ---------------------------------------------------------------- Row

  @Test
  void strIsTrimmedAndEmptyWhenMissingOutOfRangeOrNull() {
    Map<String, Integer> idx = Map.of("a", 0, "b", 1, "c", 2);
    Csv.Row r = row(idx, "  x  ", null);
    assertThat(r.str("a")).isEqualTo("x");
    assertThat(r.str("b")).isEmpty();
    assertThat(r.str("c")).isEmpty();
    assertThat(r.str("zzz")).isEmpty();
  }

  @Test
  void numReadsDecimalsWithAPointOrALoneComma() {
    Map<String, Integer> idx = Map.of("v", 0);
    assertThat(row(idx, "12.5").num("v")).isEqualTo(12.5);
    assertThat(row(idx, " 12,5 ").num("v")).isEqualTo(12.5);
    assertThat(row(idx, "3").num("v")).isEqualTo(3.0);
    assertThat(row(idx, "-0.25").num("v")).isEqualTo(-0.25);
    assertThat(row(idx, "1e3").num("v")).isEqualTo(1000.0);
  }

  @Test
  void numIsNullForEmptyOrUnreadableNeverZero() {
    Map<String, Integer> idx = Map.of("v", 0);
    assertThat(row(idx, "").num("v")).isNull();
    assertThat(row(idx, "   ").num("v")).isNull();
    assertThat(row(idx, "abc").num("v")).isNull();
    assertThat(row(idx, "1,234.5").num("v")).isNull();
    assertThat(row(idx, "1,2,3").num("v")).isNull();
    assertThat(row(idx).num("v")).isNull();
    assertThat(row(idx, "x").num("absent")).isNull();
  }

  @Test
  void flagIsTrueOnlyForTrueInAnyCase() {
    Map<String, Integer> idx = Map.of("f", 0);
    assertThat(row(idx, "true").flag("f")).isTrue();
    assertThat(row(idx, " TRUE ").flag("f")).isTrue();
    assertThat(row(idx, "True").flag("f")).isTrue();
    assertThat(row(idx, "yes").flag("f")).isFalse();
    assertThat(row(idx, "1").flag("f")).isFalse();
    assertThat(row(idx, "").flag("f")).isFalse();
    assertThat(row(idx, "true").flag("absent")).isFalse();
  }

  // ---------------------------------------------------------------- hint

  @Test
  void openingHintNamesTheFileForExcelAndNotForAMachine() {
    Path p = dir.resolve("sub").resolve("croisement.csv");
    assertThat(Csv.openingHint(p, true)).isEqualTo("  Format machine (virgule, sans BOM).");
    assertThat(Csv.openingHint(p, false))
        .isEqualTo(
            "  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start croisement.csv`."
                + " Pour un autre outil : --comma.");
  }
}
