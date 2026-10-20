package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** What a mapping is asked for: methods, name spec, confidence codes, the preset. */
class SpecTest {

  private static Csv.Row row(String column, String value) {
    return new Csv.Row(Map.of(column, 0), new String[] {value});
  }

  @ParameterizedTest
  @CsvSource({
    "' Exact ',EXACT",
    "derived,DERIVED",
    "Dérivé,DERIVED",
    "SUGGESTION,SUGGESTION",
    "manuel,MANUEL",
    "manual,MANUEL",
    "Confirmé,MANUEL",
    "Rejeté,REJETE",
    "rejected,REJETE",
    "'',NONE",
    "none,NONE",
    "whatever,NONE"
  })
  void confidenceParseAcceptsFrenchAndEnglishSpellingsWithAccents(
      String spelling, Confidence expected) {
    assertThat(Confidence.parse(spelling)).isEqualTo(expected);
  }

  @Test
  void confidenceCodesAreLowercaseNames() {
    assertThat(Confidence.REJETE.code()).isEqualTo("rejete");
    assertThat(Confidence.SUGGESTION.code()).isEqualTo("suggestion");
  }

  @Test
  void keyMethodParseReadsIdColumnsAndOptionalFilter() {
    KeyMethod m = KeyMethod.parse("  ref-1: left col = right col ", Kind.EXACT);
    assertThat(m)
        .isEqualTo(
            new KeyMethod("ref-1", "ref-1", Kind.EXACT, "left col", "right col", null, null));
  }

  @Test
  void aSpacedFilterValueMatchesTheTrimmedCell() {
    KeyMethod m = KeyMethod.parse("x:a=b? kind = v", Kind.LINK);
    assertThat(m.accepts(row("kind", "v"))).isTrue();
  }

  @Test
  void keyMethodParseFilterValueIsTrimmedAndKeepsEqualSigns() {
    KeyMethod m = KeyMethod.parse("x:a=b? kind = v=w", Kind.LINK);
    assertThat(m.whereColumn()).isEqualTo("kind");
    assertThat(m.whereValue()).isEqualTo("v=w");
    assertThat(m.accepts(row("kind", "v=w"))).isTrue();
    KeyMethod plain = KeyMethod.parse("x:a=b?kind=main", Kind.LINK);
    assertThat(plain.accepts(row("kind", "main"))).isTrue();
    assertThat(plain.accepts(row("kind", "other"))).isFalse();
    assertThat(plain.accepts(row("nothing", "main"))).isFalse();
    assertThat(KeyMethod.parse("x:a=b", Kind.LINK).accepts(row("kind", "x"))).isTrue();
  }

  @Test
  void keyMethodConfidenceAndJoinsFollowTheKind() {
    assertThat(method(Kind.EXACT).confidence()).isEqualTo(Confidence.EXACT);
    assertThat(method(Kind.DERIVED).confidence()).isEqualTo(Confidence.DERIVED);
    assertThat(method(Kind.MAPPED).confidence()).isEqualTo(Confidence.DERIVED);
    assertThat(method(Kind.NAME).confidence()).isEqualTo(Confidence.DERIVED);
    assertThat(method(Kind.LINK).confidence()).isEqualTo(Confidence.NONE);
    assertThat(method(Kind.LINK).joins()).isFalse();
    assertThat(method(Kind.NAME).joins()).isTrue();
  }

  private static KeyMethod method(Kind kind) {
    return new KeyMethod("i", "l", kind, "a", "b", null, null);
  }

  @Test
  void nameSpecParseSplitsRightColumnsOnCommas() {
    assertThat(NameSpec.parse(" nom = libelle , code ", true))
        .isEqualTo(new NameSpec("nom", List.of("libelle", "code"), true));
    assertThat(NameSpec.parse("nom=", false)).isEqualTo(new NameSpec("nom", List.of(""), false));
  }

  @Test
  void theSonarGitlabPreset() {
    Spec s = Spec.sonarGitlab(0.4, 0.2);
    assertThat(s.leftKey()).isEqualTo("path");
    assertThat(s.rightKey()).isEqualTo("key");
    assertThat(s.threshold()).isEqualTo(0.4);
    assertThat(s.margin()).isEqualTo(0.2);
    assertThat(s.names()).isEqualTo(new NameSpec("path", List.of("key", "name"), true));
    assertThat(s.methods())
        .extracting(m -> m.id() + "|" + m.kind() + "|" + m.leftColumn() + "=" + m.rightColumn())
        .containsExactly(
            "cle_ci|EXACT|cle_sonar=key",
            "cle_pom|EXACT|cle_pom=key",
            "liaison|EXACT|id=alm_repository",
            "ns_map|MAPPED|path=key",
            "chemin|DERIVED|path=key",
            "nom_pom|NAME|nom_pom=name",
            "liens|LINK|path=liens");
    assertThat(s.methods().get(2).whereColumn()).isEqualTo("alm");
    assertThat(s.methods().get(2).whereValue()).isEqualTo("gitlab");
    Spec k = s.withKeys("l", "r");
    assertThat(k).isEqualTo(new Spec("l", "r", s.methods(), s.names(), 0.4, 0.2));
  }

  @Test
  void aNameGuessIsAcceptedWhenItHasACandidateAndNoRejection() {
    assertThat(new NameGuess("k", 0.9, "", 0, null).accepted()).isTrue();
    assertThat(new NameGuess("", 0.9, "", 0, null).accepted()).isFalse();
    assertThat(new NameGuess("k", 0.9, "", 0, "ambigu").accepted()).isFalse();
  }

  @Test
  void aMatchIsJoinedOnlyWhenSureAndPaired() {
    Csv.Row r = row("key", "k");
    assertThat(match(r, Confidence.EXACT).joined()).isTrue();
    assertThat(match(r, Confidence.DERIVED).joined()).isTrue();
    assertThat(match(r, Confidence.MANUEL).joined()).isTrue();
    assertThat(match(r, Confidence.SUGGESTION).joined()).isFalse();
    assertThat(match(r, Confidence.REJETE).joined()).isFalse();
    assertThat(match(null, Confidence.EXACT).joined()).isFalse();
    Spec s = Spec.sonarGitlab(0.5, 0.1);
    assertThat(match(r, Confidence.EXACT).rightKey(s)).isEqualTo("k");
    assertThat(match(null, Confidence.EXACT).rightKey(s)).isEmpty();
  }

  private static Match match(Csv.@Nullable Row right, Confidence c) {
    return new Match(0, row("path", "p"), new Choice(right, "m", "l", c), Map.of(), null, null);
  }
}
