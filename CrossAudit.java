///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//DEPS org.yaml:snakeyaml:2.2
//DEPS org.jspecify:jspecify:1.0.0
//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/**/*.java

// Entry point for `jbang CrossAudit.java`; the code is in src/main/java/ch/ge/edg/sonar/gitlab/audit/cli/cross/CrossAudit.java.
public class CrossAudit {
    public static void main(String[] args) {
        ch.ge.edg.sonar.gitlab.audit.cli.cross.CrossAudit.main(args);
    }
}
