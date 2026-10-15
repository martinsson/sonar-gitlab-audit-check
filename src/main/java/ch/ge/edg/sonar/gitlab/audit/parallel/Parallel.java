package ch.ge.edg.sonar.gitlab.audit.parallel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Des appels réseau indépendants, faits en recouvrement et bornés en largeur.
 *
 * <p>Un fil virtuel par tâche, un sémaphore pour la largeur : les fils sont gratuits, les
 * connexions ne le sont pas. Le sémaphore borne les appels en vol, pas les objets créés.
 *
 * <p>Les exceptions ne sont pas avalées : une tâche qui échoue relance son exception à l'appelant,
 * comme si la boucle était restée séquentielle.
 */
public final class Parallel {

  private Parallel() {}

  /** Une tâche par élément ; elle peut lever une exception vérifiée, relancée enveloppée. */
  @FunctionalInterface
  public interface Task<T, R> {
    R apply(T item) throws Exception;
  }

  /** Chaque élément passé à {@code work}, au plus {@code concurrency} à la fois. */
  public static <T> void forEach(List<T> items, int concurrency, Consumer<T> work) {
    map(
        items,
        concurrency,
        item -> {
          work.accept(item);
          return item; // le résultat est jeté ; Task ne rend pas null
        });
  }

  /** Même chose, en conservant les résultats dans l'ordre des entrées. */
  public static <T, R> List<R> map(List<T> items, int concurrency, Task<T, R> work) {
    if (items.isEmpty()) return List.of();
    int width = Math.max(1, concurrency);
    if (width == 1 || items.size() == 1) return inline(items, work);
    Semaphore inFlight = new Semaphore(width);
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<R>> futures = new ArrayList<>(items.size());
      for (T item : items) futures.add(pool.submit(() -> bounded(inFlight, work, item)));
      List<R> out = new ArrayList<>(futures.size());
      for (Future<R> f : futures) out.add(await(f));
      return out;
    }
  }

  /**
   * Dans le fil de l'appelant, une tâche après l'autre, avec les mêmes exceptions qu'en parallèle.
   */
  private static <T, R> List<R> inline(List<T> items, Task<T, R> work) {
    List<R> out = new ArrayList<>(items.size());
    for (T item : items) {
      FutureTask<R> task = new FutureTask<>(() -> work.apply(item));
      task.run();
      out.add(await(task));
    }
    return out;
  }

  private static <T, R> R bounded(Semaphore inFlight, Task<T, R> work, T item) throws Exception {
    inFlight.acquire();
    try {
      return work.apply(item);
    } finally {
      inFlight.release();
    }
  }

  /** Le résultat d'une tâche, ou son exception telle qu'elle a été levée. */
  private static <R> R await(Future<R> future) {
    try {
      return future.get();
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
