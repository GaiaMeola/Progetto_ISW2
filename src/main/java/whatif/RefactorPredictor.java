package whatif;

import util.Configuration;
import util.ProjectType;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.misc.InputMappedClassifier;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.converters.CSVLoader;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveType;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RefactorPredictor {

    public static void main(String[] args) {
        try {
            String project = Configuration.SELECTED_PROJECT.toString().toLowerCase();
            String trainingPath = "csv_output/" + project + "_output.arff";
            String refactoredCsvPath = "ml_results/" + project + "_AFMethod2_metrics_refactored.csv";
            String cleanedCsvPath = "ml_results/" + project + "_cleaned_temp.csv";
            String reportPath = "ml_results/verdetto_final_refactor.txt";

            System.out.println("=== STARTING REFACTOR PREDICTION: " + project.toUpperCase() + " ===");

            // 1. PULIZIA CSV DI INPUT
            cleanCsvFile(refactoredCsvPath, cleanedCsvPath);

            // 2. PREPARAZIONE TRAINING SET (ARFF)
            Instances trainRaw = new DataSource(trainingPath).getDataSet();
            if (trainRaw.classIndex() == -1) trainRaw.setClassIndex(trainRaw.numAttributes() - 1);

            // Pre-processamento: Rimuove stringhe, storiche e applica Feature Selection
            Instances trainProcessed = preprocessLikeOriginal(trainRaw);

            // Riordina Bugginess in {no, yes} (Specifico per il Training)
            prepareBugginessAttribute(trainProcessed, true);

            if (Configuration.SELECTED_PROJECT == ProjectType.OPENJPA) {
                trainProcessed = downsample(trainProcessed);
            }

            // 3. COSTRUZIONE MODELLO MAPPATO
            Classifier baseModel = buildClassifierOriginal(trainProcessed);
            InputMappedClassifier mappedModel = new InputMappedClassifier();
            mappedModel.setClassifier(baseModel);
            mappedModel.setSuppressMappingReport(true);
            mappedModel.buildClassifier(trainProcessed);

            System.out.println("Modello addestrato con Feature Selection e rimozione storiche.");

            // 4. CARICAMENTO E PREPARAZIONE TEST SET (CSV)
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(cleanedCsvPath));
            loader.setFieldSeparator(";");
            Instances testRaw = loader.getDataSet();

            // Aggiunge la colonna Bugginess fittizia (Specifico per il Test)
            prepareBugginessAttribute(testRaw, false);

            // 5. ESECUZIONE PREDIZIONI
            try (PrintWriter writer = new PrintWriter(reportPath)) {
                writer.println("=== PREDIZIONI WHAT-IF (MODELLO RIFATTORIZZATO) ===");
                writer.println("Progetto: " + project.toUpperCase());
                writer.println("Data: " + new java.util.Date());
                writer.println("--------------------------------------------------");

                // Sostituisci il ciclo for finale nel main con questo:
                for (int i = 0; i < testRaw.numInstances(); i++) {
                    Instance inst = testRaw.instance(i);

                    // Invece di classifyInstance, usiamo distributionForInstance
                    double[] distribution = mappedModel.distributionForInstance(inst);
                    double pred = mappedModel.classifyInstance(inst);
                    String label = trainProcessed.classAttribute().value((int) pred);

                    String methodName = testRaw.instance(i).stringValue(0);

                    // distribution[1] è la probabilità di "YES"
                    double probYes = distribution[1];
                    writer.printf("Metodo: %-25s | Pred: %-5s | Prob YES: %.2f%%%n", methodName, label.toUpperCase(), probYes * 100);
                }
            }

            System.out.println("Esecuzione completata! Report generato in: " + reportPath);

        } catch (Exception e) {
            System.err.println("ERRORE: " + e.getMessage());
        }
    }

    private static void cleanCsvFile(String sourcePath, String destPath) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(sourcePath));
        List<String> cleanedLines = new ArrayList<>();
        String correctHeader = "Method;LOC;CyclomaticComplexity;CognitiveComplexity;ParameterCount;NestingDepth;StatementCount;ReturnTypeComplexity;LocalVariableCount;Number of Smells";
        cleanedLines.add(correctHeader);

        for (int i = 1; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(";");
            if (parts.length >= 10) {
                cleanedLines.add(String.join(";", Arrays.copyOfRange(parts, 0, 10)));
            }
        }
        Files.write(Paths.get(destPath), cleanedLines);
    }

    private static Instances preprocessLikeOriginal(Instances data) throws Exception {
        // 1. Rimuovi Stringhe
        RemoveType removeStrings = new RemoveType();
        removeStrings.setOptions(new String[]{"-T", "string"});
        removeStrings.setInputFormat(data);
        data = Filter.useFilter(data, removeStrings);

        // 2. RIMOZIONE HARD METRICHE STORICHE (per What-If puro sul codice)
        String[] toDelete = {"releaseID", "ReleaseID", "Churn", "MethodHistories", "StmtAdded", "StmtDeleted", "DistinctAuthors"};
        for (String colName : toDelete) {
            Attribute attr = data.attribute(colName);
            if (attr != null) {
                Remove rm = new Remove();
                rm.setAttributeIndices("" + (attr.index() + 1));
                rm.setInputFormat(data);
                data = Filter.useFilter(data, rm);
            }
        }

        // 3. FEATURE SELECTION (Information Gain)
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

    private static void prepareBugginessAttribute(Instances data, boolean isTraining) {
        List<String> values = new ArrayList<>();
        values.add("no");
        values.add("yes");
        Attribute newClassAttr = new Attribute("Bugginess", values);

        if (isTraining) {
            // 1. Recuperiamo l'indice della vecchia classe
            int oldIdx = data.classIndex();
            if (oldIdx == -1) oldIdx = data.numAttributes() - 1;

            // 2. IMPORTANTE: Reset dell'indice della classe prima di modificare/eliminare
            data.setClassIndex(-1);


            data.renameAttribute(oldIdx, "Old_Bugginess");
            data.insertAttributeAt(newClassAttr, data.numAttributes());


            int updatedOldIdx = data.attribute("Old_Bugginess").index();
            int newIdx = data.numAttributes() - 1;

            for (int i = 0; i < data.numInstances(); i++) {
                String val = data.instance(i).stringValue(updatedOldIdx).toLowerCase();
                data.instance(i).setValue(newIdx, (val.contains("yes") || val.contains("true")) ? "yes" : "no");
            }

            // 4. Ora possiamo eliminare Old_Bugginess perché non è più la "class"
            data.deleteAttributeAt(updatedOldIdx);
        } else {
            // Nel Test (CSV), aggiungiamo solo se manca
            if (data.attribute("Bugginess") == null) {
                data.insertAttributeAt(newClassAttr, data.numAttributes());
            }
        }

        data.setClassIndex(data.numAttributes() - 1);
    }

    private static Instances downsample(Instances data) {
        if (data.size() <= 40000) return data;
        data.randomize(new java.util.Random(42));
        return new Instances(data, 0, 40000);
    }

    private static Classifier buildClassifierOriginal(Instances trainData) throws Exception {
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            weka.classifiers.lazy.IBk ibk = new weka.classifiers.lazy.IBk(3);
            ibk.buildClassifier(trainData);
            return ibk;
        } else {
            weka.classifiers.trees.RandomForest rf = new weka.classifiers.trees.RandomForest();
            rf.setOptions(Utils.splitOptions("-I 30 -depth 12 -M 50 -K 0 -S 1 -num-slots 1"));
            rf.setBagSizePercent(50);
            rf.buildClassifier(trainData);
            return rf;
        }
    }
}