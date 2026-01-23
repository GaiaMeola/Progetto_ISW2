package ml.stats;

import util.Configuration;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

import java.io.FileWriter;
import java.util.logging.Level;

/*
 Classe che identifica l'AFMethod, ovvero il metodo:
  - buggy (bugginess = Yes)
  - appartenente all'ultima release
  - con valore massimo della feature AFeature (es. NSmells o NestingDepth)
 */
public class AFMethodFinder {

    public static void main(String[] args) {

        try {
            // Carica dataset
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            Instances data = source.getDataSet();
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Seleziona AFeature dinamicamente
            String logicalAFeature = Configuration.SELECTED_PROJECT == util.ProjectType.BOOKKEEPER
                    ? "NumberOfSmells"
                    : "NestingDepth";

            // Ricerca della feature nel dataset
            int aFeatureIndex = -1;
            for (int i = 0; i < data.numAttributes(); i++) {
                String normalized = data.attribute(i).name().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (normalized.equals(logicalAFeature.toLowerCase())) {
                    aFeatureIndex = i;
                    break;
                }
            }

            if (aFeatureIndex == -1) {
                throw new IllegalArgumentException("AFeature non trovata: " + logicalAFeature);
            }

            int classIndex = data.classIndex();
            int releaseIndex = data.attribute("ReleaseID").index();

            // Trova ultima release
            String lastRelease = data.instance(0).stringValue(releaseIndex);
            for (int i = 1; i < data.numInstances(); i++) {
                String rel = data.instance(i).stringValue(releaseIndex);
                if (rel.compareTo(lastRelease) > 0) {
                    lastRelease = rel;
                }
            }

            // Trova metodo buggy con max AFeature
            double maxVal = Double.NEGATIVE_INFINITY;
            String methodPath = null;
            for (int i = 0; i < data.numInstances(); i++) {
                String rel = data.instance(i).stringValue(releaseIndex);
                String bug = data.instance(i).stringValue(classIndex);
                double feature = data.instance(i).value(aFeatureIndex);

                if (rel.equals(lastRelease) && bug.equals("Yes") && feature > maxVal) {
                    maxVal = feature;
                    methodPath = data.instance(i).stringValue(1); // colonna Method
                }
            }

            // Salva su file
            String path = "ml_results/" + Configuration.getProjectName().toLowerCase() + "_afmethod_debug.txt";
            FileWriter writer = new FileWriter(path);
            writer.write("Project: " + Configuration.getProjectName() + "\n");
            writer.write("AFeature: " + logicalAFeature + "\n");
            writer.write("Last Release: " + lastRelease + "\n");
            writer.write("Buggy Method: " + methodPath + "\n");
            writer.write("Value of AFeature: " + maxVal + "\n");
            writer.close();

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("AFMethod trovato e salvato: " + methodPath);
            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nella selezione dell'AFMethod", e);
        }
    }
}

