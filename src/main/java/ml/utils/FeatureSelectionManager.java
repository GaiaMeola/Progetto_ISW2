package ml.utils;

import weka.attributeSelection.*;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.ArffSaver;
import util.Configuration;

import java.io.File;
import java.util.logging.Level;

public class FeatureSelectionManager {

    public enum SelectionMethod {
        INFO_GAIN,
        FORWARD_SEARCH,
        BACKWARD_SEARCH
    }

    public static void main(String[] args) {
        try {
            // 1. Caricamento dati (usa il path dal tuo file Configuration)
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();

            // Impostiamo l'indice della classe (Bugginess) sull'ultimo attributo
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            Configuration.logger.info("Inizio analisi Feature Selection per progetto: " + project);

            // 2. Esecuzione dei tre metodi richiesti dal prof
            SelectionMethod[] methods = {SelectionMethod.INFO_GAIN, SelectionMethod.FORWARD_SEARCH, SelectionMethod.BACKWARD_SEARCH};

            for (SelectionMethod m : methods) {
                Instances reducedData = applyFeatureSelection(data, m);

                // Stampiamo i risultati a video per il report
                printSelectedFeatures(reducedData, m);

                // 3. Salvataggio ARFF ridotto per ogni metodo
                saveArff(reducedData, "csv_output/reduced_" + m.toString().toLowerCase() + "_" + project + ".arff");
            }

            Configuration.logger.info("Tutti i file ARFF ridotti sono stati generati correttamente.");

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante la feature selection", e);
        }
    }

    public static Instances applyFeatureSelection(Instances data, SelectionMethod method) throws Exception {
        // 1. Trova dinamicamente gli indici di Method e ReleaseID
        StringBuilder indicesToRemove = new StringBuilder();
        for (int i = 0; i < data.numAttributes(); i++) {
            String name = data.attribute(i).name().toLowerCase();
            if (name.equals("method") || name.equals("releaseid")) {
                if (!indicesToRemove.isEmpty()) indicesToRemove.append(",");
                indicesToRemove.append(i + 1); // Weka usa indici base 1
            }
        }

        // 2. Applica il filtro Remove
        Instances cleanData = data;
        if (!indicesToRemove.isEmpty()) {
            Remove remove = new Remove();
            remove.setAttributeIndices(indicesToRemove.toString());
            remove.setInputFormat(data);
            cleanData = Filter.useFilter(data, remove);
            Configuration.logger.info("Rimosse colonne non-actionable agli indici: " + indicesToRemove);
        }

        AttributeSelection selector = new AttributeSelection();

        switch (method) {
            case INFO_GAIN:
                InfoGainAttributeEval evalGain = new InfoGainAttributeEval();
                Ranker ranker = new Ranker();
                ranker.setThreshold(0.01);
                selector.setEvaluator(evalGain);
                selector.setSearch(ranker);
                break;
            case FORWARD_SEARCH:
                WrapperSubsetEval evalForward = new WrapperSubsetEval();
                evalForward.setClassifier(new NaiveBayes());
                BestFirst searchForward = new BestFirst();
                searchForward.setDirection(new weka.core.SelectedTag(1, BestFirst.TAGS_SELECTION));
                selector.setEvaluator(evalForward);
                selector.setSearch(searchForward);
                break;
            case BACKWARD_SEARCH:
                WrapperSubsetEval evalBackward = new WrapperSubsetEval();
                evalBackward.setClassifier(new NaiveBayes());
                BestFirst searchBackward = new BestFirst();
                searchBackward.setDirection(new weka.core.SelectedTag(0, BestFirst.TAGS_SELECTION));
                selector.setEvaluator(evalBackward);
                selector.setSearch(searchBackward);
                break;
        }

        selector.SelectAttributes(cleanData);
        return selector.reduceDimensionality(cleanData);
    }

    private static void printSelectedFeatures(Instances data, SelectionMethod m) {
        System.out.println("\n--- Feature Selezionate con " + m + " (" + (data.numAttributes()-1) + ") ---");
        for (int i = 0; i < data.numAttributes() - 1; i++) {
            System.out.println("- " + data.attribute(i).name());
        }
    }

    private static void saveArff(Instances data, String path) throws Exception {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(path));
        saver.writeBatch();
    }
}