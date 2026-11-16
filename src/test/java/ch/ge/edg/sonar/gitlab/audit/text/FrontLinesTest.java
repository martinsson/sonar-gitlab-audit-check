package ch.ge.edg.sonar.gitlab.audit.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.ge.edg.sonar.gitlab.audit.text.FrontLines.Count;
import ch.ge.edg.sonar.gitlab.audit.text.FrontLines.Skipped;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class FrontLinesTest {

  private static final String ROOT = "app-main-0123abcd/";

  @Test
  void countsNonBlankLinesOfFrontFilesAndDropsTheWrappingDirectory() throws IOException {
    Count c =
        count(
            new Tar()
                .globalHeader()
                .dir(ROOT)
                .dir(ROOT + "front/")
                .file(ROOT + "front/App.vue", "<template>\n\n  <div/>\n</template>\n")
                .file(ROOT + "front/main.ts", "import x from 'x'\r\n   \r\nx()")
                .file(ROOT + "front/package.json", "{\n\"a\": 1\n}\n")
                .file(ROOT + "src/main/java/A.java", "class A {}\n"));

    assertThat(c.files()).isEqualTo(2);
    assertThat(c.lines()).isEqualTo(5);
    assertThat(c.byExtension()).containsExactlyInAnyOrderEntriesOf(Map.of("vue", 3L, "ts", 2L));
    assertThat(c.byExtensionText()).isEqualTo("vue 3, ts 2");
    assertThat(c.skipped()).isEmpty();
    assertThat(c.truncated()).isFalse();
  }

  @Test
  void dependenciesBuildOutputAndGeneratedFilesAreLeftOut() throws IOException {
    Count c =
        count(
            new Tar()
                .file(ROOT + "node_modules/vue/index.js", "a\nb\n")
                .file(ROOT + "front/dist/app.js", "a\n")
                .file(ROOT + "front/coverage/lcov-report/index.html", "a\n")
                .file(ROOT + "public/jquery.min.js", "a\n")
                .file(ROOT + "src/logo.svg.js", "a\0b\n")
                .file(ROOT + "src/big.js", "x\n".repeat(FrontLines.MAX_FILE_BYTES))
                .file(ROOT + "src/packed.js", "x".repeat(FrontLines.MAX_AVERAGE_LINE * 3) + "\n")
                .file(ROOT + "src/dist.js", "kept\n"));

    assertThat(c.files()).isEqualTo(1);
    assertThat(c.lines()).isEqualTo(1);
    assertThat(c.skipped())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                Skipped.MINIFIED,
                1,
                Skipped.BINARY,
                1,
                Skipped.TOO_LARGE,
                1,
                Skipped.GENERATED,
                1));
    assertThat(c.skippedText())
        .isEqualTo("minifiés 1, > 1 Mo 1, binaires 1, lignes trop longues 1");
  }

  @Test
  void longPathsFromPaxHeadersAndUstarPrefixesAreRead() throws IOException {
    String deep = ROOT + "a/".repeat(60) + "node_modules/x.js";
    String prefixed = ROOT + "b/".repeat(60) + "Widget.tsx";
    Count c =
        count(
            new Tar()
                .paxPath(deep)
                .file("truncated-name-ignored.js", "a\n")
                .ustarPrefixed(prefixed, "a\nb\n"));

    assertThat(c.files()).isEqualTo(1);
    assertThat(c.byExtension()).containsExactlyEntriesOf(Map.of("tsx", 2L));
  }

  @Test
  void aSpentBudgetStopsTheDownloadAndSaysSo() throws IOException {
    Tar tar = new Tar();
    for (int i = 0; i < 200; i++) {
      tar.file(ROOT + "src/f" + i + ".js", ("line " + i + "\n").repeat(i + 1));
    }
    byte[] gz = tar.gzip();

    Count partial = FrontLines.count(new ByteArrayInputStream(gz), gz.length / 3);
    Count whole = FrontLines.count(new ByteArrayInputStream(gz), Long.MAX_VALUE);

    assertThat(partial.truncated()).isTrue();
    assertThat(partial.files()).isLessThan(whole.files());
    assertThat(whole.truncated()).isFalse();
    assertThat(whole.files()).isEqualTo(200);
    assertThat(whole.lines()).isEqualTo(200L * 201 / 2);
  }

  @Test
  void aBrokenArchiveIsAnErrorNotAnEmptyCount() {
    byte[] gz = new Tar().file(ROOT + "a.js", "a\n".repeat(5000)).gzip();
    byte[] cut = Arrays.copyOf(gz, gz.length / 2);

    assertThatThrownBy(() -> FrontLines.count(new ByteArrayInputStream(cut), Long.MAX_VALUE))
        .isInstanceOf(IOException.class);
  }

  @Test
  void countsAddUp() {
    Count a = new Count(1, 10, Map.of("vue", 10L), Map.of(Skipped.BINARY, 1), false);
    Count b = new Count(2, 5, Map.of("vue", 2L, "css", 3L), Map.of(), true);

    Count sum = a.plus(b);

    assertThat(sum.files()).isEqualTo(3);
    assertThat(sum.lines()).isEqualTo(15);
    assertThat(sum.byExtensionText()).isEqualTo("vue 12, css 3");
    assertThat(sum.skippedText()).isEqualTo("binaires 1");
    assertThat(sum.truncated()).isTrue();
    assertThat(Count.EMPTY.plus(a)).isEqualTo(a);
  }

  @Test
  void blankLinesAreWhitespaceOnly() {
    assertThat(FrontLines.nonBlankLines(bytes(""))).isZero();
    assertThat(FrontLines.nonBlankLines(bytes("\n \t\n\r\n"))).isZero();
    assertThat(FrontLines.nonBlankLines(bytes("a\n\nb"))).isEqualTo(2);
    assertThat(FrontLines.nonBlankLines(bytes("  // comment\n"))).isEqualTo(1);
  }

  @Test
  void pathRules() {
    assertThat(FrontLines.extension("a/b.Vue")).isEqualTo("vue");
    assertThat(FrontLines.extension("a/.eslintrc")).isEmpty();
    assertThat(FrontLines.extension("Makefile")).isEmpty();
    assertThat(FrontLines.inSkippedDirectory("front/build/x.js")).isTrue();
    assertThat(FrontLines.inSkippedDirectory("front/build.js")).isFalse();
    assertThat(FrontLines.generatedByName("lib/Vendor.MIN.JS")).isTrue();
    assertThat(FrontLines.paxPath(bytes("30 mtime=1\n21 path=a/b/c.js\n"))).isEqualTo("a/b/c.js");
    assertThat(FrontLines.paxPath(bytes("52 comment=0123\n"))).isEmpty();
  }

  private static Count count(Tar tar) throws IOException {
    return FrontLines.count(new ByteArrayInputStream(tar.gzip()), Long.MAX_VALUE);
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  /** A tar written the way git archive writes one: ustar headers, pax records for long names. */
  private static final class Tar {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    Tar dir(String name) {
      return entry(name, "", '5', new byte[0]);
    }

    Tar file(String name, String content) {
      return entry(name, "", '0', bytes(content));
    }

    /** git archive's first entry: a global header holding the commit id. */
    Tar globalHeader() {
      return entry(
          "pax_global_header",
          "",
          'g',
          bytes("52 comment=0123456789012345678901234567890123456789\n"));
    }

    Tar paxPath(String path) {
      String body = " path=" + path + "\n";
      int len = body.length();
      len += String.valueOf(len + String.valueOf(len).length()).length();
      return entry("PaxHeader", "", 'x', bytes(len + body));
    }

    Tar ustarPrefixed(String path, String content) {
      int cut = path.lastIndexOf('/', 154);
      return entry(path.substring(cut + 1), path.substring(0, cut), '0', bytes(content));
    }

    private Tar entry(String name, String prefix, char type, byte[] content) {
      byte[] h = new byte[512];
      put(h, 0, name.length() > 100 ? name.substring(0, 100) : name);
      put(h, 100, "0000644");
      put(h, 124, String.format("%011o", content.length));
      h[156] = (byte) type;
      put(h, 257, "ustar");
      put(h, 263, "00");
      put(h, 345, prefix);
      Arrays.fill(h, 148, 156, (byte) ' ');
      long sum = 0;
      for (byte b : h) sum += b & 0xFF;
      put(h, 148, String.format("%06o", sum));
      out.writeBytes(h);
      out.writeBytes(content);
      out.writeBytes(new byte[(512 - content.length % 512) % 512]);
      return this;
    }

    private static void put(byte[] h, int offset, String s) {
      byte[] b = bytes(s);
      System.arraycopy(b, 0, h, offset, b.length);
    }

    byte[] gzip() {
      ByteArrayOutputStream gz = new ByteArrayOutputStream();
      try (GZIPOutputStream z = new GZIPOutputStream(gz)) {
        z.write(out.toByteArray());
        z.write(new byte[1024]);
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
      return gz.toByteArray();
    }
  }
}
