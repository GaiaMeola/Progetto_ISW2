package org.example.controller;

import org.example.model.ClassifierResult;
import org.example.model.CustomClassifier;
import org.example.model.MethodMetrics;
import org.example.utilities.DatasetUtils;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

public class ProjectAnalyzer {

    public static class DatasetGroups {

        /*suddividiamo il dataset in 4 gruppi*/
        public List<MethodMetrics> A = new ArrayList<>();
        public List<MethodMetrics> Bplus = new ArrayList<>();
        public List<MethodMetrics> B = new ArrayList<>();
        public List<MethodMetrics> C = new ArrayList<>();
    }

    public static DatasetGroups splitDataset(List<MethodMetrics> methods) {
        DatasetGroups groups = new DatasetGroups();

        for (MethodMetrics m : methods) {
            groups.A.add(m);

            if (m.getNumberOfCodeSmells() > 0) {
                groups.Bplus.add(m);

                MethodMetrics copy = copyMethod(m);
                copy.setNumberOfCodeSmells(0); // simulate refactoring
                groups.B.add(copy);
            } else {
                groups.C.add(m);
            }
        }

        return groups;
    }

    private static MethodMetrics copyMethod(MethodMetrics original) {
        MethodMetrics copy = new MethodMetrics();
        copy.setBug(original.isBug());
        copy.setLinesOfCode(original.getLinesOfCode());
        copy.setStatementCount(original.getStatementCount());
        copy.setCyclomaticComplexity(original.getCyclomaticComplexity());
        copy.setCognitiveComplexity(original.getCognitiveComplexity());
        copy.setNestingDepth(original.getNestingDepth());
        copy.setParameterCount(original.getParameterCount());
        copy.setNumberOfTests(original.getNumberOfTests());
        copy.setAge(original.getAge());
        copy.setFanIn(original.getFanIn());
        copy.setFanOut(original.getFanOut());
        copy.setMethodAccessor(original.getMethodAccessor());
        copy.setNumberOfChanges(original.getNumberOfChanges());
        copy.setAddedChurn(original.getAddedChurn());
        copy.setRemovedChurn(original.getRemovedChurn());
        copy.setNumberOfCodeSmells(0);
        copy.setSimpleName(original.getSimpleName());
        return copy;
    }

    public static void evaluateClassifierOnSmellGroups(ClassifierResult bestResult, List<MethodMetrics> methodList) throws Exception {
        CustomClassifier custom = bestResult.getCustomClassifier();
        Classifier classifier = custom.getClassifier();

        // Step 1: suddividi il dataset
        DatasetGroups groups = splitDataset(methodList);

        // Step 2: convertili in Instances
        Instances trainSet = DatasetUtils.convertToInstances(groups.A);
        trainSet.setClassIndex(trainSet.numAttributes() - 1);
        classifier.buildClassifier(trainSet);

        Instances testA = DatasetUtils.convertToInstances(groups.A);
        Instances testBplus = DatasetUtils.convertToInstances(groups.Bplus);
        Instances testB = DatasetUtils.convertToInstances(groups.B);
        Instances testC = DatasetUtils.convertToInstances(groups.C);

        testA.setClassIndex(testA.numAttributes() - 1);
        testBplus.setClassIndex(testBplus.numAttributes() - 1);
        testB.setClassIndex(testB.numAttributes() - 1);
        testC.setClassIndex(testC.numAttributes() - 1);

        // Step 3: predici i bug
        int buggyPredA = predictBugCount(classifier, testA);
        int buggyPredBplus = predictBugCount(classifier, testBplus);
        int buggyPredB = predictBugCount(classifier, testB);
        int buggyPredC = predictBugCount(classifier, testC);

        int actualBuggyBplus = countBuggy(groups.Bplus);
        int actualBuggyB = countBuggy(groups.B);
        int actualBuggyC = countBuggy(groups.C);

        // Step 4: stampa la tabella finale
        System.out.println("---------- OUR PROJECT RESULTS ----------");
        System.out.println("Group\t\tActualBuggy\tPredictedBuggy");
        System.out.printf("B+\t\t%d\t\t\t%d%n", actualBuggyBplus, buggyPredBplus);
        System.out.printf("B (cleaned)\t%d\t\t\t%d%n", actualBuggyB, buggyPredB);
        System.out.printf("C\t\t%d\t\t\t%d%n", actualBuggyC, buggyPredC);
        System.out.println("-----------------------------------------");
        System.out.printf("Total methods: %d%n", methodList.size());
    }

    private static int predictBugCount(Classifier classifier, Instances instances) throws Exception {
        int count = 0;
        for (Instance instance : instances) {
            double pred = classifier.classifyInstance(instance);
            if (pred == 1.0) {
                count++;
            }
        }
        return count;
    }

    private static int countBuggy(List<MethodMetrics> methods) {
        return (int) methods.stream().filter(MethodMetrics::isBug).count();
    }
}
