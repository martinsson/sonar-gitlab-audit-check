package ch.ge.edg.sonar.gitlab.audit.sonar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Pure helpers: organization rule, query strings, bounded parallel map. */
class SonarHelpersTest {

  private static Sonar sonar() throws Exception {
    return new Sonar(new SonarConnection("http://127.0.0.1:1", "t", null, 1, false));
  }

  @Test
  void needsOrganizationOnlyForTheListedPrefixes() {
    assertThat(Sonar.needsOrganization("api/components/search_projects")).isTrue();
    assertThat(Sonar.needsOrganization("api/qualityprofiles/search")).isTrue();
    assertThat(Sonar.needsOrganization("api/projects/search")).isTrue();
    assertThat(Sonar.needsOrganization("api/issues/search")).isTrue();
    assertThat(Sonar.needsOrganization("api/qualitygates/get_by_project")).isTrue();
    assertThat(Sonar.needsOrganization("api/components/tree")).isFalse();
    assertThat(Sonar.needsOrganization("api/measures/search")).isFalse();
    assertThat(Sonar.needsOrganization("/api/issues/search")).isFalse();
  }

  @Test
  void queryStringIsEmptyForNoParams() {
    assertThat(Sonar.queryString(Map.of())).isEmpty();
  }

  @Test
  void queryStringEncodesKeysAndValuesInOrder() {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("metric Keys", "a,b");
    p.put("q", "é&=");
    assertThat(Sonar.queryString(p)).isEqualTo("?metric+Keys=a%2Cb&q=%C3%A9%26%3D");
  }

  @Test
  void queryStringOfOnlyNullValuesIsAQuestionMark() {
    Map<String, String> p = new LinkedHashMap<>();
    p.put("a", null);
    assertThat(Sonar.queryString(p)).isEqualTo("?");
  }

  @Test
  void mapOfNothingIsEmpty() throws Exception {
    assertThat(sonar().<String, String>map(List.of(), 4, s -> s)).isEmpty();
  }

  @Test
  void sequentialMapKeepsOrderAndIsMutable() throws Exception {
    List<Integer> out = sonar().map(List.of(1, 2, 3), 1, i -> i * 10);
    assertThat(out).containsExactly(10, 20, 30);
    out.add(40);
    assertThat(sonar().map(List.of(1, 2), 0, i -> i + 1)).containsExactly(2, 3);
    assertThat(sonar().map(List.of(5), 8, i -> i + 1)).containsExactly(6);
  }

  @Test
  void parallelMapKeepsInputOrderAndBoundsConcurrency() throws Exception {
    AtomicInteger inFlight = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    List<Integer> items = IntStream.range(0, 40).boxed().toList();
    List<Integer> out =
        sonar()
            .map(
                items,
                3,
                i -> {
                  peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                  pause();
                  inFlight.decrementAndGet();
                  return 100 - i;
                });
    assertThat(out).isEqualTo(items.stream().map(i -> 100 - i).toList());
    assertThat(peak.get()).isBetween(2, 3);
  }

  @Test
  void parallelMapRethrowsRuntimeExceptions() {
    assertThatThrownBy(() -> sonar().map(List.of(1, 2), 2, SonarHelpersTest::failOnTwo))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("two");
  }

  @Test
  void parallelMapRethrowsErrors() {
    assertThatThrownBy(
            () ->
                sonar()
                    .map(
                        List.of(1, 2),
                        2,
                        i -> {
                          throw new AssertionError("err" + i);
                        }))
        .isInstanceOf(AssertionError.class)
        .hasMessage("err1");
  }

  @Test
  void interruptedCallerGetsAnIllegalStateException() throws Exception {
    Sonar sq = sonar();
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> sq.map(List.of(1, 2), 2, i -> i))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("interrompu")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void parallelRunsEveryItem() throws Exception {
    List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
    sonar().parallel(List.of(1, 2, 3), 2, seen::add);
    assertThat(seen).containsExactlyInAnyOrder(1, 2, 3);
  }

  private static Integer failOnTwo(Integer i) {
    if (i == 2) throw new IllegalArgumentException("two");
    return i;
  }

  private static void pause() {
    try {
      Thread.sleep(5);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
