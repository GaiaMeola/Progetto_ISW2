package analyzer.csv;

import analyzer.model.TicketInfo;
import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

public class CsvTicketDebugWriter {

    private CsvTicketDebugWriter() {
        // Utility class → no instances allowed
    }

    public static void writeTicketCsv(String outputPath, Map<String, TicketInfo> tickets) {
        java.io.File file = new java.io.File(outputPath);
        java.io.File parent = file.getParentFile();

        // Crea le cartelle se non esistono (es. debug_file o csv_output)
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter fw = new FileWriter(file)) {
            fw.write("TicketID;OpeningVersion;FixVersionName;FixVersionDate;AllFixVersions;AffectedVersions\n");
            // ... resto del codice ...
        } catch (IOException e) {
            // È meglio loggare il messaggio reale dell'errore per il debug
            Configuration.logger.severe("Errore nella scrittura del file CSV dei ticket: " + e.getMessage());
        }
    }
}
