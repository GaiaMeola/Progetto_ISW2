package org.example.controller;

import org.example.logging.SeLogger;
import org.example.model.ClassifierResult;
import org.example.model.CustomClassifier;
import org.example.model.Module;
import org.example.utilities.Sink;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.CVParameterSelection;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class WekaProcessing {

    private final String projName;
    private final int numIter;
    private final List<ClassifierResult> classifierResults;

    public WekaProcessing(String projectName, int iterations) {
        this.projName = projectName;
        this.numIter = iterations;
        this.classifierResults = new ArrayList<>();
    }

//    public void classify() {
//
//        final String arff = Sink.FileExtension.ARFF.name().toLowerCase(Locale.getDefault());
//        final String head = Sink.DELIVERY_OUTPUT + File.separator + "datasets" + File.separator + projName +
//                File.separator + arff + File.separator;
//        final String training_path = head + "training" + File.separator + this.projName;
//        final String testing_path = head + "testing" + File.separator + this.projName;
//
//        CountDownLatch latch = new CountDownLatch(this.numIter);
//
//        for (int walkForwardIteration = 1; walkForwardIteration <= this.numIter; walkForwardIteration++) {
//            final int iteration = walkForwardIteration;
//
//            Runnable task = () -> {
//                try {
//                    ConverterUtils.DataSource trainingSetDataSource = new ConverterUtils
//                            .DataSource(training_path + '_' + iteration + '.' + arff);
//                    ConverterUtils.DataSource testingSetDataSource = new ConverterUtils
//                            .DataSource(testing_path + '_' + iteration + '.' + arff);
//                    Instances trainingSetInstance = trainingSetDataSource.getDataSet();
//                    Instances testingSetInstance = testingSetDataSource.getDataSet();
//
//                    int numAttr = trainingSetInstance.numAttributes();
//                    trainingSetInstance.setClassIndex(numAttr - 1);
//                    testingSetInstance.setClassIndex(numAttr - 1);
//
//                    List<CustomClassifier> customClassifiers =
//                            ComputeAllClassifiersCombinations.returnAllClassifiersCombinations(
//                                    trainingSetInstance.attributeStats(numAttr - 1));
//
//                    for (CustomClassifier customClassifier : customClassifiers) {
//                        Evaluation evaluator = new Evaluation(testingSetInstance);
//
//                        Classifier originalClassifier = customClassifier.getClassifier();
//                        Classifier tunedClassifier = tuneIfNeeded(originalClassifier, trainingSetInstance);
//
//                        tunedClassifier.buildClassifier(trainingSetInstance);
//                        evaluator.evaluateModel(tunedClassifier, testingSetInstance);
//
//                        // Calcolo effort-aware metrics
//                        List<Module> modules = new ArrayList<>();
//
//                        for (int i = 0; i < testingSetInstance.numInstances(); i++) {
//                            Instance inst = testingSetInstance.instance(i);
//
//                            // Probabilità che l'istanza sia buggy
//                            double[] probs = tunedClassifier.distributionForInstance(inst);
//                            double risk = probs[0]; // classe 0 = buggy (assunto)
//
//                            int loc = (int) inst.value(testingSetInstance.attribute("loc"));
//                            boolean buggy = ((int) inst.classValue()) == 0;
//
//                            Module m = new Module("mod_" + i, buggy, loc, risk);
//                            modules.add(m);
//                        }
//
//                        double pofb20 = EffortAwareMetrics.calculatePOFB(modules, 20.0);
//                        double npofb20 = EffortAwareMetrics.calculateNPofB(modules, 20.0);
//
//                        // Salvo nel risultato
//                        ClassifierResult resultOfClassifier = new ClassifierResult(iteration, customClassifier, evaluator);
//                        resultOfClassifier.setTrainingPercent(100.0 * (
//                                (double) trainingSetInstance.numInstances() /
//                                        (trainingSetInstance.numInstances() + testingSetInstance.numInstances())));
//
//                        resultOfClassifier.setPofb20(pofb20);
//                        resultOfClassifier.setNpofb20(npofb20);
//
//                        synchronized (classifierResults) {
//                            classifierResults.add(resultOfClassifier);
//                        }
//                    }
//                } catch (Exception e) {
//                    final String severe = "Error in classify() during walkForwardIteration: " + iteration;
//                    SeLogger.getInstance().getLogger().severe(severe + ": " + e.getMessage());
//                } finally {
//                    latch.countDown();
//                }
//            };
//
//            new Thread(task).start();
//        }
//
//        try {
//            latch.await();
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            SeLogger.getInstance().getLogger().severe("Classification interrupted: " + e.getMessage());
//        }
//    }
//
//    public void sinkResults() {
//        Sink.serializeResultsToCsv(this.projName, this.classifierResults);
//    }
//
//    private Classifier tuneIfNeeded(Classifier classifier, Instances trainingSet) throws Exception {
//        if (classifier instanceof RandomForest) {
//            CVParameterSelection ps = new CVParameterSelection();
//            ps.setClassifier(classifier);
//            ps.addCVParameter("I 10 100 10");
//            ps.setNumFolds(3);
//            ps.buildClassifier(trainingSet);
//            return ps;
//        } else {
//            return classifier;
//        }
//    }
}