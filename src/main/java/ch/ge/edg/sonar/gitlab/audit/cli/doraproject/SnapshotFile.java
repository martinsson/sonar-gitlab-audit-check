package ch.ge.edg.sonar.gitlab.audit.cli.doraproject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * dora-donnees.json : un instantané écrit en JSON lisible, les dates en ISO-8601 UTC, et relu pour
 * refaire la page et l'import sans rien extraire de nouveau.
 */
final class SnapshotFile {

  static final String NAME = "dora-donnees.json";

  private static final ObjectMapper JSON =
      new ObjectMapper()
          .registerModule(
              new SimpleModule()
                  .addSerializer(Instant.class, new InstantWriter())
                  .addDeserializer(Instant.class, new InstantReader()))
          .enable(SerializationFeature.INDENT_OUTPUT)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private SnapshotFile() {}

  static void write(Snapshot snapshot, Path file) throws IOException {
    Files.writeString(file, json(snapshot) + "\n", StandardCharsets.UTF_8);
  }

  static String json(Snapshot snapshot) throws IOException {
    return JSON.writeValueAsString(snapshot);
  }

  static Snapshot read(Path file) throws IOException {
    return JSON.readValue(Files.readString(file, StandardCharsets.UTF_8), Snapshot.class);
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
