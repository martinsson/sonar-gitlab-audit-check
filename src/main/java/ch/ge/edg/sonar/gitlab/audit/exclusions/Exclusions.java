import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Une liste de projets à laisser hors de l'audit, tenue dans un fichier texte.
 *
 * Une entrée par ligne, comparée au chemin GitLab ({@code groupe/projet}) côté
 * GitLab, à la clé côté Sonar. {@code *} couvre un segment, {@code **} tout
 * le reste, sans distinction de casse. Ce qui suit {@code #} est un commentaire :
 * y écrire pourquoi, c'est ce qu'on se demandera dans six mois.
 *
 * <pre>
 *   equipe-x/bac-a-sable        # prototype jetable
 *   formation/**                # dépôts d'exercices
 *   *-archive
 * </pre>
 *
 * Sans --exclusions, {@code exclusions.txt} du répertoire courant est lu s'il
 * existe : la liste se tient une fois et vaut pour tous les outils.
 */
record Exclusions(Path file, List<String> entries, List<Pattern> patterns) {

    static final Path DEFAULT = Path.of("exclusions.txt");

    static Exclusions none() {
        return new Exclusions(null, List.of(), List.of());
    }

    /** Le fichier donné, ou exclusions.txt s'il est là, ou rien. */
    static Exclusions load(Path explicit) throws IOException {
        Path file = explicit != null ? explicit : Files.exists(DEFAULT) ? DEFAULT : null;
        if (file == null) return none();
        List<String> entries = new ArrayList<>();
        List<Pattern> patterns = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int hash = line.indexOf('#');
            String entry = (hash >= 0 ? line.substring(0, hash) : line).trim();
            if (entry.isEmpty()) continue;
            entries.add(entry);
            patterns.add(glob(entry));
        }
        return new Exclusions(file, entries, patterns);
    }

    boolean isEmpty() { return patterns.isEmpty(); }

    /** L'entrée qui exclut cet identifiant, ou null. */
    String match(String id) {
        if (id == null) return null;
        for (int i = 0; i < patterns.size(); i++) {
            if (patterns.get(i).matcher(id).matches()) return entries.get(i);
        }
        return null;
    }

    static Pattern glob(String g) {
        StringBuilder re = new StringBuilder();
        for (int i = 0; i < g.length(); i++) {
            char ch = g.charAt(i);
            if (ch == '*' && i + 1 < g.length() && g.charAt(i + 1) == '*') { re.append(".*"); i++; }
            else if (ch == '*') re.append("[^/]*");
            else if (ch == '?') re.append("[^/]");
            else re.append(Pattern.quote(String.valueOf(ch)));
        }
        return Pattern.compile(re.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }
}
