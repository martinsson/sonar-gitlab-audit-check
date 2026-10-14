package ch.ge.edg.sonar.gitlab.audit.sonar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Repris de {@code Gitlab#map} : un fil virtuel par tâche, un sémaphore pour la largeur. Les fils
 * sont gratuits, les connexions ne le sont pas, et le proxy devant l'instance ne doit pas être ce
 * qui découvre la limite.
 */
final class Parallel {

  private Parallel() {}

  /**
   * Les exceptions ne sont pas avalées : une tâche qui échoue relance son exception à l'appelant,
   * comme si la boucle était restée séquentielle.
   */
  static <T, R> List<R> map(List<T> items, int concurrency, Function<T, R> work) {
    if (items.isEmpty()) return List.of();
    int width = Math.max(1, concurrency);
    if (width == 1 || items.size() == 1) {
      return items.stream().map(work).collect(Collectors.toCollection(ArrayList::new));
    }
    Semaphore inFlight = new Semaphore(width);
    List<R> out = new ArrayList<>(items.size());
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<R>> futures = new ArrayList<>(items.size());
      for (T item : items) futures.add(pool.submit(() -> bounded(inFlight, work, item)));
      for (Future<R> f : futures) out.add(await(f));
    }
    return out;
  }

  private static <T, R> R bounded(Semaphore inFlight, Function<T, R> work, T item)
      throws InterruptedException {
    inFlight.acquire();
    try {
      return work.apply(item);
    } finally {
      inFlight.release();
    }
  }

  private static <R> R await(Future<R> f) {
    try {
      return f.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrompu", e);
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof RuntimeException re) throw re;
      if (cause instanceof Error err) throw err;
      throw new IllegalStateException(cause);
    }
  }
}
