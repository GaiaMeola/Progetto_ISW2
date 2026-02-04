package ml.csv;

import ml.model.EvaluationFoldResult;
import util.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public class DetailedFoldCsvWriter {

    private DetailedFoldCsvWriter(){
        // Prevent instantiation
    }

    private static final String OUTPUT_PATH = "csv_output/fold_results.csv";

    public static void writeAll(List<EvaluationFoldResult> results) {
        File file = new File(OUTPUT_PATH);
        boolean exists = file.exists();

        // Usiamo Locale.US per evitare che i numeri diventino "0,89" (che rompe le colonne del CSV)
        try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {

            // Se il file è nuovo, scriviamo l'header
            if (!exists || file.length() == 0) {
                pw.println("Classifier,FS,SMOTE,Seed,Repeat,Fold,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20");
            }

            for (EvaluationFoldResult r : results) {
                pw.printf(Locale.US, "%s,%b,%b,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        r.getClassifierName(),
                        r.isApplyFS(),
                        r.isApplySMOTE(),
                        r.getSeed(),
                        r.getRepeat(),
                        r.getFold(),
                        r.getAccuracy(),
                        r.getPrecision(),
                        r.getRecall(),
                        r.getF1(),
                        r.getAuc(),
                        r.getKappa(),
                        r.getNpofb20());
            }
        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nella scrittura dei fold", e);
        }
    }
}