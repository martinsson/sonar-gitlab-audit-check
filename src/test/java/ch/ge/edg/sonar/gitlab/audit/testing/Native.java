package ch.ge.edg.sonar.gitlab.audit.testing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What differs between platforms, made the same for the tests. What the tool prints natively — line
 * ends, path separators — is turned into the Unix form the expectations and snapshots are written
 * in: on Linux and macOS a no-op, on Windows the tool rightly prints {@code \r\n} and {@code
 * C:\...\x.csv} to its user. A file is made unreadable the way its file system allows.
 */
public final class Native {

  private Native() {}

  /** The platform's line separator turned into {@code \n}. */
  public static String lines(String text) {
    return text.replace(System.lineSeparator(), "\n");
  }

  /**
   * {@code dir} replaced by {@code mark}, and the path that follows the mark written with {@code /}
   * — {@code <tmp>/in/a.csv} whatever the platform. The real path of {@code dir} is replaced too:
   * it differs from the given one behind a link (macOS /var) or a short name (Windows RUNNER~1).
   */
  public static String marked(String text, Path dir, String mark) {
    String absolute = dir.toAbsolutePath().toString();
    String marked = text.replace(real(dir), mark).replace(absolute, mark);
    if (File.separatorChar == '/') return marked;
    return Pattern.compile(Pattern.quote(mark) + "\\S*")
        .matcher(marked)
        .replaceAll(m -> Matcher.quoteReplacement(m.group().replace(File.separatorChar, '/')));
  }

  /**
   * The refused connection as macOS and Linux word it: Windows adds the failed call, {@code
   * Connection refused: getsockopt}.
   */
  public static String refused(String text) {
    return text.replaceAll("Connection refused: \\w+", "Connection refused");
  }

  /**
   * Makes {@code file} unreadable by its owner: no permission bits where the file system has them,
   * a deny-read entry in front of its access list elsewhere (Windows).
   */
  public static void unreadable(Path file) throws IOException {
    if (Files.getFileStore(file).supportsFileAttributeView(PosixFileAttributeView.class)) {
      Files.setPosixFilePermissions(file, Set.of());
      return;
    }
    AclFileAttributeView view = Files.getFileAttributeView(file, AclFileAttributeView.class);
    AclEntry denyRead =
        AclEntry.newBuilder()
            .setType(AclEntryType.DENY)
            .setPrincipal(view.getOwner())
            .setPermissions(AclEntryPermission.READ_DATA)
            .build();
    List<AclEntry> acl = new ArrayList<>(view.getAcl());
    acl.addFirst(denyRead);
    view.setAcl(acl);
  }

  private static String real(Path dir) {
    try {
      return dir.toRealPath().toString();
    } catch (IOException e) {
      return dir.toAbsolutePath().toString();
    }
  }
}
