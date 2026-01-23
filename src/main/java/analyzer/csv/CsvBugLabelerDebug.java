package analyzer.csv;

import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public class CsvBugLabelerDebug {

    private CsvBugLabelerDebug() {
        // Utility class → no instances allowed
    }

    public static void writeCsv(String path, List<String[]> rows) {
        // 1. Prepariamo il file e la cartella di destinazione
        java.io.File targetFile = new java.io.File(path);
        java.io.File parentDir = targetFile.getParentFile();

        // 2. Creiamo la cartella se non esiste
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 3. Proviamo a scrivere il file
        try (FileWriter fw = new FileWriter(targetFile)) {
            fw.write("TicketID;CommitHash;Method;Release\n");
            for (String[] row : rows) {
                fw.write(String.join(";", row) + "\n");
            }
        } catch (IOException e) {
            Configuration.logger.log(Level.SEVERE, "Errore scrivendo il debug CSV dei metodi buggy: " + e.getMessage(), e);
        }
    }
}
