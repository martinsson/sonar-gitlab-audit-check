package ch.ge.edg.sonar.gitlab.audit.cli.cross;

import ch.ge.edg.sonar.gitlab.audit.console.Console;
import ch.ge.edg.sonar.gitlab.audit.csv.Csv;
import ch.ge.edg.sonar.gitlab.audit.exchange.GitlabInventory;
import ch.ge.edg.sonar.gitlab.audit.matching.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/**
 * La passe sur tout GitLab : chaque projet Sonar cherché dans l'inventaire complet, à côté de
 * pratiques.csv ou donné par --inventaire, et son CSV.
 *
 * @param inventoryCsv --inventaire, ou null pour inventaire.csv à côté de pratiques.csv
 * @param sonarGitlabCsv --sonar-gitlab, ou null pour sonar-gitlab.csv à côté de --out
 * @param out --out, à côté duquel s'écrit sonar-gitlab.csv par défaut
 */
record WidePass(
    @Nullable Path inventoryCsv,
    @Nullable Path sonarGitlabCsv,
    @Nullable Path out,
    boolean comma,
    SonarToGitlab.NameThresholds thresholds) {

  /** Chaque projet Sonar contre tout l'inventaire GitLab ; null sans inventaire. */
  SonarToGitlab.@Nullable Result run(
      Csv.Table sonar,
      Path gitlabFile,
      Result mapping,
      SonarToGitlab.Rejections rejected,
      CrossReport console)
      throws IOException {
    Csv.Table inventory = inventory(gitlabFile, console);
    if (inventory == null) return null;
    return SonarToGitlab.map(sonar, inventory, mapping.matches(), rejected, thresholds);
  }

  /** L'inventaire GitLab complet, s'il est là ; null sinon, et on dit pourquoi. */
  private Csv.@Nullable Table inventory(Path gitlabFile, CrossReport console) throws IOException {
    Path f =
        inventoryCsv != null
            ? inventoryCsv
            : gitlabFile.toAbsolutePath().resolveSibling("inventaire.csv");
    if (!Files.exists(f)) {
      if (inventoryCsv != null) throw new IOException("introuvable : " + f);
      console.noInventory();
      return null;
    }
    Csv.Table t = Csv.read(f);
    t.require(
        f, "GitlabActivityAudit (inventaire.csv)", GitlabInventory.PATH, GitlabInventory.SELECTED);
    console.inventory(t, f);
    return t;
  }

  void write(SonarToGitlab.Result wide, Path gitlabFile, CrossReport console, Console stdout)
      throws IOException {
    console.wideIntro();
    Path wideFile =
        sonarGitlabCsv != null
            ? sonarGitlabCsv
            : (out != null ? out : gitlabFile).toAbsolutePath().resolveSibling("sonar-gitlab.csv");
    SonarToGitlabCsv.write(wide, wideFile, comma);
    new SonarToGitlabReport(stdout).print(wide, wideFile);
  }
}
