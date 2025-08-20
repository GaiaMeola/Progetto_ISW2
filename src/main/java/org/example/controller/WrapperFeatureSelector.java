package org.example.controller;

import weka.attributeSelection.AttributeSelection;
import weka.attributeSelection.GreedyStepwise;
import weka.attributeSelection.WrapperSubsetEval;
import weka.classifiers.Classifier;
import weka.core.Instances;

public class WrapperFeatureSelector {

    private final Classifier baseClassifier;

    public WrapperFeatureSelector(Classifier baseClassifier) {
        this.baseClassifier = baseClassifier;
    }

    public Instances apply(Instances data) throws Exception {
        // Imposta l'indice della classe se non è già stato fatto
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        // Valutatore wrapper
        WrapperSubsetEval evaluator = new WrapperSubsetEval();
        evaluator.setClassifier(baseClassifier);
        evaluator.setFolds(10); // 10-fold cross-validation interna

        // Metodo di ricerca
        GreedyStepwise search = new GreedyStepwise();
        search.setSearchBackwards(false); // true per backward elimination

        // Selezione attributi
        AttributeSelection selector = new AttributeSelection();
        selector.setEvaluator(evaluator);
        selector.setSearch(search);
        selector.SelectAttributes(data);

        // Applica il filtro al dataset originale per ottenere il dataset ridotto
        Instances reducedData = selector.reduceDimensionality(data);

        System.out.println("Attributi originali: " + data.numAttributes());
        System.out.println("Attributi selezionati: " + reducedData.numAttributes());

        return reducedData;
    }
}
