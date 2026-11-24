package ch.ge.edg.sonar.gitlab.audit.cli.activity.model;

/** Un projet sélectionné, et les pratiques que la passe profonde y a mesurées. */
public record Assessed(AuditedProject audited, Practices practices) {}
