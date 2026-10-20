package ch.ge.edg.sonar.gitlab.audit.exchange;

/**
 * Un nom de colonne n'est jamais une constante de compilation : javac recopierait la chaîne chez
 * chaque lecteur et l'écrivain, et la dépendance sur {@code exchange} disparaîtrait du bytecode,
 * donc des règles d'architecture.
 */
final class Columns {

  private Columns() {}

  static String named(String name) {
    return name;
  }
}
