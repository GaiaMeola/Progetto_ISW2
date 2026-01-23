package ml.evaluation;

import ml.csv.DetailedFoldCsvWriter;
import ml.model.EvaluationFoldResult;
import ml.model.EvaluationResult;
import util.Configuration;
import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.RemoveUseless;
import java.util.ArrayList;
import java.util.List;

public class CrossValidator {

    private CrossValidator(){
        // Prevent instantiation
    }

    private static final int SEED = 42;

    /**
     * Metodo per eseguire la cross validation di un classificatore con possibili step di preprocessing:
     * - Feature Selection (InfoGain + Ranker)
     * - SMOTE per il bilanciamento della classe minoritaria
     */
    public static EvaluationResult evaluateAndWrap(String name, Classifier cls, Instances data,
                                                   int folds, int repeats,
                                                   boolean applyFeatureSelection,
                                                   boolean applySmote) throws Exception {


        // Rimuove l'attributo ReleaseID se presente per evitare data leakage
        int releaseIdIndex = data.attribute("ReleaseID") != null ? data.attribute("ReleaseID").index() : -1;
        if (releaseIdIndex != -1) {
            Remove remove = new Remove();
            remove.setAttributeIndicesArray(new int[]{releaseIdIndex});
            remove.setInputFormat(data);
            data = Filter.useFilter(data, remove);
        }

        double totalAccuracy = 0;
        double totalPrecision = 0;
        double totalRecall = 0;
        double totalF1 = 0;
        double totalAUC = 0;
        double totalKappa = 0;
        int totalFolds = (folds - 1) * repeats;

        // 10x10-fold Cross Validation
        for (int i = 0; i < repeats; i++) {

            Instances trainFull = new Instances(data);

            // Applica Feature Selection su tutte le istanze
            if (applyFeatureSelection) {
                RemoveUseless remove = new RemoveUseless();
                remove.setInputFormat(trainFull);
                trainFull = Filter.useFilter(trainFull, remove);

                AttributeSelection selector = new AttributeSelection();
                InfoGainAttributeEval eval = new InfoGainAttributeEval();
                Ranker ranker = new Ranker();
                ranker.setThreshold(0.01); // rimuove feature non informative
                selector.setEvaluator(eval);
                selector.setSearch(ranker);
                selector.SelectAttributes(trainFull);
                trainFull = selector.reduceDimensionality(trainFull);
            }

            // Applica SMOTE una volta per ripetizione
            if (applySmote && Configuration.getProjectName().equals("BOOKKEEPER")) {
                double percentage = 65;

                SMOTE smote = new SMOTE();
                smote.setPercentage(percentage);
                smote.setNearestNeighbors(5);
                smote.setInputFormat(trainFull);
                trainFull = Filter.useFilter(trainFull, smote);
            }

            List<EvaluationFoldResult> foldResults = new ArrayList<>();

            int totalInstances = trainFull.numInstances();
            int foldSize = totalInstances / folds;

            for (int n = 1; n < folds; n++) {
                int trainEnd = n * foldSize;
                int testEnd = Math.min(trainEnd + foldSize, totalInstances);

                Instances train = new Instances(trainFull, 0, trainEnd);
                Instances test = new Instances(trainFull, trainEnd, testEnd - trainEnd);

                // Clona il classificatore e addestra
                Classifier clsCopy = weka.classifiers.AbstractClassifier.makeCopy(cls);
                clsCopy.buildClassifier(train);
                Evaluation foldEval = new Evaluation(train);
                foldEval.evaluateModel(clsCopy, test);
                double npofb20Fold = CrossValidator.computeNPofB20(clsCopy, test);

                EvaluationFoldResult foldResult = new EvaluationFoldResult(
                        name, applyFeatureSelection, applySmote, SEED, i, n
                );
                foldResult.setAccuracy(foldEval.weightedPrecision());
                foldResult.setRecall(foldEval.weightedRecall());
                foldResult.setF1(foldEval.weightedFMeasure());
                foldResult.setAuc(foldEval.weightedAreaUnderROC());
                foldResult.setKappa(foldEval.kappa());
                foldResult.setNpofb20(npofb20Fold);

                foldResults.add(foldResult);

                totalAccuracy += foldEval.pctCorrect() / 100.0;
                totalPrecision += foldEval.weightedPrecision();
                totalRecall += foldEval.weightedRecall();
                totalF1 += foldEval.weightedFMeasure();
                totalAUC += foldEval.weightedAreaUnderROC();
                totalKappa += foldEval.kappa();
            }


            DetailedFoldCsvWriter.writeAll(foldResults);
        }

        EvaluationResult result = new EvaluationResult(
                name,
                totalAccuracy / totalFolds,
                totalPrecision / totalFolds,
                totalRecall / totalFolds,
                totalF1 / totalFolds,
                totalAUC / totalFolds,
                totalKappa / totalFolds
        );

        // Calcolo NPofB20 sul dataset originale
        double npofb20 = CrossValidator.computeNPofB20(cls, data);
        result.setNpofb20(npofb20);

        return result;
    }

    public static double computeNPofB20(Classifier cls, Instances data) throws Exception {
        // Costruisce un nuovo classificatore su tutti i dati
        Classifier copy = weka.classifiers.AbstractClassifier.makeCopy(cls);
        copy.buildClassifier(data);

        // Trova l’indice della classe “Yes”
        int yesIndex = data.classAttribute().indexOfValue("Yes");
        if (yesIndex == -1) {
            throw new IllegalArgumentException("La classe 'Yes' non è presente tra i valori della variabile target.");
        }

        // Prepara una lista (score, isBuggy)
        List<double[]> scored = new ArrayList<>();
        for (int i = 0; i < data.numInstances(); i++) {
            double[] dist = copy.distributionForInstance(data.instance(i));
            double score = dist[yesIndex];  // probabilità che sia buggy
            double actual = data.instance(i).classValue(); // 1 = Yes, 0 = No (valore numerico)
            scored.add(new double[]{score, actual});
        }

        // Ordina per probabilità discendente
        scored.sort((a, b) -> Double.compare(b[0], a[0]));

        int topN = (int) Math.ceil(data.numInstances() * 0.2); // top 20%
        int foundBuggy = 0;
        int totalBuggy = 0;

        for (int i = 0; i < data.numInstances(); i++) {
            if (scored.get(i)[1] == yesIndex) totalBuggy++;
            if (i < topN && scored.get(i)[1] == yesIndex) foundBuggy++;
        }

        if (totalBuggy == 0) return 0.0;

        return (double) foundBuggy / totalBuggy;
    }

}
