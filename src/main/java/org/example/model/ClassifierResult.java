package org.example.model;

import weka.classifiers.Evaluation;

public class ClassifierResult {

    private final int walkForwardIteration;
    private final String classifierName;
    private final boolean featureSelection;
    private final boolean hasSampling;
    private final CustomClassifier customClassifier;
    private final boolean costSensitive;
    private double trainingPercent;
    private double precision;
    private double recall;
    private final double areaUnderROC;
    private final double kappa;
    private final double truePositives;
    private final double falsePositives;
    private final double trueNegatives;
    private final double falseNegatives;

    public ClassifierResult(int walkForwardIteration, CustomClassifier customClassifier, Evaluation evaluation) {
        this.walkForwardIteration = walkForwardIteration;
        this.customClassifier = customClassifier;
        this.classifierName = customClassifier.getClassifierName();
        this.featureSelection = (!customClassifier.getFeatureSelectionFilterName().equals("NoSelection"));
        this.hasSampling = (!customClassifier.getSamplingFilterName().equals("NoSampling"));
        this.costSensitive = customClassifier.isCostSensitive();

        trainingPercent = 0.0;
        truePositives = evaluation.numTruePositives(0);
        falsePositives = evaluation.numFalsePositives(0);
        trueNegatives = evaluation.numTrueNegatives(0);
        falseNegatives = evaluation.numFalseNegatives(0);
        if(truePositives == 0.0 && falsePositives == 0.0){
            precision = Double.NaN;
        } else{
            precision = evaluation.precision(0);
        }
        if(truePositives == 0.0 && falseNegatives == 0.0){
            recall = Double.NaN;
        } else{
            recall = evaluation.recall(0);
        }
        areaUnderROC = evaluation.areaUnderROC(0);
        kappa = evaluation.kappa();
    }

    public boolean hasSampling() {
        return hasSampling;
    }

    public int getWalkForwardIteration() {
        return walkForwardIteration;
    }

    public String getClassifierName() {
        return classifierName;
    }

    public boolean isFeatureSelection() {
        return featureSelection;
    }

    public boolean isHasSampling() {
        return hasSampling;
    }

    public CustomClassifier getCustomClassifier() {
        return customClassifier;
    }

    public boolean isCostSensitive() {
        return costSensitive;
    }

    public double getTrainingPercent() {
        return trainingPercent;
    }

    public void setTrainingPercent(double trainingPercent) {
        this.trainingPercent = trainingPercent;
    }

    public double getPrecision() {
        return precision;
    }

    public void setPrecision(double precision) {
        this.precision = precision;
    }

    public double getRecall() {
        return recall;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public double getAreaUnderROC() {
        return areaUnderROC;
    }

    public double getKappa() {
        return kappa;
    }

    public double getTruePositives() {
        return truePositives;
    }

    public double getFalsePositives() {
        return falsePositives;
    }

    public double getTrueNegatives() {
        return trueNegatives;
    }

    public double getFalseNegatives() {
        return falseNegatives;
    }
}