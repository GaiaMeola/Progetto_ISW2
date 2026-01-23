package whatif;

import util.Configuration;
import util.ProjectType;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.lazy.IBk;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;

public class WhatIfPredictor {

    private static final String OLD_ATTRIBUTE = "Old_Bugginess";

    private WhatIfPredictor(){
        // Prevent instantation
    }

    // Metodo principale per eseguire la predizione What-If
    public static void runPrediction(
            String datasetAPath,
            String bPlusPath,
            String bPath,
            String cPath,
            String outputCsvPath
    ) throws Exception {

        // Carica i dataset
        Instances datasetA = loadDataset(datasetAPath);
        Instances datasetBplus = loadDataset(bPlusPath);
        Instances datasetB = loadDataset(bPath);
        Instances datasetC = loadDataset(cPath);

        // Rimuovi releaseID e Applica Feature Selection a tutti
        datasetA = preprocess(datasetA, true);
        datasetA = reorderBugginessValues(datasetA);
        List<String> selectedAttributes = new ArrayList<>();
        for (int i = 0; i < datasetA.numAttributes() - 1; i++) {
            selectedAttributes.add(datasetA.attribute(i).name());
        }
        String classAttr = datasetA.classAttribute().name();
        datasetBplus = preprocess(datasetBplus, false);
        datasetBplus = reorderBugginessValues(datasetBplus);
        datasetB = preprocess(datasetB, false);
        datasetB = reorderBugginessValues(datasetB);
        datasetC = preprocess(datasetC, false);
        datasetC = reorderBugginessValues(datasetC);

        // Se BookKeeper, allinea le feature ai selectedAttributes
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            datasetBplus = alignFeatures(datasetBplus, selectedAttributes, classAttr);
            datasetB = alignFeatures(datasetB, selectedAttributes, classAttr);
            datasetC = alignFeatures(datasetC, selectedAttributes, classAttr);
        }

        Instances datasetNewA = datasetA;

        // Opzionale: Sampling (solo per OpenJPA)
        if (Configuration.SELECTED_PROJECT == ProjectType.OPENJPA) {
            datasetNewA = downsample(datasetA); // massimo 20.000 istanze
            Configuration.logger.info("OPENJPA: campionamento hard limit a 20.000 istanze.");
        }

