package ch.ge.edg.sonar.gitlab.audit.cli.jiraextract;

import java.nio.file.Path;
import java.time.ZoneId;

/**
 * Où et comment s'écrivent les fichiers d'une extraction.
 *
 * @param raw écrire aussi les tickets bruts (jsonl)
 * @param comma CSV pour un outil (virgule, sans BOM) plutôt que pour Excel
 * @param zone le fuseau des dates écrites : celui du poste, sauf en test
 */
public record Output(Path dir, boolean raw, boolean comma, ZoneId zone) {}
