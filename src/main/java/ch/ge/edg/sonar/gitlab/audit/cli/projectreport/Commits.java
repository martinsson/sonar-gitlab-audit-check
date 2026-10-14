package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.util.List;
import org.jspecify.annotations.Nullable;

// Agrégats internes, passés à la section « Constats ».

/** Les commits de la fenêtre, séparés entre humains et bots. */
record Commits(List<Commit> humans, List<Commit> bots, boolean truncated, @Nullable String error) {}
