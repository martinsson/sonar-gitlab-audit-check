package ch.ge.edg.sonar.gitlab.audit.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RecodingTest {

  private static final Charset CP850 = Charset.forName("IBM850");

  /** Records what reaches the console, and whether it was flushed or closed. */
  private static final class Sink extends ByteArrayOutputStream {
    int flushes;
    boolean closed;

    @Override
    public void flush() {
      flushes++;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static byte[] recode(String text, Charset cs) throws IOException {
    Sink sink = new Sink();
    try (Recoding r = new Recoding(sink, cs)) {
      r.write(text.getBytes(StandardCharsets.UTF_8));
    }
    return sink.toByteArray();
  }

  @Test
  void accentsTheCodePageKnowsAreEncodedInIt() throws IOException {
    assertThat(recode("été à çà", CP850)).isEqualTo("été à çà".getBytes(CP850));
    assertThat(recode("été", CP850)).containsExactly(0x82, 0x74, 0x82);
  }

  @Test
  void typographicCharactersBecomeTheirAsciiEquivalent() throws IOException {
    String in = "—|–|…|≥|≤|→|«|»|’|“|”|·|" + "×|§|•| | ";
    assertThat(new String(recode(in, StandardCharsets.US_ASCII), StandardCharsets.US_ASCII))
        .isEqualTo("-|-|...|>=|<=|->|\"|\"|'|\"|\"|-|x|S|*| | ");
  }

  @Test
  void charactersTheCodePageHasAreKeptEvenIfTheAsciiTableKnowsThem() throws IOException {
    // cp850 has « » · × § and the non-breaking space: they are not transliterated.
    String in = "«»·×§ ";
    assertThat(recode(in, CP850)).isEqualTo(in.getBytes(CP850));
  }

  @Test
  void unknownAccentedLettersLoseTheirAccent() throws IOException {
    assertThat(new String(recode("ŝŵő", StandardCharsets.US_ASCII), StandardCharsets.US_ASCII))
        .isEqualTo("swo");
  }

  @Test
  void whatCannotBeStrippedBecomesAQuestionMark() throws IOException {
    // A lone combining accent strips to nothing; a CJK character strips to itself, which the
    // code page still cannot encode.
    assertThat(new String(recode("áb中c", CP850), CP850)).isEqualTo("a?b?c");
  }

  @Test
  void aCharacterSplitAcrossWritesIsDecodedOnceWhole() throws IOException {
    Sink sink = new Sink();
    byte[] e = "é".getBytes(StandardCharsets.UTF_8);
    Recoding r = new Recoding(sink, CP850);
    r.write(e[0]);
    assertThat(sink.size()).isZero();
    r.write(e[1]);
    assertThat(sink.toByteArray()).containsExactly(0x82);
    byte[] dash = "x—y".getBytes(StandardCharsets.UTF_8);
    r.write(dash, 0, 2);
    r.write(dash, 2, dash.length - 2);
    assertThat(new String(sink.toByteArray(), CP850)).isEqualTo("éx-y");
  }

  @Test
  void writeHonoursOffsetAndLength() throws IOException {
    Sink sink = new Sink();
    Recoding r = new Recoding(sink, CP850);
    r.write("abcdef".getBytes(StandardCharsets.UTF_8), 2, 3);
    assertThat(sink.toString(CP850)).isEqualTo("cde");
  }

  @Test
  void anInvalidUtf8ByteIsReplacedAndTheRestStillWritten() throws IOException {
    Sink sink = new Sink();
    Recoding r = new Recoding(sink, CP850);
    r.write(new byte[] {'a', (byte) 0xFF, 'b'}, 0, 3);
    r.write("cd".getBytes(StandardCharsets.UTF_8));
    assertThat(sink.toString(CP850)).isEqualTo("a?bcd");
  }

  @Test
  void flushAndCloseReachTheTarget() throws IOException {
    Sink sink = new Sink();
    Recoding r = new Recoding(sink, CP850);
    r.flush();
    assertThat(sink.flushes).isEqualTo(1);
    assertThat(sink.closed).isFalse();
    r.close();
    assertThat(sink.flushes).isEqualTo(2);
    assertThat(sink.closed).isTrue();
  }

  @Test
  void throughAUtf8PrintStreamAsInstallWiresIt() {
    Sink sink = new Sink();
    PrintStream ps = new PrintStream(new Recoding(sink, CP850), true, StandardCharsets.UTF_8);
    ps.print("Croisement SonarQube × GitLab — ≥ 20 %");
    ps.flush();
    assertThat(sink.toString(CP850)).isEqualTo("Croisement SonarQube × GitLab - >= 20 %");
  }
}
