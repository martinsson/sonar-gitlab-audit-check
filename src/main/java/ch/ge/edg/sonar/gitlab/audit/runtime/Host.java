package ch.ge.edg.sonar.gitlab.audit.runtime;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.console.Recoding;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

/**
 * Le monde extérieur tel qu'une commande le reçoit : ses flux, son environnement, son horloge (et
 * avec elle le fuseau de toute la course), son sommeil, son répertoire de travail et le terminal.
 * {@link #system()} est le seul endroit du programme qui lit {@code System.out}/{@code err},
 * l'environnement, l'horloge et le fuseau du système, la console et le répertoire courant. Une
 * commande le prend dans son {@code main} et passe chaque morceau séparément ; rien sous les
 * commandes ne le voit. Les tests le construisent avec des valeurs fixes. (Host et pas Runtime : le
 * nom de java.lang.Runtime, qu'il masquerait dans chaque fichier qui l'importe.)
 *
 * @param env les variables d'environnement
 * @param workDir contre quoi se résolvent les fichiers nommés en ligne de commande et les fichiers
 *     par défaut
 */
public record Host(
    PrintStream out,
    PrintStream err,
    Map<String, String> env,
    Clock clock,
    Sleeper sleeper,
    Path workDir,
    Terminal terminal) {

  /** Forcée par les tests : simuler une console cp850 sans machine Windows. */
  static final String FORCED_CHARSET = "audit.console.charset";

  public Host {
    env = Map.copyOf(env);
  }

  /**
   * Le vrai monde. Tout l'affichage est en français : l'encodage n'est pas un détail cosmétique
   * ici, c'est la différence entre un rapport lisible et une page de points d'interrogation.
   *
   * <p>Le problème a deux moitiés opposées, et corriger l'une seule casse l'autre.
   *
   * <p>REDIRIGÉ (fichier, pipe, CI). {@code stdout.encoding} retombe sur l'encodage natif : sous
   * une locale C — cas courant en conteneur, où LANG n'est pas défini — cela vaut ANSI_X3.4-1968 et
   * chaque accent devient '?'. Il faut donc imposer UTF-8 et ne pas dépendre de l'environnement.
   *
   * <p>TERMINAL WINDOWS. PowerShell affiche selon la page de code de la console, typiquement cp850
   * ou cp1252, jamais UTF-8 sans un {@code chcp 65001} préalable. Lui envoyer de l'UTF-8 produit
   * exactement le symptôme rapporté : « Ã© » à la place de « é ». Ici il faut au contraire encoder
   * dans la page de code réelle de la console — où tous les accents français existent.
   *
   * <p>D'où la règle : on écrit dans l'encodage de la console quand on parle à un terminal, en
   * UTF-8 dès que la sortie est redirigée, et {@link Recoding} translittère ce qu'une page de code
   * héritée ne sait pas coder. Les flux du système sont remplacés aussi, pour qu'une trace de pile
   * imprévue reste lisible.
   */
  public static Host system() {
    java.io.@Nullable Console console = systemConsole();
    Charset cs =
        charset(System.getProperty(FORCED_CHARSET), console == null ? null : console.charset());
    PrintStream out = Recoding.printStream(new FileOutputStream(FileDescriptor.out), cs);
    PrintStream err = Recoding.printStream(new FileOutputStream(FileDescriptor.err), cs);
    System.setOut(out);
    System.setErr(err);
    boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    return new Host(
        out,
        err,
        System.getenv(),
        Clock.system(ZoneId.systemDefault()),
        Sleeper.system(),
        // Le répertoire courant, laissé relatif : un fichier par défaut s'affiche comme on le
        // nomme.
        Path.of(""),
        new Terminal(console != null, windows));
  }

  /**
   * Le jeu de caractères de la sortie : celui que les tests forcent, sinon celui du terminal, sinon
   * UTF-8. Un nom forcé qu'on ne connaît pas ne doit pas empêcher d'afficher.
   */
  static Charset charset(@Nullable String forced, @Nullable Charset terminal) {
    if (forced != null && !forced.isBlank()) {
      try {
        return Charset.forName(forced.trim());
      } catch (IllegalArgumentException e) {
        // IllegalCharsetNameException ou UnsupportedCharsetException.
        return StandardCharsets.UTF_8;
      }
    }
    return terminal == null ? StandardCharsets.UTF_8 : terminal;
  }

  /**
   * La console, si la JVM parle à un terminal. isTerminal() est la question qui compte, et pas «
   * console non nulle » : depuis JDK 22 System.console() répond même quand la sortie est redirigée,
   * et sa charset vaut alors l'encodage natif — celui qui massacre les accents en CI. Elle peut
   * encore rendre null : depuis JDK 25 elle le fait de nouveau quand l'entrée et la sortie sont
   * redirigées (vérifié sur JDK 26), alors que SystemConsoleNull d'Error Prone date du comportement
   * de JDK 22 : d'où l'Optional.
   */
  private static java.io.@Nullable Console systemConsole() {
    return Optional.ofNullable(System.console()).filter(java.io.Console::isTerminal).orElse(null);
  }

  /** La console de la commande, en couleurs selon {@code colorMode} (auto | always | never). */
  public Console console(@Nullable String colorMode) {
    return new Console(out, err, Console.colors(colorMode, terminal.colors(env)));
  }

  /** La ligne de commande picocli de {@code command}, qui écrit aide et erreurs sur nos flux. */
  public CommandLine commandLine(Object command) {
    return new CommandLine(command).setOut(writer(out)).setErr(writer(err));
  }

  private static PrintWriter writer(PrintStream stream) {
    return new PrintWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8), true);
  }
}
