package ch.ge.edg.sonar.gitlab.audit.cli.activity;

import ch.ge.edg.sonar.gitlab.audit.gitlab.Gitlab;
import java.io.PrintStream;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * Ce que chaque étape partage : l'instance, l'écran, les réglages de l'entonnoir, le début de la
 * fenêtre, le motif des robots et les effectifs publiés en synthèse.
 */
record AuditContext(
    Gitlab gl,
    Console con,
    FunnelOptions funnel,
    OffsetDateTime windowStart,
    Pattern bots,
    Counters counters) {

  PrintStream out() {
    return con.out();
  }

  int sinceDays() {
    return funnel.sinceDays;
  }

  int concurrency() {
    return funnel.concurrency;
  }
}
