package ml.arff;

import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.util.ArrayList;

public class CSVToARFFConverter {

    public static void main(String[] args) throws Exception {
        String csvPath = Configuration.getOutputCsvPath();
        String arffPath = Configuration.getOutputArffPath();

        File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            throw new Exception("File CSV non trovato al percorso: " + csvPath);
        }

        // 1. Inizializzazione del loader WEKA con opzioni robuste
        CSVLoader loader = new CSVLoader();

        /* * Spiegazione opzioni:
         * -F ";" : Specifica il punto e virgola come separatore
         * -S "1,2" : Forza le prime due colonne (Project e Method) ad essere 'String'.
         * Questo è FONDAMENTALE perché se contengono apici (') o virgole (,)
         * Weka non proverà a interpretarle come categorie nominali, evitando crash.
         */
        loader.setOptions(new String[]{"-F", ";", "-S", "1,2"});
        loader.setSource(csvFile);

        Instances data = loader.getDataSet();

        // LOG DI CONTROLLO: Verifica se ha letto tutte le colonne
        Configuration.logger.info("Attributi rilevati nel CSV: " + data.numAttributes());
        if (data.numAttributes() < 5) {
            Configuration.logger.severe("ERRORE CRITICO: Weka ha letto troppe poche colonne (" +
                    data.numAttributes() + "). Controlla se ci sono apici non chiusi nel CSV!");
        }

        // 2. Impostazione dell'indice della classe
        if (data.classIndex() == -1) {
            data.setClassIndex(data.numAttributes() - 1);
        }

        // 3. Riordino delle etichette Bugginess {No, Yes}
        int classIndex = data.classIndex();
        Attribute originalAttr = data.attribute(classIndex);

        if (originalAttr.isNominal() && originalAttr.numValues() == 2) {
            // Verifichiamo se l'ordine attuale è {Yes, No}
            if ("Yes".equals(originalAttr.value(0)) && "No".equals(originalAttr.value(1))) {

                Configuration.logger.info("Riordino etichette Bugginess: {Yes, No} → {No, Yes}");

                ArrayList<String> reorderedValues = new ArrayList<>();
                reorderedValues.add("No");
                reorderedValues.add("Yes");

                Attribute newAttr = new Attribute(originalAttr.name(), reorderedValues);
                Instances newData = new Instances(data);
                newData.replaceAttributeAt(newAttr, classIndex);

                // Migrazione dei valori riga per riga
                for (int i = 0; i < data.numInstances(); i++) {
                    String label = data.instance(i).stringValue(classIndex);
                    newData.instance(i).setValue(classIndex, label);
                }
                data = newData;
            }
        }

        // 4. Salvataggio finale in ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(arffPath));
        saver.writeBatch();

        Configuration.logger.info("Conversione completata con successo: " + arffPath);
    }
}