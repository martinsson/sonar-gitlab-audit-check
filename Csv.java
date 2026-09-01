//JAVA 25

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    // ----------------------------------------------------------------------
    // Lecture
    // ----------------------------------------------------------------------

    /**
     * Relire ce que ces outils écrivent, sans savoir d'avance lequel l'a écrit.
     *
     * Les CSV de ce dépôt sortent sous deux formes : point-virgule + BOM pour
     * Excel, virgule nue pour un outil. Un fichier peut aussi avoir fait
     * l'aller-retour par Excel, qui le réécrit à sa façon. Le séparateur se
     * déduit donc de l'en-tête plutôt que du nom de l'option qui l'a produit,
     * et le BOM se retire — sans quoi la première colonne s'appelle
     * « ﻿key » et aucune recherche par nom ne la trouve.
     */
    public static Table read(Path path) throws IOException {
        String head;
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            head = r.readLine();
        }
        if (head == null) return new Table(Map.of(), List.of());
        char sep = count(head, ';') > count(head, ',') ? ';' : ',';

        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             var csv = new CSVReaderBuilder(r)
                     .withCSVParser(new CSVParserBuilder().withSeparator(sep).build())
                     .build()) {
            List<String[]> all;
            try {
                all = csv.readAll();
            } catch (CsvException e) {
                throw new IOException(path + " illisible : " + e.getMessage(), e);
            }
            if (all.isEmpty()) return new Table(Map.of(), List.of());

            String[] header = all.get(0);
            header[0] = stripBom(header[0]);
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < header.length; i++) index.put(header[i].trim(), i);

            List<Row> rows = new ArrayList<>();
            for (String[] cells : all.subList(1, all.size())) {
                if (cells.length == 0 || (cells.length == 1 && cells[0].isBlank())) continue;
                rows.add(new Row(index, cells));
            }
            return new Table(index, rows);
        }
    }

    public record Table(Map<String, Integer> index, List<Row> rows) {

        public boolean has(String column) { return index.containsKey(column); }

        /** De quoi refuser tôt un fichier qui n'est pas celui qu'on croit. */
        public void require(Path path, String what, String... columns) throws IOException {
            for (String c : columns) {
                if (!index.containsKey(c)) {
                    throw new IOException("colonne « %s » absente de %s — ce fichier vient-il bien de %s ?"
                            .formatted(c, path, what));
                }
            }
        }
    }

    /**
     * Une ligne. Les valeurs restent des chaînes : c'est ce que l'API a renvoyé,
     * et une cellule vide se distingue ainsi d'un zéro — la confusion que tout ce
     * dépôt s'emploie à ne pas commettre.
     */
    public record Row(Map<String, Integer> index, String[] cells) {

        public String str(String column) {
            Integer i = index.get(column);
            if (i == null || i >= cells.length || cells[i] == null) return "";
            return cells[i].trim();
        }

        /** {@code null} si la cellule est vide — jamais 0. */
        public Double num(String column) {
            String v = str(column);
            if (v.isEmpty()) return null;
            try {
                return Double.parseDouble(v.replace(',', '.'));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        public boolean flag(String column) {
            return "true".equalsIgnoreCase(str(column));
        }
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(x -> x == c).count();
    }

    private static String stripBom(String s) {
        return (s != null && !s.isEmpty() && s.charAt(0) == BOM) ? s.substring(1) : s;
    }

    /** Ce qu'il faut dire à qui vient de recevoir un chemin de fichier. */
    public static String openingHint(Path path, boolean comma) {
        return comma
                ? "  Format machine (virgule, sans BOM)."
                : "  Format Excel (point-virgule + BOM UTF-8) : double-clic, ou `start "
                        + path.getFileName() + "`. Pour un autre outil : --comma.";
    }
}
