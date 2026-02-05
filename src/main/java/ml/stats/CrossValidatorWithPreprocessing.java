package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.File;

import static ml.evaluation.CrossValidator.evaluateAndWrap;

public class CrossValidatorWithPreprocessing {

    private CrossValidatorWithPreprocessing() {
        // Prevent instantation
    }


    public static void main(String[] args) {

        // 1. Zittisce i logger JNI e Netlib (quelli che vedi in console)
        java.util.logging.Logger.getLogger("com.github.fommil.jni").setLevel(java.util.logging.Level.OFF);
        java.util.logging.Logger.getLogger("com.github.fommil.netlib").setLevel(java.util.logging.Level.OFF);

        // 2. Opzionale: Zittisce anche i log di sistema standard per queste librerie
        System.setProperty("com.github.fommil.netlib.NativeSystemBLAS.loglevel", "OFF");
        System.setProperty("com.github.fommil.netlib.NativeSystemLAPACK.loglevel", "OFF");
        System.setProperty("com.github.fommil.netlib.NativeSystemARPACK.loglevel", "OFF");

        try {
            File directory = new File("ml_results");
            if (!directory.exists()) directory.mkdirs();

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            String inputPath = "csv_output/" + project + "_output.arff";

            System.out.println("Caricamento dataset: " + inputPath);
            DataSource source = new DataSource(inputPath);
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // --- CONFIGURAZIONE STEP 2 (FALESSI PDF) ---
            boolean applyFS = false;
            boolean applySMOTE = false;
            int iterations = 10; // "10 times"
            int folds = 10;      // "10-folds"

            Classifier[] classifiers = new Classifier[]{
                    ml.evaluation.ClassifierFactory.getNaiveBayes(),
                    ml.evaluation.ClassifierFactory.getRandomForest(),
                    ml.evaluation.ClassifierFactory.getIBk()
            };

            for (Classifier cls : classifiers) {
                String classifierName = cls.getClass().getSimpleName();
                String runName = String.format("%s_FS=%s_SMOTE=%s", classifierName, applyFS, applySMOTE);

                System.out.println("\n-------------------------------------------");
                System.out.println("AVVIO: " + runName);
                System.out.println("Configurazione: " + iterations + "x" + folds + " cross-validation");

                // Chiamata al valutatore
                EvaluationResult result = evaluateAndWrap(runName, cls, data, iterations, folds, applyFS, applySMOTE);

                // Scrittura immediata dei risultati medi
                EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);

                System.out.println("COMPLETATO: " + classifierName);
            }

            System.out.println("\nESECUZIONE TERMINATA CON SUCCESSO!");
            System.out.println("I risultati finali sono in: ml_results/" + project + "_summary_results.csv");

        } catch (Exception e) {
            System.err.println("ERRORE CRITICO: " + e.getMessage());
        }
    }
}
