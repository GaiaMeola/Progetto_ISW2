package ml.csv;

import util.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;

public class CorrelationCsvWriter {

    private CorrelationCsvWriter(){
        // Prevent instantation
    }

    private static final String HEADER = "Feature,Spearman,P-Value,Correlazione";

    public static void writeCorrelation(String feature, double spearman, double pValue) {
        try {
            File file = new File(Configuration.getCorrelationCsvPath());
            boolean writeHeader = !file.exists();

            FileWriter fw = new FileWriter(file, true);

            if (writeHeader) fw.write(HEADER + "\n");

            String direction;
            if (spearman > 0) {
                direction = "positiva";
            } else if (spearman < 0) {
                direction = "negativa";
            } else {
                direction = "nessuna";
            }

            fw.write(String.format("%s,%.4f,%.12f,%s%n", feature, spearman, pValue, direction));
            fw.close();

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("Correlazione scritta: " + feature + " → ρ=" + spearman + ", p=" + pValue);
            }

        } catch (IOException e) {
            Configuration.logger.log(Level.SEVERE, "Errore nella scrittura del file di correlazione", e);
        }
    }
}
