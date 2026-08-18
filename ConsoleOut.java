//JAVA 25

import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
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
import java.util.Locale;
import java.util.Map;

/**
 * Sortie console partagée par les trois scripts. Tout l'affichage est en
 * français : l'encodage n'est pas un détail cosmétique ici, c'est la
 * différence entre un rapport lisible et une page de points d'interrogation.
 *
 * Le problème a deux moitiés opposées, et corriger l'une seule casse l'autre.
 *
 * REDIRIGÉ (fichier, pipe, CI). {@code stdout.encoding} retombe sur l'encodage
 * natif : sous une locale C — cas courant en conteneur, où LANG n'est pas défini
 * — cela vaut ANSI_X3.4-1968 et chaque accent devient '?'. Il faut donc imposer
 * UTF-8 et ne pas dépendre de l'environnement. C'est ce que faisaient les trois
 * scripts.
 *
 * TERMINAL WINDOWS. PowerShell affiche selon la page de code de la console,
 * typiquement cp850 ou cp1252, jamais UTF-8 sans un {@code chcp 65001}
 * préalable. Lui envoyer de l'UTF-8 produit exactement le symptôme rapporté :
 * « Ã© » à la place de « é ». Ici il faut au contraire encoder dans la page de
 * code réelle de la console — où tous les accents français existent.
 *
 * D'où la règle : on écrit dans l'encodage de la console quand on parle à un
 * terminal, en UTF-8 dès que la sortie est redirigée. Et comme une page de code
 * hérités ne connaît ni « — » ni « ≥ », les caractères qu'elle ne sait pas coder
 * sont translittérés en ASCII au lieu d'être remplacés par '?'.
 */
public final class ConsoleOut {

    private ConsoleOut() { }

    /** Forcée par les tests : simuler une console cp850 sans machine Windows. */
    private static final String FORCED = "audit.console.charset";

    private static boolean colors = true;

    public static void install() {
        Charset cs = target();
        System.setOut(wrap(FileDescriptor.out, cs));
        System.setErr(wrap(FileDescriptor.err, cs));
        colors = autoColors();
    }

    private static PrintStream wrap(FileDescriptor fd, Charset cs) {
        OutputStream raw = new FileOutputStream(fd);
        OutputStream out = cs.equals(StandardCharsets.UTF_8) ? raw : new Recoding(raw, cs);
        // Le PrintStream écrit toujours en UTF-8 ; c'est Recoding qui ramène
        // vers la page de code de la console. Une seule couche à comprendre.
        return new PrintStream(out, true, StandardCharsets.UTF_8);
    }

    static Charset target() {
        String forced = System.getProperty(FORCED);
        if (forced != null && !forced.isBlank()) {
            try {
                return Charset.forName(forced.trim());
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
        Console c = System.console();
        // isTerminal() est la question qui compte, et pas « console non nulle » :
        // depuis JDK 22 System.console() répond même quand la sortie est
        // redirigée, et sa charset vaut alors l'encodage natif — celui qui
        // massacre les accents en CI. C'est le piège que corrigeait le code
        // précédent, et il fallait éviter de le réintroduire en le corrigeant.
        if (c != null && c.isTerminal()) {
            Charset cs = c.charset();
            if (cs != null) return cs;
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Les couleurs ANSI sont le second symptôme sous Windows : conhost ne les
     * interprète pas tant qu'un programme n'a pas activé le mode VT, et
     * PowerShell 5.1 affiche alors « ←[1m » en clair. On ne les active donc sous
     * Windows que si l'émulateur s'annonce (Windows Terminal, ConEmu, ANSICON).
     * Ailleurs, un terminal suffit. NO_COLOR coupe tout, partout.
     */
    private static boolean autoColors() {
        if (System.getenv("NO_COLOR") != null) return false;
        Console c = System.console();
        if (c == null || !c.isTerminal()) return false;
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) return true;
        return System.getenv("WT_SESSION") != null
                || System.getenv("ConEmuANSI") != null
                || System.getenv("ANSICON") != null
                || System.getenv("TERM") != null;
    }

    public static boolean colors() {
        return colors;
    }

    /** auto | always | never — l'option en ligne de commande a le dernier mot. */
    public static void colorMode(String mode) {
        if (mode == null) return;
        switch (mode.toLowerCase(Locale.ROOT)) {
            case "always" -> colors = true;
            case "never" -> colors = false;
            default -> colors = autoColors();
        }
    }

    public static String color(String text, String code) {
        return colors ? code + text + "\033[0m" : text;
    }

    /**
     * Réencode un flux UTF-8 vers la page de code de la console, en
     * translittérant ce qu'elle ne sait pas représenter.
     *
     * Le décodage est incrémental : rien ne garantit qu'un write() s'arrête sur
     * une frontière de caractère, et couper un « é » en deux produirait le bug
     * qu'on est en train de corriger.
     */
    static final class Recoding extends OutputStream {

        private static final Map<Character, String> ASCII = Map.ofEntries(
                Map.entry('—', "-"), Map.entry('–', "-"), Map.entry('…', "..."),
                Map.entry('≥', ">="), Map.entry('≤', "<="), Map.entry('→', "->"),
                Map.entry('«', "\""), Map.entry('»', "\""), Map.entry('’', "'"),
                Map.entry('“', "\""), Map.entry('”', "\""), Map.entry('·', "-"),
                Map.entry('×', "x"), Map.entry('§', "S"), Map.entry('•', "*"),
                Map.entry(' ', " "), Map.entry(' ', " "));

        private final OutputStream target;
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        private final CharsetEncoder encoder;
        private ByteBuffer pending = ByteBuffer.allocate(0);

        Recoding(OutputStream target, Charset cs) {
            this.target = target;
            this.encoder = cs.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
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
         * Une page de code héritée ne connaît ni tiret cadratin ni « ≥ ». Plutôt
         * qu'un '?', on donne l'équivalent ASCII ; à défaut, on retire l'accent,
         * ce qui garde le mot lisible au lieu de le trouer.
         */
        private static String fallback(char ch) {
            String mapped = ASCII.get(ch);
            if (mapped != null) return mapped;
            String stripped = Normalizer.normalize(String.valueOf(ch), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}+", "");
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
}
