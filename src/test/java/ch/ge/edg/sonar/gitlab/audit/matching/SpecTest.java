package ch.ge.edg.sonar.gitlab.audit.matching;

import static org.assertj.core.api.Assertions.assertThat;

import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.testing.SuspectedBug;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** What a mapping is asked for: methods, name spec, confidence codes, the preset. */
class SpecTest {

  private static Csv.Row row(String column, String value) {
    return new Csv.Row(Map.of(column, 0), new String[] {value});
  }

  @Test
  void confidence_parse_accepts_french_and_english_spellings_with_accents() {
    assertThat(Confidence.parse(" Exact ")).isEqualTo(Confidence.EXACT);
    assertThat(Confidence.parse("derived")).isEqualTo(Confidence.DERIVED);
    assertThat(Confidence.parse("Dérivé")).isEqualTo(Confidence.DERIVED);
    assertThat(Confidence.parse("SUGGESTION")).isEqualTo(Confidence.SUGGESTION);
    assertThat(Confidence.parse("manuel")).isEqualTo(Confidence.MANUEL);
    assertThat(Confidence.parse("manual")).isEqualTo(Confidence.MANUEL);
    assertThat(Confidence.parse("Confirmé")).isEqualTo(Confidence.MANUEL);
    assertThat(Confidence.parse("Rejeté")).isEqualTo(Confidence.REJETE);
    assertThat(Confidence.parse("rejected")).isEqualTo(Confidence.REJETE);
    assertThat(Confidence.parse("")).isEqualTo(Confidence.NONE);
    assertThat(Confidence.parse("none")).isEqualTo(Confidence.NONE);
    assertThat(Confidence.parse("whatever")).isEqualTo(Confidence.NONE);
  }

  @Test
  void confidence_codes_are_lowercase_names() {
    assertThat(Confidence.REJETE.code()).isEqualTo("rejete");
    assertThat(Confidence.SUGGESTION.code()).isEqualTo("suggestion");
  }

  @Test
  void key_method_parse_reads_id_columns_and_optional_filter() {
    KeyMethod m = KeyMethod.parse("  ref-1: left col = right col ", Kind.EXACT);
    assertThat(m)
        .isEqualTo(
            new KeyMethod("ref-1", "ref-1", Kind.EXACT, "left col", "right col", null, null));
  }

  @Test
  @SuspectedBug("The ?col=val value is not trimmed, so '? kind = v' never matches a 'v' cell")
  void a_spaced_filter_value_never_matches_the_cell() {
    KeyMethod m = KeyMethod.parse("x:a=b? kind = v", Kind.LINK);
    assertThat(m.accepts(row("kind", "v"))).isFalse();
  }

  @Test
  void key_method_parse_filter_value_keeps_spaces_and_equal_signs() {
    KeyMethod m = KeyMethod.parse("x:a=b? kind = v=w", Kind.LINK);
    assertThat(m.whereColumn()).isEqualTo("kind");
    assertThat(m.whereValue()).isEqualTo(" v=w");
    assertThat(m.accepts(row("kind", "v=w"))).isFalse();
    KeyMethod plain = KeyMethod.parse("x:a=b?kind=main", Kind.LINK);
    assertThat(plain.accepts(row("kind", "main"))).isTrue();
    assertThat(plain.accepts(row("kind", "other"))).isFalse();
    assertThat(plain.accepts(row("nothing", "main"))).isFalse();
    assertThat(KeyMethod.parse("x:a=b", Kind.LINK).accepts(row("kind", "x"))).isTrue();
  }

  @Test
  void key_method_confidence_and_joins_follow_the_kind() {
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
  void name_spec_parse_splits_right_columns_on_commas() {
    assertThat(NameSpec.parse(" nom = libelle , code ", true))
        .isEqualTo(new NameSpec("nom", List.of("libelle", "code"), true));
    assertThat(NameSpec.parse("nom=", false)).isEqualTo(new NameSpec("nom", List.of(""), false));
  }

  @Test
  void the_sonar_gitlab_preset() {
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
  void a_name_guess_is_accepted_when_it_has_a_candidate_and_no_rejection() {
    assertThat(new NameGuess("k", 0.9, "", 0, null).accepted()).isTrue();
    assertThat(new NameGuess("", 0.9, "", 0, null).accepted()).isFalse();
    assertThat(new NameGuess("k", 0.9, "", 0, "ambigu").accepted()).isFalse();
  }

  @Test
  void a_match_is_joined_only_when_sure_and_paired() {
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
