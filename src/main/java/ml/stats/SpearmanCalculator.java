package ml.stats;

import ml.csv.CorrelationCsvWriter;
import ml.stats.SpearmanWithPValue.Result;
import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Remove;

import java.util.logging.Level;

/*
 Questa classe calcola la correlazione di Spearman tra ciascuna feature numerica
 e la variabile bugginess. Viene usata per identificare le feature più correlate
 con la presenza di difetti nei metodi Java.
 */
public class SpearmanCalculator {

    // Se true, lavora sul dataset ridotto (già sottoposto a feature selection)
    private static final boolean REDUCTION = false;

    public static void main(String[] args) {

        try {

            Instances data;

            data = getInstances();

            // Imposta la colonna di classe come target
            if (data.classIndex() == -1) {
                data.setClassIndex(data.numAttributes() - 1);
            }

            // Prepara il vettore della bugginess come numerico
            double[] bugginess = new double[data.numInstances()];
            for (int i = 0; i < data.numInstances(); i++) {
                bugginess[i] = data.instance(i).stringValue(data.classIndex()).equals("Yes") ? 1.0 : 0.0;
            }

            // Calcola Spearman e p-value per ogni feature numerica
            for (int i = 0; i < data.numAttributes() - 1; i++) {
                Attribute attr = data.attribute(i);
                if (!attr.isNumeric()) continue;

                // Estrai i valori della feature corrente per tutti i metodi
                double[] featureValues = new double[data.numInstances()];
                for (int j = 0; j < data.numInstances(); j++) {
                    featureValues[j] = data.instance(j).value(attr);
                }

                // Calcola Spearman ρ e p-value per la feature corrente
                Result result = SpearmanWithPValue.compute(featureValues, bugginess);
                CorrelationCsvWriter.writeCorrelation(attr.name(), result.rho, result.pValue);
            }

            if (Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("Calcolo Spearman completato: " + Configuration.getCorrelationCsvPath());
            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nel calcolo della correlazione Spearman", e);
        }
    }

    private static Instances getInstances() throws Exception {
        Instances data;
        // Caricamento del dataset
        if (!REDUCTION) {
            // Dataset completo
            DataSource source = new DataSource(Configuration.getOutputArffPath());
            data = source.getDataSet();
        } else {
            // Dataset ridotto
            DataSource source = new DataSource(Configuration.getReducedOutputArffPath());
            Instances original = source.getDataSet();

            // Rimuovi releaseID se presente
            int releaseIdIndex = original.attribute("releaseID") != null ? original.attribute("releaseID").index() : -1;
            if (releaseIdIndex != -1) {
                Remove remove = new Remove();
                remove.setAttributeIndicesArray(new int[]{releaseIdIndex});
                remove.setInputFormat(original);
                data = Filter.useFilter(original, remove);
            } else {
                data = original;
            }
        }
        return data;
    }
}
