package ch.ge.edg.sonar.gitlab.audit.gocd;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Les réponses GoCD lues dans des records : seuls les champs que l'outil lit, en snake_case comme
 * GoCD les écrit. Un champ inconnu est ignoré ; un texte ou une liste absents ou nuls valent vide,
 * pour que les records n'aient jamais à le vérifier. Une date est un epoch en millisecondes (ce que
 * GoCD écrit) ou de l'ISO-8601 ; illisible, elle vaut null.
 */
final class GocdJson {

  private static final ObjectReader READER = reader();

  private GocdJson() {}

  private static ObjectReader reader() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    mapper
        .configOverride(String.class)
        .setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    mapper.configOverride(List.class).setSetterInfo(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY));
    mapper.registerModule(new SimpleModule().addDeserializer(Instant.class, new Epoch()));
    return mapper.reader();
  }

  /**
   * Un nœud JSON en record.
   *
   * @throws IllegalStateException si GoCD a changé la forme d'un champ lu (un texte là où l'outil
   *     attend un nombre, par exemple) : mieux vaut s'arrêter que lire de travers
   */
  static <T> T read(JsonNode node, Class<T> type) {
    try {
      return READER.treeToValue(node.isMissingNode() ? READER.createObjectNode() : node, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "Réponse GoCD inattendue pour " + type.getSimpleName() + " : " + e.getOriginalMessage(),
          e);
    }
  }

  /** Epoch en millisecondes, ou ISO-8601 avec décalage. */
  static final class Epoch extends JsonDeserializer<Instant> {

    @Override
    public @Nullable Instant deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException {
      if (p.currentToken().isNumeric()) return Instant.ofEpochMilli(p.getLongValue());
      return parse(p.getValueAsString(""));
    }

    static @Nullable Instant parse(String s) {
      try {
        return s.isBlank() ? null : OffsetDateTime.parse(s).toInstant();
      } catch (DateTimeParseException e) {
        return null;
      }
    }
  }
}
