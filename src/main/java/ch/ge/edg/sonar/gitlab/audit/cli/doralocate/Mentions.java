package ch.ge.edg.sonar.gitlab.audit.cli.doralocate;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pour un préfixe de clé Jira : les commits, MR et branches qui le citent, et ses tickets. */
final class Mentions {

  private int commits;
  private int mrs;
  private int branches;
  private final Set<String> tickets = new LinkedHashSet<>();

  int commits() {
    return commits;
  }

  int mrs() {
    return mrs;
  }

  int branches() {
    return branches;
  }

  Set<String> tickets() {
    return Collections.unmodifiableSet(tickets);
  }

  /** Ce qui classe les préfixes : le code et sa revue, pas les noms de branches. */
  int weight() {
    return commits + mrs;
  }

  void commit() {
    commits++;
  }

  void mr() {
    mrs++;
  }

  void branch() {
    branches++;
  }

  void ticket(String key) {
    tickets.add(key);
  }
}
