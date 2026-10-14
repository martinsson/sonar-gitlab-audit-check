package ratchet;

import java.io.IOException;
import java.io.PrintStream;

/**
 * The ratchet: existing problems may stay, new ones may not, and nobody loosens a gate quietly.
 *
 * <pre>
 *   java tools/ratchet/Ratchet.java baseline     rewrite config/baseline from the reports in target/
 *   java tools/ratchet/Ratchet.java check REF    compare the working tree with git REF, exit 1 if looser
 * </pre>
 *
 * <p>Run through the verify script. JDK only, so it runs wherever the build runs; Maven compiles
 * these files as test sources so every gate of the project applies to them too.
 */
public final class Ratchet {

  private Ratchet() {}

  public static void main(String[] args) throws IOException {
    System.exit(run(args, System.out));
  }

  static int run(String[] args, PrintStream out) throws IOException {
    if (args.length == 1 && args[0].equals("baseline")) {
      new BaselineWriter(out).writeAll();
      return 0;
    }
    if (args.length == 2 && args[0].equals("check")) {
      return new RatchetCheck(args[1], out).run();
    }
    out.println("usage: java tools/ratchet/Ratchet.java baseline | check <git-ref>");
    return 2;
  }
}
