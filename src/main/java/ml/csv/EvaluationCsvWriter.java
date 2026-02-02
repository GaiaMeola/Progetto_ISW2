package ml.csv;

import ml.model.EvaluationResult;
import util.Configuration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale; // Import necessario per Locale.US

public class EvaluationCsvWriter {

    private EvaluationCsvWriter(){
        // Prevent instantiation
    }

    public static void write(String projectName, EvaluationResult result) {
        String outputFile = "ml_results/" + projectName.toLowerCase() + "_summary_results.csv";
        boolean fileExists = new File(outputFile).exists();

        try (FileWriter writer = new FileWriter(outputFile, true)) {
            if (!fileExists) {
                writer.write("Classifier,FeatureSelection,SMOTE,Precision,Recall,AUC,Kappa,NPofB20\n");
            }

            String[] tokens = result.getClassifierName().split("_");
            String classifier = tokens[0];
            String fs = tokens[1].split("=")[1];
            String smote = tokens[2].split("=")[1];

            // Forziamo Locale.US per usare il punto '.' invece della virgola ','
            writer.write(String.format(
                    Locale.US,
                    "%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                    classifier, fs, smote,
                    result.getPrecision(),
                    result.getRecall(),
                    result.getAuc(),
                    result.getKappa(),
                    result.getNpofb20()
            ));
        } catch (IOException e) {
            Configuration.logger.severe("Errore durante la scrittura del CSV di riepilogo: " + e.getMessage());
        }
    }
}