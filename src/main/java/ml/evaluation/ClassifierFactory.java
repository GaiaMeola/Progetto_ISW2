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
        return new NaiveBayes();
    }

    public static Classifier getIBk() {
        return new IBk(3); // K=3 è un buon compromesso
    }

    public static Classifier getRandomForest() {
        RandomForest rf = new RandomForest();
        rf.setNumIterations(100);
        // Usa tutti i core disponibili del tuo processore (M1/M2/M3)
        rf.setNumExecutionSlots(Runtime.getRuntime().availableProcessors());
        rf.setSeed(42);
        return rf;
    }
}