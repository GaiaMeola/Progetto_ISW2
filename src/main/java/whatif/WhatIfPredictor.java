package whatif;

import util.Configuration;
import util.ProjectType;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveType;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WhatIfPredictor {

    private static final String OLD_ATTRIBUTE = "Old_Bugginess";

    private WhatIfPredictor(){
        // Prevent instantation
    }

    public static void runPrediction(String datasetAPath, String outputCsvPath) throws Exception {

        // 1. CARICAMENTO: Dataset A completo (con tutte le colonne e le stringhe)
        Instances datasetRaw = loadDataset(datasetAPath);
        Instances datasetA = preprocess(datasetRaw);
        datasetA = reorderBugginessValues(datasetA);

        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            datasetA = filterActionableFeatures(datasetA);
        }

        // Questo garantisce che B+ e C abbiano lo STESSO IDENTICO header di A
        Instances datasetBplus = new Instances(datasetA, 0);
        Instances datasetC = new Instances(datasetA, 0);

        // Usiamo una logica di partizionamento basata sullo stato originale
        // Nota: devi assicurarti che l'indice di "Number of Smells" sia corretto nel datasetRaw
        int smellIdx = datasetRaw.attribute("Number of Smells").index();
        for (int i = 0; i < datasetA.numInstances(); i++) {
            // Se nel dataset originale la riga i-esima aveva smells > 0
            if (datasetRaw.instance(i).value(smellIdx) > 0) {
                datasetBplus.add(datasetA.instance(i)); // Qui finiscono quelli CON smells
            } else {
                datasetC.add(datasetA.instance(i));    // Qui finiscono quelli SENZA smells
            }
        }

        // 4. CREAZIONE DATASET B (What-If)
        Instances datasetB = new Instances(datasetBplus);
        int smellIdxA = datasetB.attribute("Number of Smells").index();

        for (int i = 0; i < datasetB.numInstances(); i++) {
            Instance inst = datasetB.instance(i);
            double valueBefore = inst.value(smellIdxA);
            inst.setValue(smellIdxA, 0);

            // DEBUG: Verifica che il valore sia effettivamente cambiato e che l'indice sia giusto
            if (i == 0) {
                System.out.println("DEBUG ATTRIBUTO: " + datasetB.attribute(smellIdxA).name());
                System.out.println("DEBUG VALORE: Prima=" + valueBefore + " Dopo=" + inst.value(smellIdxA));
            }
        }

        // 5. TRAINING (Usiamo una copia per lo SMOTE, lasciando datasetA intatto per il test)
        Instances datasetTrain = new Instances(datasetA);
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            for (int i = 0; i < datasetTrain.numInstances(); i++) {
                if (datasetTrain.instance(i).value(smellIdxA) > 0) {
                    datasetTrain.instance(i).setWeight(20.0); // Peso 20x
                }
            }
            // Nota: applySMOTE è a 0% o rimosso per evitare conflitti con i pesi
        }

        if (Configuration.SELECTED_PROJECT == ProjectType.OPENJPA) {
            datasetTrain = downsample(datasetTrain);
        } else if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            datasetTrain = applySMOTE(datasetTrain);
        }

        // 6. COSTRUZIONE MODELLO
        Classifier model = buildClassifier(datasetTrain);

        // 7. PREDIZIONI (Ora la precisione è garantita perché B+, B e C sono "figli" di A)
        PredictionSummary summaryA = predict("A", datasetA, model);
        PredictionSummary summaryBplus = predict("B+", datasetBplus, model);
        PredictionSummary summaryB = predict("B", datasetB, model);
        PredictionSummary summaryC = predict("C", datasetC, model);

        // VERIFICA IN CONSOLE
        System.out.println("--- CHECK MATEMATICO ---");
        System.out.println("Righe: " + datasetA.numInstances() + " = " + (datasetBplus.numInstances() + datasetC.numInstances()));
        System.out.println("Predizioni E: " + summaryA.predictedBuggy + " = " + (summaryBplus.predictedBuggy + summaryC.predictedBuggy));

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

    private static Instances preprocess(Instances data) throws Exception {

        // 1. RIMUOVI ATTRIBUTI STRINGA (Project e Method)
        RemoveType removeStrings = new RemoveType();
        removeStrings.setOptions(new String[]{"-T", "string"}); // -T string specifica il tipo da rimuovere
        removeStrings.setInputFormat(data);
        data = Filter.useFilter(data, removeStrings);

        // 2. RIMUOVI releaseID (se presente)
        // Nota: Se releaseID era nominale, è rimasto. Se era stringa, è già sparito sopra.
        if (data.attribute("releaseID") != null || data.attribute("ReleaseID") != null) {
            Attribute relAttr = data.attribute("releaseID") != null ?
                    data.attribute("releaseID") : data.attribute("ReleaseID");
            Remove remove = new Remove();
            remove.setAttributeIndices("" + (relAttr.index() + 1));
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        // 3. SELEZIONE DELLE FEATURE (Ora funzionerà perché ci sono solo numeri e la classe)
        AttributeSelection fs = new AttributeSelection();
        InfoGainAttributeEval eval = new InfoGainAttributeEval();
        Ranker search = new Ranker();
        search.setThreshold(0.00);

        fs.setEvaluator(eval);
        fs.setSearch(search);
        fs.setInputFormat(data);
        data = Filter.useFilter(data, fs);

        return data;
    }

    // Applica SMOTE per riequilibrare le classi
    private static Instances applySMOTE(Instances data) throws Exception {
        SMOTE smote = new SMOTE();
        smote.setInputFormat(data);
        smote.setPercentage(00.0);
        return Filter.useFilter(data, smote);
    }

    // Campionamento semplice delle istanze (per limitare dimensioni)
    private static Instances downsample(Instances data) {
        if (data.size() <= 40000) return data;

        // Shuffle con seed fisso per riproducibilità
        data.randomize(new java.util.Random(42));  // NOSONAR: uso intenzionale e sicuro per riproducibilità esperimenti ML

        // Estrae casualmente le prime N istanze
        return new Instances(data, 0, 40000);
    }


    // Costruisce il classificatore (RandomForest o IBk) in base al progetto.
    private static Classifier buildClassifier(Instances trainData) throws Exception {
        RandomForest rf = new RandomForest();
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            // Aumentiamo il numero di alberi per dare stabilità
            rf.setNumIterations(200);
            rf.setNumFeatures(0);     // Forza l'uso di tutte le feature in ogni split se necessario
        } else {
            String[] options = Utils.splitOptions("-I 30 -depth 12 -K 0 -S 1 -num-slots 1 -M 50");
            rf.setOptions(options);
            rf.setBagSizePercent(50);
        }
        rf.buildClassifier(trainData);
        return rf;
    }

    // Applica una predizione su un dataset e conta i veri/predetti buggy
    private static PredictionSummary predict(String name, Instances data, Classifier model) throws Exception {
        int actualBuggy = 0;
        int predictedBuggy = 0;

        // Recuperiamo l'indice del valore "yes" dinamicamente
        Attribute classAttr = data.classAttribute();

        for (Instance instance : data) {
            // Valore reale
            if (instance.stringValue(classAttr).equalsIgnoreCase("yes")) {
                actualBuggy++;
            }

            // Valore predetto
            double predictionIndex = model.classifyInstance(instance);
            String predictionLabel = classAttr.value((int) predictionIndex);

            if (predictionLabel.equalsIgnoreCase("yes")) {
                predictedBuggy++;
            }
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

    /**
     * Metodo di supporto per tenere solo le feature "azionabili".
     * Riducendo il numero di colonne, la Random Forest è obbligata a usare gli Smell.
     */
    private static Instances filterActionableFeatures(Instances data) throws Exception {
        List<String> actionable = Arrays.asList(
                "Number of Smells", "LOC", "CycloComplexity", "CognitiveComplexity", "Bugginess"
        );

        StringBuilder indices = new StringBuilder();
        for (int i = 0; i < data.numAttributes(); i++) {
            String name = data.attribute(i).name();
            if (actionable.contains(name)) {
                if (!indices.isEmpty()) indices.append(",");
                indices.append(i + 1);
            }
        }

        weka.filters.unsupervised.attribute.Remove remove = new weka.filters.unsupervised.attribute.Remove();
        remove.setAttributeIndices(indices.toString());
        remove.setInvertSelection(true);
        remove.setInputFormat(data);
        return Filter.useFilter(data, remove);
    }

}

