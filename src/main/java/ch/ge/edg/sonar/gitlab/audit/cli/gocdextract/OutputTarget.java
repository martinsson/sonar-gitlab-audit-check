package ch.ge.edg.sonar.gitlab.audit.cli.gocdextract;

import java.nio.file.Path;

/**
 * Où l'extraction écrit, et sous quelle forme.
 *
 * @param dir répertoire de sortie
 * @param comma CSV pour un outil (virgule, sans BOM) plutôt que pour Excel
 * @param raw écrire aussi gocd-runs.jsonl
 */
record OutputTarget(Path dir, boolean comma, boolean raw) {}
