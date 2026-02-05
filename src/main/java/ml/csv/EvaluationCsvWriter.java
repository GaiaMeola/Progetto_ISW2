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
        // Nota: Ho cambiato la cartella in 'ml_results/' per coerenza con lo script Python
        // che cercherà i file 'results_OPENJPA.csv'
        String outputFile = "results_" + projectName.toLowerCase() + ".csv";
        boolean fileExists = new File(outputFile).exists();

        try (FileWriter writer = new FileWriter(outputFile, true)) {
            if (!fileExists) {
                // AGGIORNATO: Aggiunte le colonne Model, Accuracy e F1
                writer.write("Model,FeatureSelection,SMOTE,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20\n");
            }

            String[] tokens = result.getClassifierName().split("_");
            String classifier = tokens[0];
            String fs = tokens[1].split("=")[1];
            String smote = tokens[2].split("=")[1];

            // AGGIORNATO: Inseriti result.getAccuracy() e result.getF1()
            writer.write(String.format(
                    Locale.US,
                    "%s,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                    classifier, fs, smote,
                    result.getAccuracy(),   // <--- CHIAMA IL NUOVO GETTER
                    result.getPrecision(),
                    result.getRecall(),
                    result.getF1(),          // <--- CHIAMA IL NUOVO GETTER
                    result.getAuc(),
                    result.getKappa(),
                    result.getNpofb20()
            ));
        } catch (IOException e) {
            Configuration.logger.severe("Errore durante la scrittura del CSV di riepilogo: " + e.getMessage());
        }
    }
}