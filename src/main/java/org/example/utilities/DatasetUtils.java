package org.example.utilities;

import org.example.model.MethodMetrics;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.util.ArrayList;
import java.util.List;

public class DatasetUtils {
    /*ci permette di ottenere un oggetto per addestrare un classificatore*/
    /*Instances è un formato che Weka interpreta come dataset*/

    public static Instances convertToInstances(List<MethodMetrics> metricsList) {
        ArrayList<Attribute> attributes = new ArrayList<>();

        attributes.add(new Attribute("linesOfCode"));
        attributes.add(new Attribute("statementCount"));
        attributes.add(new Attribute("cyclomaticComplexity"));
        attributes.add(new Attribute("cognitiveComplexity"));
        attributes.add(new Attribute("nestingDepth"));
        attributes.add(new Attribute("parameterCount"));
        attributes.add(new Attribute("numberOfTests"));
        attributes.add(new Attribute("age"));
        attributes.add(new Attribute("fanIn"));
        attributes.add(new Attribute("fanOut"));
        attributes.add(new Attribute("methodAccessor"));
        attributes.add(new Attribute("numberOfChanges"));
        attributes.add(new Attribute("addedChurn"));
        attributes.add(new Attribute("removedChurn"));
        attributes.add(new Attribute("numberOfCodeSmells"));

        // Classe target: bug (valori possibili: 0 = no bug, 1 = bug)
        List<String> classValues = new ArrayList<>();
        classValues.add("0");
        classValues.add("1");
        Attribute classAttribute = new Attribute("bug", classValues);
        attributes.add(classAttribute);

        Instances dataset = new Instances("MethodMetricsDataset", attributes, metricsList.size());
        dataset.setClassIndex(dataset.numAttributes() - 1);

        for (MethodMetrics m : metricsList) {
            double[] values = new double[dataset.numAttributes()];
            values[0] = m.getLinesOfCode();
            values[1] = m.getStatementCount();
            values[2] = m.getCyclomaticComplexity();
            values[3] = m.getCognitiveComplexity();
            values[4] = m.getNestingDepth();
            values[5] = m.getParameterCount();
            values[6] = m.getNumberOfTests();
            values[7] = m.getAge();
            values[8] = m.getFanIn();
            values[9] = m.getFanOut();
            values[10] = Double.parseDouble(m.getMethodAccessor());
            values[11] = m.getNumberOfChanges();
            values[12] = m.getAddedChurn();
            values[13] = m.getRemovedChurn();
            values[14] = m.getNumberOfCodeSmells();

            // Class label
            values[15] = m.isBug() ? 1.0 : 0.0;

            dataset.add(new DenseInstance(1.0, values));
        }

        return dataset;
    }
}
