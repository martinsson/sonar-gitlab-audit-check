package ch.ge.edg.sonar.gitlab.audit.dora;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Une ligne JSON par objet : les réponses brutes, gardées pour appliquer une nouvelle règle sans
 * extraire de nouveau.
 */
public final class Jsonl implements AutoCloseable {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final BufferedWriter out;
  private int rows;

  public Jsonl(Path path) throws IOException {
    this.out = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
  }

  public void write(JsonNode node) throws IOException {
    out.write(JSON.writeValueAsString(node));
    out.write('\n');
    rows++;
  }

  public int rows() {
    return rows;
  }

  @Override
  public void close() throws IOException {
    out.close();
  }
}