        // Opzionale: SMOTE (solo per BookKeeper)
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            datasetNewA = applySMOTE(datasetA);
        }

        // Costruisci classificatore
        Classifier model = buildClassifier(datasetNewA);

        // Predizioni
        PredictionSummary summaryA = predict("A", datasetA, model);
        PredictionSummary summaryBplus = predict("B+", datasetBplus, model);
        PredictionSummary summaryB = predict("B", datasetB, model);
        PredictionSummary summaryC = predict("C", datasetC, model);

        List<PredictionSummary> results = Arrays.asList(summaryA, summaryBplus, summaryB, summaryC);
        exportSummaryToCsv(results, outputCsvPath);

    }

    // Carica un dataset Weka da file .arff
    private static Instances loadDataset(String path) throws Exception {
        Instances data = new DataSource(path).getDataSet();
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        return data;
    }

    // Allinea le feature dei dataset di test con quelle del training
    private static Instances alignFeatures(Instances data, List<String> selectedNames, String classAttr) throws Exception {
        List<Integer> indicesToKeep = new ArrayList<>();
        for (int i = 0; i < data.numAttributes(); i++) {
            String attrName = data.attribute(i).name();
            if (selectedNames.contains(attrName) || attrName.equals(classAttr)) {
                indicesToKeep.add(i);
            }
        }

        int[] indices = indicesToKeep.stream().mapToInt(i -> i).toArray();

        Remove remove = new Remove();
        remove.setInvertSelection(true);
        remove.setAttributeIndicesArray(indices);
        remove.setInputFormat(data);
        Instances filtered = Filter.useFilter(data, remove);

        // Assicura che bugginess sia la classe, anche se è stata spostata
        for (int i = 0; i < filtered.numAttributes(); i++) {
            if (filtered.attribute(i).name().equalsIgnoreCase(classAttr)) {
                filtered.setClassIndex(i);
                break;
            }
        }

        return filtered;
    }


    // Applica la selezione delle feature e rimuove releaseID se richiesto
    private static Instances preprocess(Instances data, boolean applyFS) throws Exception {
        if (data.attribute("releaseID") != null) {
            Remove remove = new Remove();
            remove.setAttributeIndices("" + (data.attribute("releaseID").index() + 1)); // 1-based
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        if (applyFS) {
            AttributeSelection fs = new AttributeSelection();
            InfoGainAttributeEval eval = new InfoGainAttributeEval();
            Ranker search = new Ranker();
            search.setThreshold(0.01);

            fs.setEvaluator(eval);
            fs.setSearch(search);
            fs.setInputFormat(data);
            data = Filter.useFilter(data, fs);
        }

        return data;
    }

    // Applica SMOTE per riequilibrare le classi
    private static Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        smote.setPercentage(50.0);
        return Filter.useFilter(data, smote);
    }

    // Campionamento semplice delle istanze (per limitare dimensioni)
    private static Instances downsample(Instances data) {
        if (data.size() <= 20000) return data;

        // Shuffle con seed fisso per riproducibilità
        data.randomize(new java.util.Random(42));  // NOSONAR: uso intenzionale e sicuro per riproducibilità esperimenti ML

        // Estrae casualmente le prime N istanze
        return new Instances(data, 0, 20000);
    }


    // Costruisce il classificatore (RandomForest o IBk) in base al progetto.
    private static Classifier buildClassifier(Instances trainData) throws Exception {
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            IBk ibk = new IBk();
            ibk.setKNN(3);
            ibk.buildClassifier(trainData);
            return ibk;
        } else {
            RandomForest rf = new RandomForest();
            String[] options = Utils.splitOptions("-I 30 -depth 12 -K 0 -S 1 -num-slots 1 -M 50");
            rf.setOptions(options);
            rf.setBagSizePercent(50);
            rf.buildClassifier(trainData);
            return rf;
        }
    }

    // Applica una predizione su un dataset e conta i veri/predetti buggy
    private static PredictionSummary predict(String name, Instances data, Classifier model) throws Exception {
        int actualBuggy = 0;
        int predictedBuggy = 0;

        for (Instance instance : data) {
            double actual = instance.classValue();
            double predicted = model.classifyInstance(instance);

            if ((int) actual == 1) actualBuggy++;
            if ((int) predicted == 1) predictedBuggy++;
        }

        return new PredictionSummary(name, actualBuggy, predictedBuggy);
    }

    // Esporta la tabella dei risultati nel formato richiesto (A,E)
    private static void exportSummaryToCsv(List<PredictionSummary> summaries, String path) {
        try (FileWriter writer = new FileWriter(path)) {
            writer.write("Dataset,A,E\n");
            for (PredictionSummary s : summaries) {
                String aValue = s.datasetName.equals("B") ? "" : String.valueOf(s.realBuggy);
                writer.write(s.datasetName + "," + aValue + "," + s.predictedBuggy + "\n");
            }
        } catch (IOException e) {
            Configuration.logger.severe("Errore durante salvataggio CSV: " + e.getMessage());
        }
    }


    // Riordina i valori della classe Bugginess
    private static Instances reorderBugginessValues(Instances data) throws Exception {
        Attribute classAttr = data.classAttribute();

        // Verifica che sia nominale
        if (!classAttr.isNominal()) {
            throw new IllegalArgumentException("Bugginess deve essere nominale");
        }

        // Se l'ordine è già corretto {no, yes}, non fare nulla
        if (classAttr.value(0).equalsIgnoreCase("no") && classAttr.value(1).equalsIgnoreCase("yes")) {
            return data;
        }

        // Rinomina il vecchio attributo per evitare conflitto
        data.renameAttribute(classAttr, OLD_ATTRIBUTE);

        // Crea nuovo attributo con ordine corretto
        ArrayList<String> newValues = new ArrayList<>();
        newValues.add("no");  // clean = 0.0
        newValues.add("yes"); // buggy = 1.0
        Attribute newClassAttr = new Attribute("Bugginess", newValues);

        // Inserisci in fondo
        data.insertAttributeAt(newClassAttr, data.numAttributes());
        int newClassIndex = data.numAttributes() - 1;

        // Copia i valori corretti
        for (int i = 0; i < data.numInstances(); i++) {
            Instance inst = data.instance(i);
            String original = inst.stringValue(data.attribute(OLD_ATTRIBUTE)).toLowerCase();
            inst.setValue(newClassIndex, original.equals("yes") ? "yes" : "no");
        }

        // Rimuovi vecchio attributo
        Remove remove = new Remove();
        remove.setAttributeIndicesArray(new int[]{data.attribute(OLD_ATTRIBUTE).index()});
        remove.setInputFormat(data);
        data = Filter.useFilter(data, remove);

        // Imposta la nuova classe
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }
}


