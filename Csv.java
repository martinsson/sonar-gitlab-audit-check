//JAVA 25

import com.opencsv.CSVWriter;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Écriture des CSV, partagée par les outils qui en produisent.
 *
 * Un fichier qu'Excel ouvre de travers passe pour un outil cassé, et le détour
 * par Données → À partir d'un fichier texte ne se fait jamais : on double-clique,
 * on voit des accents massacrés ou une seule colonne, on referme.
 *
 * Trois choses doivent être vraies en même temps, et il suffit d'en rater une :
 *
 *   BOM UTF-8       — sans lui, Excel lit l'UTF-8 comme de l'ANSI et « é »
 *                     devient « Ã© » sur toute la colonne.
 *   POINT-VIRGULE   — sous locale française, Excel attend le point-virgule ;
 *                     avec des virgules il verse la ligne entière en colonne A.
 *   CRLF            — fins de ligne attendues par Excel sous Windows.
 *
 * D'où le défaut : lisible par un humain. Et --comma pour l'autre public,
 * l'outil qui relira le fichier, où le BOM et le point-virgule sont au contraire
 * ce qui casse la lecture.
 */
public final class Csv {

    private Csv() { }

    private static final char BOM = (char) 0xFEFF;

    /**
     * Le {@link Writer} sous-jacent est fermé par le {@link CSVWriter} retourné :
     * un try-with-resources sur celui-ci suffit.
     */
    public static CSVWriter writer(Path path, boolean comma) throws IOException {
        Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        if (comma) return new CSVWriter(w);
        w.write(BOM);
        return new CSVWriter(w, ';', CSVWriter.DEFAULT_QUOTE_CHARACTER,
                CSVWriter.DEFAULT_ESCAPE_CHARACTER, "\r\n");
    }

    /** Ce qu'il faut dire à qui vient de recevoir un chemin de fichier. */
    public static String openingHint(Path path, boolean comma) {
        return comma
                ? "  Format machine (virgule, sans BOM)."
                : "  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start "
                        + path.getFileName() + "`. Pour un autre outil : --comma.";
    }
}
