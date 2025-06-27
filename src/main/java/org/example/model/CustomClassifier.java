package org.example.model;

import weka.classifiers.Classifier;

public class CustomClassifier {
    private final Classifier classifier;
    private final String featureSelectionFilterName;
    private final String samplingFilterName;
    private final String classifierName;
    private final boolean costSensitive;

    public CustomClassifier(Classifier classifier, String classifierName, String featureSelectionFilterName,
                            String bestFirstDirection, String samplingFilterName, boolean isCostSensitive) {
        this.classifier = classifier;
        switch (samplingFilterName) {
            case "Resample":
                this.samplingFilterName = "OverSampling";
                break;
            case "SpreadSubsample":
                this.samplingFilterName = "UnderSampling";
                break;
            case "SMOTE":
                this.samplingFilterName = "SMOTE";
                break;
            default:
                this.samplingFilterName = samplingFilterName;
                break;
        }
        if (featureSelectionFilterName.equals("BestFirst")) {
            this.featureSelectionFilterName = featureSelectionFilterName + "(" + bestFirstDirection + ")";
        } else {
            this.featureSelectionFilterName = featureSelectionFilterName;
        }
        this.costSensitive = isCostSensitive;
        this.classifierName = classifierName;
    }

    public Classifier getClassifier() {
        return classifier;
    }

    public String getFeatureSelectionFilterName() {
        return featureSelectionFilterName;
    }

    public String getSamplingFilterName() {
        return samplingFilterName;
    }

    public String getClassifierName() {
        return classifierName;
    }

    public boolean isCostSensitive() {
        return costSensitive;
    }
}