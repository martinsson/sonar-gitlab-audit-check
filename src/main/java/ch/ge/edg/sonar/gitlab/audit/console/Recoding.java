package ch.ge.edg.sonar.gitlab.audit.console;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Map;

/**
 * Réencode un flux UTF-8 vers la page de code de la console, en translittérant ce qu'elle ne sait
 * pas représenter.
 *
 * <p>Le décodage est incrémental : rien ne garantit qu'un write() s'arrête sur une frontière de
 * caractère, et couper un « é » en deux produirait le bug qu'on est en train de corriger.
 */
public final class Recoding extends OutputStream {

  private static final Map<Character, String> ASCII =
      Map.ofEntries(
          Map.entry('—', "-"),
          Map.entry('–', "-"),
          Map.entry('…', "..."),
          Map.entry('≥', ">="),
          Map.entry('≤', "<="),
          Map.entry('→', "->"),
          Map.entry('«', "\""),
          Map.entry('»', "\""),
          Map.entry('’', "'"),
          Map.entry('“', "\""),
          Map.entry('”', "\""),
          Map.entry('·', "-"),
          Map.entry('×', "x"),
          Map.entry('§', "S"),
          Map.entry('•', "*"),
          Map.entry(' ', " "),
          Map.entry(' ', " "));

  private final OutputStream target;
  private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
  private final CharsetEncoder encoder;
  private ByteBuffer pending = ByteBuffer.allocate(0);

  Recoding(OutputStream target, Charset cs) {
    this.target = target;
    this.encoder =
        cs.newEncoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE);
  }

  /**
   * Un flux d'impression vers {@code raw} dans le jeu de caractères {@code cs}. Le PrintStream
   * écrit toujours en UTF-8 ; c'est Recoding qui ramène vers la page de code de la console. Une
   * seule couche à comprendre.
   */
  public static PrintStream printStream(OutputStream raw, Charset cs) {
    OutputStream out = cs.equals(StandardCharsets.UTF_8) ? raw : new Recoding(raw, cs);
    return new PrintStream(out, true, StandardCharsets.UTF_8);
  }

  @Override
  public void write(int b) throws IOException {
    write(new byte[] {(byte) b}, 0, 1);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    ByteBuffer in = ByteBuffer.allocate(pending.remaining() + len);
    in.put(pending).put(b, off, len).flip();

    CharBuffer chars = CharBuffer.allocate(in.remaining() + 1);
    CoderResult r = decoder.decode(in, chars, false);
    if (r.isError()) r.isMalformed(); // ignoré : REPLACE par défaut suffit
    chars.flip();

    // Reste d'un caractère multi-octets coupé en deux : gardé pour le
    // prochain write plutôt que rendu tel quel.
    pending = ByteBuffer.allocate(in.remaining()).put(in);
    pending.flip();

    StringBuilder sb = new StringBuilder(chars.length());
    while (chars.hasRemaining()) {
      char ch = chars.get();
      if (encoder.canEncode(ch)) {
        sb.append(ch);
      } else {
        sb.append(fallback(ch));
      }
    }
    target.write(sb.toString().getBytes(encoder.charset()));
  }

  /**
   * Une page de code héritée ne connaît ni tiret cadratin ni « ≥ ». Plutôt qu'un '?', on donne
   * l'équivalent ASCII ; à défaut, on retire l'accent, ce qui garde le mot lisible au lieu de le
   * trouer.
   */
  private static String fallback(char ch) {
    String mapped = ASCII.get(ch);
    if (mapped != null) return mapped;
    String stripped =
        Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return stripped.isEmpty() ? "?" : stripped;
  }

  @Override
  public void flush() throws IOException {
    target.flush();
  }

  @Override
  public void close() throws IOException {
    flush();
    target.close();
  }
}
