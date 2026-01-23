package analyzer.csv;

import analyzer.model.Commit;
import analyzer.model.Release;
import util.Configuration;

import java.io.File; // <--- Aggiunto
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvDebugWriter {

    private CsvDebugWriter() {}

    public static void writeCommitCsv(String path, List<Commit> commits) {
        ensureDirectoryExists(path); // <--- Controllo cartella
        try (FileWriter fw = new FileWriter(path)) {
            fw.write(String.format("CommitID;Author;Date;Message%n")); // <--- String.format
            for (Commit c : commits) {
                fw.write(String.format("%s;%s;%s;%s%n",
                        c.getId(),
                        c.getAuthor(),
                        c.getDate(),
                        c.getMessage().replace(";", " ")));
            }
        } catch (IOException e) {
            Configuration.logger.severe("Errore scrittura CSV commit: " + e.getMessage());
        }
    }

    public static void writeReleaseCsv(String path, List<Release> all, List<Release> selected) {
        ensureDirectoryExists(path); // <--- Controllo cartella
        try (FileWriter fw = new FileWriter(path)) {
            fw.write(String.format("Index;Version ID;Version Name;Release Date;Selected%n"));
            int i = 1;
            for (Release r : all) {
                fw.write(String.format("%d;%s;%s;%s;%s%n",
                        i++,
                        r.getId(),
                        r.getName(),
                        r.getReleaseDate(),
                        selected.contains(r) ? "Y" : "N"));
            }
        } catch (IOException e) {
            Configuration.logger.severe("Errore scrittura CSV release: " + e.getMessage());
        }
    }

    // Metodo di supporto per creare le cartelle se mancano
    private static void ensureDirectoryExists(String path) {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}