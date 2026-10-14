package ch.ge.edg.sonar.gitlab.audit.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitlabParallelTest {

  private Gitlab gl;

  @BeforeEach
  void create() throws Exception {
    gl = new Gitlab("http://127.0.0.1:1", "t", 1, false, null);
  }

  private static List<Integer> upTo(int n) {
    return IntStream.rangeClosed(1, n).boxed().toList();
  }

  @Test
  void anEmptyListGivesAnImmutableEmptyResult() {
    List<Integer> out = gl.map(List.<Integer>of(), 4, x -> x);
    assertThat(out).isEmpty();
    assertThatThrownBy(() -> out.add(1)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void widthOneRunsInTheCallingThreadInOrder() {
    Thread caller = Thread.currentThread();
    List<String> threads = Collections.synchronizedList(new ArrayList<>());
    List<Integer> out =
        gl.map(
            upTo(5),
            1,
            x -> {
              threads.add(Thread.currentThread() == caller ? "caller" : "other");
              return x * 10;
            });
    assertThat(out).containsExactly(10, 20, 30, 40, 50);
    assertThat(threads).containsOnly("caller");
    out.add(60);
    assertThat(out).hasSize(6);
  }

  @Test
  void aNonPositiveWidthMeansSequential() {
    Thread caller = Thread.currentThread();
    List<Boolean> same = gl.map(upTo(3), 0, x -> Thread.currentThread() == caller);
    assertThat(same).containsExactly(true, true, true);
    assertThat(gl.map(upTo(3), -5, x -> Thread.currentThread() == caller)).containsOnly(true);
  }

  @Test
  void aSingleItemRunsInTheCallingThread() {
    Thread caller = Thread.currentThread();
    assertThat(gl.map(List.of(1), 8, x -> Thread.currentThread() == caller)).containsExactly(true);
  }

  @Test
  void manyItemsRunOnOtherThreadsAndKeepTheInputOrder() {
    Thread caller = Thread.currentThread();
    List<Integer> out = gl.map(upTo(20), 4, x -> x * x);
    assertThat(out).containsExactlyElementsOf(upTo(20).stream().map(x -> x * x).toList());
    assertThat(gl.map(upTo(2), 2, x -> Thread.currentThread() == caller)).containsOnly(false);
    out.set(0, -1);
    assertThat(out.getFirst()).isEqualTo(-1);
  }

  @Test
  void theWidthBoundsTheCallsInFlight() {
    AtomicInteger inFlight = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    gl.parallel(
        upTo(12),
        3,
        x -> {
          peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
          pause();
          inFlight.decrementAndGet();
        });
    assertThat(peak.get()).isBetween(2, 3);
  }

  @Test
  void parallelRunsEveryItem() {
    List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
    gl.parallel(upTo(10), 4, seen::add);
    assertThat(seen).containsExactlyInAnyOrderElementsOf(upTo(10));
    List<Integer> seq = new ArrayList<>();
    gl.parallel(upTo(3), 1, seq::add);
    assertThat(seq).containsExactly(1, 2, 3);
  }

  @Test
  void aRuntimeExceptionIsRethrownAsIs() {
    IllegalArgumentException boom = new IllegalArgumentException("boom");
    assertThatThrownBy(
            () ->
                gl.map(
                    upTo(5),
                    3,
                    x -> {
                      if (x == 4) throw boom;
                      return x;
                    }))
        .isSameAs(boom);
  }

  @Test
  void anErrorIsRethrownAsIs() {
    AssertionError boom = new AssertionError("boom");
    assertThatThrownBy(
            () ->
                gl.parallel(
                    upTo(3),
                    3,
                    x -> {
                      throw boom;
                    }))
        .isSameAs(boom);
  }

  @Test
  void aCheckedExceptionIsWrapped() {
    IOException boom = new IOException("boom");
    assertThatThrownBy(() -> gl.parallel(upTo(3), 3, x -> GitlabParallelTest.sneaky(boom)))
        .isInstanceOf(IllegalStateException.class)
        .hasCause(boom);
  }

  @Test
  void anInterruptedCallerStopsWaiting() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> gl.map(upTo(3), 3, x -> x))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("interrompu")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  private static void pause() {
    try {
      Thread.sleep(30);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @SuppressWarnings("unchecked")
  private static <E extends Throwable> void sneaky(Throwable t) throws E {
    throw (E) t;
  }
}
