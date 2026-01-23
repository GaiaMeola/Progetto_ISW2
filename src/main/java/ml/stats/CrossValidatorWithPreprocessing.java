package ml.stats;

import ml.csv.EvaluationCsvWriter;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import static ml.evaluation.CrossValidator.evaluateAndWrap;

public class CrossValidatorWithPreprocessing {

    private CrossValidatorWithPreprocessing() {
        // Prevent instantation
    }


    public static void main(String[] args) {
        try {
            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            String inputPath = "csv_output/" + project + "_output.arff";
            DataSource source = new DataSource(inputPath);
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Esegui una sola combinazione (modificare a seconda dei test desiderati)
            boolean applyFS = false;
            boolean applySMOTE = false;

            for (Classifier cls : new Classifier[]{
                    ml.evaluation.ClassifierFactory.getNaiveBayes(),
                    ml.evaluation.ClassifierFactory.getRandomForest(),
                    ml.evaluation.ClassifierFactory.getIBk()
            }) {
                String classifierName = cls.getClass().getSimpleName();
                String runName = String.format("%s_FS=%s_SMOTE=%s", classifierName, applyFS, applySMOTE);

                EvaluationResult result = evaluateAndWrap(runName, cls, data, 10, 10, applyFS, applySMOTE);
                EvaluationCsvWriter.write(Configuration.getProjectColumn(), result);
            }

        } catch (Exception e) {
            Configuration.logger.severe("Errore durante la valutazione con preprocessing: " + e.getMessage());
        }
    }
}
