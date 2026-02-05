package ml.evaluation;

import ml.csv.DetailedFoldCsvWriter;
import ml.model.EvaluationFoldResult;
import ml.model.EvaluationResult;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CrossValidator {

    private CrossValidator() {}

    private static final int SEED = 42;

    public static EvaluationResult evaluateAndWrap(String name, Classifier cls, Instances data,
                                                   int folds, int repeats,
                                                   boolean applyFeatureSelection,
                                                   boolean applySmote) throws Exception {

        // 1. Pulizia Globale: Rimuovi Project, Method e ReleaseID
        // È fondamentale che il modello non veda mai questi attributi
        String[] attributesToRemove = {"Project", "Method", "ReleaseID"};
        List<Integer> indices = new ArrayList<>();
        for (String attrName : attributesToRemove) {
            if (data.attribute(attrName) != null) {
                indices.add(data.attribute(attrName).index());
            }
        }

        if (!indices.isEmpty()) {
            Remove remove = new Remove();
            int[] arrIndices = indices.stream().mapToInt(j -> j).toArray();
            remove.setAttributeIndicesArray(arrIndices);
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        double sumAccuracy = 0,  sumPrecision = 0, sumRecall = 0, sumF1 = 0, sumAUC = 0, sumKappa = 0, sumNP = 0;
        int totalIterations = repeats * folds;

        // 10 times...
        for (int i = 0; i < repeats; i++) {
            // Mescoliamo i dati per ogni ripetizione
            Instances randomizedData = new Instances(data);
            randomizedData.randomize(new Random(SEED + i));
            if (randomizedData.classAttribute().isNominal()) {
                randomizedData.stratify(folds);
            }

            List<EvaluationFoldResult> foldResults = new ArrayList<>();

            // ...10-fold Cross Validation
            for (int n = 0; n < folds; n++) {
                Instances train = randomizedData.trainCV(folds, n);
                Instances test = randomizedData.testCV(folds, n);

                // --- PREPROCESSING (SOLO SUL TRAIN) ---

                // Feature Selection
                if (applyFeatureSelection) {
                    AttributeSelection selector = new AttributeSelection();
                    InfoGainAttributeEval eval = new InfoGainAttributeEval();
                    Ranker ranker = new Ranker();
                    ranker.setThreshold(0.01);
                    selector.setEvaluator(eval);
                    selector.setSearch(ranker);
                    selector.SelectAttributes(train);
                    train = selector.reduceDimensionality(train);
                    test = selector.reduceDimensionality(test); // Applica la stessa selezione al test
                }

                // SMOTE
                if (applySmote) {
                    SMOTE smote = new SMOTE();
                    smote.setInputFormat(train);
                    train = Filter.useFilter(train, smote);
                }

                // --- EVALUATION ---
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);

                Evaluation foldEval = new Evaluation(train);
                foldEval.evaluateModel(clsCopy, test);

                double npofb20Fold = computeNPofB20(clsCopy, train, test);

                // Accumulo risultati medi
                sumAccuracy += foldEval.pctCorrect() / 100.0;
                sumPrecision += foldEval.weightedPrecision();
                sumRecall += foldEval.weightedRecall();
                sumF1 += foldEval.weightedFMeasure();
                sumAUC += foldEval.weightedAreaUnderROC();
                sumKappa += foldEval.kappa();
                sumNP += npofb20Fold;

                // Salvataggio dettaglio fold
                EvaluationFoldResult foldRes = new EvaluationFoldResult(name, applyFeatureSelection, applySmote, SEED, i, n);
                foldRes.setAccuracy(foldEval.pctCorrect() / 100.0);
                foldRes.setF1(foldEval.weightedFMeasure());
                foldRes.setPrecision(foldEval.weightedPrecision());
                foldRes.setRecall(foldEval.weightedRecall());
                foldRes.setAuc(foldEval.weightedAreaUnderROC());
                foldRes.setKappa(foldEval.kappa());
                foldRes.setNpofb20(npofb20Fold);
                foldResults.add(foldRes);
            }
            DetailedFoldCsvWriter.writeAll(foldResults);
        }

        // Creazione dell'oggetto usando il tuo costruttore a 7 parametri
        EvaluationResult result = new EvaluationResult(
                name,
                sumAccuracy / totalIterations,                           // accuracy (non calcolata nel ciclo)
                sumPrecision / totalIterations,
                sumRecall / totalIterations,
                sumF1 / totalIterations,
                sumAUC / totalIterations,
                sumKappa / totalIterations
        );

        // Aggiunta di NPofB20 tramite il setter (visto che non è nel costruttore)
        result.setNpofb20(sumNP / totalIterations);

        return result;
    }

    public static double computeNPofB20(Classifier trainedCls, Instances train, Instances test) throws Exception {
        int yesIndex = test.classAttribute().indexOfValue("Yes");
        List<double[]> scored = new ArrayList<>();

        for (int i = 0; i < test.numInstances(); i++) {
            double[] dist = trainedCls.distributionForInstance(test.instance(i));
            scored.add(new double[]{dist[yesIndex], test.instance(i).classValue()});
        }

        scored.sort((a, b) -> Double.compare(b[0], a[0]));

        int topN = (int) Math.ceil(test.numInstances() * 0.2);
        int foundBuggy = 0;
        int totalBuggy = 0;

        for (int i = 0; i < scored.size(); i++) {
            if (scored.get(i)[1] == yesIndex) totalBuggy++;
            if (i < topN && scored.get(i)[1] == yesIndex) foundBuggy++;
        }

        return (totalBuggy == 0) ? 0 : (double) foundBuggy / totalBuggy;
    }
}