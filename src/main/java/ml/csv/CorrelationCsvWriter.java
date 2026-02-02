package ml.csv;

import util.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;
import java.util.logging.Level;

public class CorrelationCsvWriter {

    private CorrelationCsvWriter(){
        // Prevent instantiation
    }

    private static final String HEADER = "Feature,Spearman,P-Value,Correlazione";

    public static void writeCorrelation(String feature, double spearman, double pValue) {
        try {
            File file = new File(Configuration.getCorrelationCsvPath());
            boolean writeHeader = !file.exists();

            // FileWriter in modalità append (true)
            try (FileWriter fw = new FileWriter(file, true)) {

                if (writeHeader) {
                    fw.write(HEADER + "\n");
                }

                String direction;
                if (spearman > 0) {
                    direction = "positiva";
                } else if (spearman < 0) {
                    direction = "negativa";
                } else {
                    direction = "nessuna";
                }

                // Locale.US garantisce il punto decimale (0.1234 invece di 0,1234)
                // Questo evita lo slittamento delle colonne nel CSV
                fw.write(String.format(Locale.US, "%s,%.4f,%.12f,%s%n",
                        feature, spearman, pValue, direction));
            }

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("Correlazione scritta: " + feature + " -> rho=" + spearman + ", p=" + pValue);
            }

        } catch (IOException e) {
            Configuration.logger.log(Level.SEVERE, "Errore nella scrittura del file di correlazione", e);
        }
    }
}