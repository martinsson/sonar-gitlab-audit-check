package ch.ge.edg.sonar.gitlab.audit.cli.storyinspection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Les tickets d'un répertoire consigné par {@code --dump-dir}, lus sans appeler Jira. */
final class ReplayDump {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ReplayDump() {}

  static List<JsonNode> issues(Path dir) throws IOException {
    List<Path> files;
    try (Stream<Path> listing = Files.list(dir)) {
      files = listing.filter(ReplayDump::isSearchPage).sorted().toList();
    }
    if (files.isEmpty()) {
      throw new IOException("Aucun fichier de replay trouvé dans " + dir);
    }
    List<JsonNode> issues = new ArrayList<>();
    for (Path file : files) {
      JSON.readTree(file.toFile()).path("issues").forEach(issues::add);
    }
    return issues;
  }

  private static boolean isSearchPage(Path file) {
    String name = String.valueOf(file.getFileName());
    return name.startsWith("search_") && name.endsWith(".json");
  }
}
