package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils.DataSource;

import java.util.Random;
import java.util.logging.Level;

import static ml.evaluation.CrossValidator.evaluateAndWrap;

public class GenericSamplerEvaluation {

    private static final int SAMPLE_SIZE = 40000;

    public static void main(String[] args) {
        try {
            // --- CONFIGURAZIONE MANUALE ---
            // Imposta a true per bilanciare (Kappa alto), false per distribuzione reale (Kappa 0.2-0.4)
            boolean useUnderSampling = false;
            boolean applyFeatureSelection = false;
            // ------------------------------

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            if (!project.equals("openjpa")) {
                Configuration.logger.severe("Questa classe è progettata solo per OpenJPA.");
                return;
            }

            DataSource source = new DataSource("csv_output/" + project + "_output.arff");
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) data.setClassIndex(data.numAttributes() - 1);

            String[] classifiersToTest = {"ibk", "naivebayes", "randomforest"};

            for (String name : classifiersToTest) {
                String strategyLog = useUnderSampling ? "CON Under-sampling" : "SENZA Under-sampling";
                Configuration.logger.info("\n>>> AVVIO: " + name + " su OpenJPA " + strategyLog);

                // Selezione del sample iniziale (20.000 istanze)
                int sampleSize = Math.min(SAMPLE_SIZE, data.numInstances());
                Instances currentData = new Instances(data, 0, sampleSize);

                // LOGICA DI BILANCIAMENTO CONDIZIONALE
                if (useUnderSampling) {
                    currentData = balanceDataset(currentData);
                    Configuration.logger.info("Bilanciamento applicato. Istanze finali: " + currentData.numInstances());
                } else {
                    Configuration.logger.info("Esecuzione su dati originali. Istanze finali: " + currentData.numInstances());
                }

                Classifier classifier = createClassifier(name);

                // Il runName riflette la scelta per distinguere i file di output
                String runName = String.format("%s_FS=%s_US=%s", name, applyFeatureSelection, useUnderSampling);

                // ESECUZIONE 10-times 10-fold Cross Validation
                // SMOTE è impostato a false perché o usiamo l'Under-sampling (US=true) o nulla
                EvaluationResult result = evaluateAndWrap(runName, classifier, currentData, 10, 10, applyFeatureSelection, false);

                EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);
                Configuration.logger.info(">>> COMPLETATO: " + name);
            }

            Configuration.logger.info("ESECUZIONE TERMINATA. Risultati salvati.");

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore critico nel main", e);
        }
    }

    private static Instances balanceDataset(Instances sample) {
        Instances positives = new Instances(sample, 0);
        Instances negatives = new Instances(sample, 0);

        for (int i = 0; i < sample.numInstances(); i++) {
            Instance instance = sample.instance(i);
            if ((int) instance.classValue() == 1) {
                positives.add(instance);
            } else {
                negatives.add(instance);
            }
        }

        Random rand = new Random(42);
        negatives.randomize(rand);

        int limit = Math.min(positives.numInstances(), negatives.numInstances());
        Instances balanced = new Instances(positives);
        for (int i = 0; i < limit; i++) {
            balanced.add(negatives.instance(i));
        }

        return balanced;
    }

    private static Classifier createClassifier(String name) throws Exception {
        switch (name.toLowerCase()) {
            case "ibk":
                return new IBk(3);
            case "naivebayes":
                return new NaiveBayes();
            case "randomforest":
                RandomForest rf = new RandomForest();
                // Parametri ottimizzati per stabilità e velocità
                rf.setOptions(Utils.splitOptions("-I 30 -depth 12 -M 50 -K 0 -S 1 -num-slots 1"));
                rf.setBagSizePercent(40);
                return rf;
            default:
                throw new IllegalArgumentException("Classificatore non supportato: " + name);
        }
    }
}