package ch.ge.edg.sonar.gitlab.audit.cli.activity;

/** Un projet sélectionné, et les pratiques que la passe profonde y a mesurées. */
record Assessed(AuditedProject audited, Prat practices) {}
