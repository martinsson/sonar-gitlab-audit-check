package ch.ge.edg.sonar.gitlab.audit.parallel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParallelTest {

  private static Integer failing(Integer i) throws IOException {
    throw new IOException("io" + i);
  }

  @Test
  void aCheckedExceptionIsWrappedInParallel() {
    assertThatThrownBy(() -> Parallel.map(List.of(1, 2), 2, ParallelTest::failing))
        .isInstanceOf(IllegalStateException.class)
        .hasCauseInstanceOf(IOException.class)
        .cause()
        .hasMessage("io1");
  }

  @Test
  void aCheckedExceptionIsWrappedTheSameWayInline() {
    assertThatThrownBy(() -> Parallel.map(List.of(7), 4, ParallelTest::failing))
        .isInstanceOf(IllegalStateException.class)
        .cause()
        .isInstanceOf(IOException.class)
        .hasMessage("io7");
  }

  @Test
  void inlineKeepsOrderAndRethrowsRuntimeExceptionsAsIs() {
    assertThat(Parallel.map(List.of(1, 2, 3), 1, i -> i * 10)).containsExactly(10, 20, 30);
    IllegalArgumentException boom = new IllegalArgumentException("boom");
    assertThatThrownBy(
            () ->
                Parallel.map(
                    List.of(1),
                    1,
                    i -> {
                      throw boom;
                    }))
        .isSameAs(boom);
  }

  @Test
  void anInterruptedCallerStopsEvenWhenTheTasksAreAlreadyDone() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> Parallel.map(List.of(1, 2), 1, x -> x))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("interrompu")
          .hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
