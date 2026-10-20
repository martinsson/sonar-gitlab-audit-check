package ch.ge.edg.sonar.gitlab.audit.dora;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonlTest {

  @TempDir Path dir;

  @Test
  void one_compact_json_object_per_line() throws IOException {
    ObjectMapper m = new ObjectMapper();
    Path f = dir.resolve("runs.jsonl");
    try (Jsonl out = new Jsonl(f)) {
      out.write(m.readTree("{ \"a\" : 1 }"));
      out.write(m.readTree("[2]"));
      assertThat(out.rows()).isEqualTo(2);
    }
    assertThat(Files.readString(f)).isEqualTo("{\"a\":1}\n[2]\n");
  }
}
