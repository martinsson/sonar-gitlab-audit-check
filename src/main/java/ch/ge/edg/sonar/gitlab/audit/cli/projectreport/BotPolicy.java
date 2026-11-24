package ch.ge.edg.sonar.gitlab.audit.cli.projectreport;

import java.util.regex.Pattern;

/** Qui est un bot : écarté des contributeurs, sauf si l'auditeur demande à les compter. */
record BotPolicy(boolean includeBots, Pattern pattern) {}
