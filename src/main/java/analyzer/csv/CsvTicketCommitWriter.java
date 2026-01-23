package analyzer.csv;

import analyzer.model.TicketInfo;
import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class CsvTicketCommitWriter {

    private CsvTicketCommitWriter() {
        // Utility class → no instances allowed
    }

    public static void write(String path, Map<String, TicketInfo> ticketMap) {
        // 1. Creiamo un oggetto File basato sul path di configurazione
        java.io.File targetFile = new java.io.File(path);
        java.io.File parentDir = targetFile.getParentFile();

        // 2. Creiamo le cartelle (es. debug_file) se non esistono
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        // 3. Apriamo il FileWriter usando l'oggetto targetFile appena preparato
        try (FileWriter fw = new FileWriter(targetFile)) {
            fw.write(String.format("TicketID;CommitID;JavaFileModified%n"));

            for (TicketInfo ticket : ticketMap.values()) {
                for (String commit : ticket.getCommitIds()) {
                    if (ticket.getFixedFiles().isEmpty()) {
                        fw.write(String.format("%s;%s;%s%n", ticket.getId(), commit, "(NO JAVA FILES)"));
                    } else {
                        // Qui 'file' è una Stringa, ecco perché prima andava in conflitto
                        for (String file : ticket.getFixedFiles()) {
                            fw.write(String.format("%s;%s;%s%n", ticket.getId(), commit, file));
                        }
                    }
                }
            }

        } catch (IOException e) {
            // Usiamo severe per vedere l'errore reale se qualcosa fallisce ancora
            Configuration.logger.severe("Errore nella scrittura del CSV dei commit: " + e.getMessage());
        }
    }
}
