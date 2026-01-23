package ml.arff;

import util.Configuration;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.core.converters.ArffSaver;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;

// Questa classe si occupa della conversione da CSV a ARFF
public class CSVToARFFConverter {

    public static void main(String[] args) throws Exception {
        String csvPath = Configuration.getOutputCsvPath(); // Percorso del file CSV da convertire
        String arffPath = Configuration.getOutputArffPath(); // Percorso del file ARFF di output

        // Inizializzazione del loader WEKA per il CSV
        CSVLoader loader = new CSVLoader();
        loader.setOptions(new String[]{"-F", ";"});
        loader.setSource(new File(csvPath));
        Instances data = loader.getDataSet();  // carica le istanze dal CSV

        // Riordina le etichette
        int classIndex = data.numAttributes() - 1; // ultima colonna bugginess
        Attribute originalAttr = data.attribute(classIndex);

        if (originalAttr.isNominal()
                && originalAttr.numValues() == 2
                && "Yes".equals(originalAttr.value(0))
                && "No".equals(originalAttr.value(1))) {

            if (Configuration.logger.isLoggable(Level.INFO))
                Configuration.logger.info("Riordino etichette Bugginess: {Yes,No} → {No,Yes}");

            ArrayList<String> reordered = new ArrayList<>();
            reordered.add("No");
            reordered.add("Yes");

            // Creazione del nuovo attributo con le etichette ordinate
            Attribute newAttr = new Attribute(originalAttr.name(), reordered);
            Instances newData = new Instances(data);  // copia del dataset originale
            newData.replaceAttributeAt(newAttr, classIndex); // sostituisce l'attributo

            // Copia i valori di etichetta dalle istanze originali
            for (int i = 0; i < newData.numInstances(); i++) {
                String label = data.instance(i).stringValue(classIndex);
                newData.instance(i).setValue(classIndex, label);
            }

            // Aggiorna il dataset finale
            data = newData;
        }

        // Inizializza il salvataggio del file ARFF
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(new File(arffPath));
        saver.writeBatch(); // esegue il salvataggio fisico del file

        if (Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info(String.format("Conversione completata: path = %s", arffPath));
        }
    }
}
