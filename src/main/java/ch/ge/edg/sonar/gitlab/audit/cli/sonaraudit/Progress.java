package ch.ge.edg.sonar.gitlab.audit.cli.sonaraudit;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.text.Text;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Une passe qui dure des minutes sans rien écrire ne se distingue pas d'un blocage. Sur un terminal
 * on réécrit la même ligne ; ailleurs — CI, sortie redirigée — on se tait, parce qu'un fichier de
 * log n'a que faire de 500 lignes de compteur.
 */
final class Progress {
  private final Console out;
  private final String label;
  private final int total;
  private final AtomicInteger done = new AtomicInteger();
  private final boolean live;

  Progress(Console out, String label, int total) {
    this.out = out;
    this.live = out.colors();
    this.label = label;
    this.total = total;
    if (live && total > 0) out.print(Text.format("%s 0/%d\r", label, total));
  }

  void tick() {
    int n = done.incrementAndGet();
    if (live && (n % 25 == 0 || n == total)) {
      out.print(Text.format("%s %d/%d\r", label, n, total));
      out.flush();
    }
  }

  void done() {
    if (live && total > 0) out.print(Text.format("%s %d/%d%n", label, done.get(), total));
  }
}
