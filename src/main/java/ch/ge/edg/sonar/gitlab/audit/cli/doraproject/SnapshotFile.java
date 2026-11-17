package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * dora-donnees.json : un instantané écrit en JSON lisible, les dates en ISO-8601 UTC, et relu pour
 * refaire la page et l'import sans rien extraire de nouveau.
 */
final class SnapshotFile {

  static final String NAME = "dora-donnees.json";

  private static final ObjectWriter WRITER = mapper().writerWithDefaultPrettyPrinter();

  private static final ObjectReader READER = mapper().readerFor(Snapshot.class);

  private SnapshotFile() {}

  private static ObjectMapper mapper() {
    return new ObjectMapper()
        .registerModule(
            new SimpleModule()
                .addSerializer(Instant.class, new InstantWriter())
                .addDeserializer(Instant.class, new InstantReader()))
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  static void write(Snapshot snapshot, Path file) throws IOException {
    Files.writeString(file, json(snapshot) + "\n", StandardCharsets.UTF_8);
  }

  static String json(Snapshot snapshot) throws IOException {
    return WRITER.writeValueAsString(snapshot);
  }

  static Snapshot read(Path file) throws IOException {
    JsonNode tree = READER.readTree(Files.readString(file, StandardCharsets.UTF_8));
    return READER.readValue(upgrade(tree));
  }

  /**
   * Un fichier écrit avant {@link Snapshot.MainBranch} gardait les commits et les pushs de la
   * branche par défaut à la racine : ils y passent, vides compris, comme ils étaient lus.
   */
  static JsonNode upgrade(JsonNode tree) {
    if (!(tree instanceof ObjectNode o) || o.has("mainBranch")) return tree;
    JsonNode commits = o.remove("mainCommits");
    JsonNode pushes = o.remove("pushes");
    if (filled(commits) || filled(pushes)) {
      ObjectNode main = o.putObject("mainBranch");
      if (commits != null) main.set("commits", commits);
      if (pushes != null) main.set("pushes", pushes);
    }
    return o;
  }

  private static boolean filled(@Nullable JsonNode n) {
    return n != null && !n.isEmpty();
  }

  private static final class InstantWriter extends JsonSerializer<Instant> {
    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers)
        throws IOException {
      gen.writeString(value.toString());
    }
  }

  private static final class InstantReader extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      return Instant.parse(p.getValueAsString());
    }
  }
}
