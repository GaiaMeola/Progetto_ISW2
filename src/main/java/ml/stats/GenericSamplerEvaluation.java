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

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Random;
import java.util.logging.Level;

import static ml.evaluation.CrossValidator.evaluateAndWrap;

public class GenericSamplerEvaluation {

    private static final int SAMPLE_SIZE = 20000;

    public static void main(String[] args) {
        try {
            String classifierName = args.length > 0 ? args[0] : "IBk";
            boolean applyFeatureSelection = false;
            boolean applySmote = true;

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            if (!project.equals("openjpa")) {
                Configuration.logger.severe("Questa classe è progettata solo per OpenJPA.");
                return;
            }

            DataSource source = new DataSource("csv_output/" + project + "_output.arff");
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            int sampleSize = Math.min(SAMPLE_SIZE, data.numInstances());
            Instances sample = new Instances(data, 0, sampleSize);
            Configuration.logger.info("Dataset campionato temporalmente (" + sample.numInstances() + " istanze)");

            if (applySmote) {
                sample = balanceDataset(sample);
            }

            Classifier classifier = createClassifier(classifierName);
            String runName = String.format("%s_FS=%s_SMOTE=%s", classifier.getClass().getSimpleName(), applyFeatureSelection, applySmote);

            // Scrivi intestazione se necessario
            try (PrintWriter pw = new PrintWriter(new FileWriter("csv_output/fold_results_openjpa.csv"))) {
                pw.println("Classifier,FS,SMOTE,Seed,Repeat,Fold,Accuracy,Precision,Recall,F1,AUC,Kappa,NPofB20");
            }

            EvaluationResult result = evaluateAndWrap(runName, classifier, sample, 10, 10, applyFeatureSelection, applySmote);
            EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la valutazione campionata", e);
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

        Random rand = new Random(42); // NOSONAR: riproducibilità esperimenti
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
            case "naive":
                return new NaiveBayes();
            case "randomforest":
                RandomForest rf = new RandomForest();
                String[] options = Utils.splitOptions("-I 30 -depth 12 -M 50 -K 0 -S 1 -num-slots 1");
                rf.setOptions(options);
                rf.setBagSizePercent(40);
                Configuration.logger.info("RandomForest configurato con 30 alberi, profondità max 12");
                return rf;
            default:
                throw new IllegalArgumentException("Classificatore non riconosciuto: " + name);
        }
    }
}
