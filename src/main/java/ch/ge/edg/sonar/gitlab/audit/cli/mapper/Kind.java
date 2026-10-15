package ch.ge.edg.sonar.gitlab.audit.cli.mapper;

/**
 * NAME : un nom affiché, normalisé des deux côtés comme DERIVED, mais qui n'apparie que s'il ne
 * désigne qu'un seul projet. Un nom se répète (« api », « frontend ») là où une clé ne le peut pas.
 */
public enum Kind {
  EXACT,
  DERIVED,
  LINK,
  MAPPED,
  NAME
}
