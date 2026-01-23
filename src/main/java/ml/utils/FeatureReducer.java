package ml.utils;

import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.RemoveUseless;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.ArffSaver;
import util.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.util.logging.Level;

public class FeatureReducer {

    public static Instances reduceFeatures(Instances data, String projectName) throws Exception {
        Configuration.logger.info("== Inizio Feature Selection ==");

        // Step 1: Rimuove attributi costanti o quasi
        RemoveUseless remove = new RemoveUseless();
        remove.setInputFormat(data);
        Instances noUseless = Filter.useFilter(data, remove);
        Configuration.logger.info("Rimossi attributi inutili: da " + data.numAttributes() + " a " + noUseless.numAttributes());

        // Step 2: Gain di Informazione + Ranker
        AttributeSelection selector = new AttributeSelection();
        InfoGainAttributeEval eval = new InfoGainAttributeEval();
        Ranker search = new Ranker();
        search.setThreshold(0.01);

        selector.setEvaluator(eval);
        selector.setSearch(search);
        selector.SelectAttributes(noUseless);

        Instances reduced = selector.reduceDimensionality(noUseless);

        // Step 3: Salva le feature selezionate in un file
        int[] selectedIndices = selector.selectedAttributes();
        File outputFile = new File("ml_results/features_selected_" + projectName.toLowerCase() + ".txt");
        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write("Feature selezionate (" + selectedIndices.length + "):\n");
            for (int index : selectedIndices) {
                String name = noUseless.attribute(index).name();
                writer.write(" - " + name + "\n");
            }
        }
        Configuration.logger.info("Feature selezionate salvate in: " + outputFile.getPath());

        Configuration.logger.info("Attributi finali selezionati: " + reduced.numAttributes());
        return reduced;
    }

    public static void main(String[] args) {
        try {
            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();  // bookkeeper o openjpa

            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            Instances reduced = reduceFeatures(data, project);
            Configuration.logger.info("Feature selection completata con successo.");

            // Salva ARFF finale
            String outPath = "csv_output/reduced_features_" + project + ".arff";
            ArffSaver saver = new ArffSaver();
            saver.setInstances(reduced);
            saver.setFile(new File(outPath));
            saver.writeBatch();

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la feature selection", e);
        }
    }
}
