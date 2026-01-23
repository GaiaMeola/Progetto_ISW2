package ml.evaluation;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.trees.RandomForest;

public class ClassifierFactory {

    private ClassifierFactory(){
        // Prevent instantiation
    }

    public static Classifier getNaiveBayes() {
        return new NaiveBayes(); // default params
    }

    public static Classifier getIBk() {
        return new IBk(3); // puoi parametrizzare il k
    }

    public static Classifier getRandomForest() {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100); // questo è spesso il nome corretto
        rf.setNumFeatures(0); // default (√n features)
        rf.setNumExecutionSlots(Runtime.getRuntime().availableProcessors());
        rf.setSeed(42);       // riproducibilità
        return rf;
    }


}
