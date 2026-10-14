///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 26
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.2
//DEPS com.opencsv:opencsv:5.9
//DEPS info.picocli:picocli:4.7.6
//DEPS org.yaml:snakeyaml:2.2
//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/cli/ProjectMapper.java
//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/console/ConsoleOut.java
//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/csv/Csv.java
//SOURCES src/main/java/ch/ge/edg/sonar/gitlab/audit/naming/NameMatcher.java

// Entry point for `jbang ProjectMapper.java`; the code is in src/main/java/ch/ge/edg/sonar/gitlab/audit/cli/ProjectMapper.java.
public class ProjectMapper {
    public static void main(String[] args) {
        ch.ge.edg.sonar.gitlab.audit.cli.ProjectMapper.main(args);
    }
}
