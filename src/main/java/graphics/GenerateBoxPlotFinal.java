package graphics;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenerateBoxPlotFinal {
    public static void main(String[] args) throws Exception {
        // --- CONFIGURAZIONE MANUALE ---
        String projectName = "Bookkeeper"; // Cambia in "OpenJPA" quando serve
        String csvPath = "csv_output/fold_results.csv"; // Il file che contiene sia SMOTE che NoSMOTE
        // ------------------------------

        Map<String, List<Double>> mapData = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Classifier") || line.trim().isEmpty()) continue;
                String[] v = line.split(",");
                try {
                    String classifier = v[0];
                    // Identifichiamo la configurazione (SMOTE o NoSMOTE)
                    // Nel ciclo di lettura del CSV
                    String strategyLabel = Boolean.parseBoolean(v[2]) ? "UnderSampling" : "NoSampling";

                    // Carichiamo le 5 metriche principali
                    addValue(mapData, classifier, strategyLabel, "Precision", Double.parseDouble(v[7]));
                    addValue(mapData, classifier, strategyLabel, "Recall", Double.parseDouble(v[8]));
                    addValue(mapData, classifier, strategyLabel, "AUC", Double.parseDouble(v[10]));
                    addValue(mapData, classifier, strategyLabel, "Kappa", Double.parseDouble(v[11]));
                    addValue(mapData, classifier, strategyLabel, "NPofB20", Double.parseDouble(v[12]));
                } catch (Exception e) {
                    System.err.println("Errore parsing riga: " + line);
                }
            }
        }

        // Generazione automatica di tutti i grafici per ogni metrica
        String[] metriche = {"Precision", "Recall", "AUC", "Kappa", "NPofB20"};
        for (String m : metriche) {
            String outputFileName = projectName.toLowerCase() + "_comparison_" + m.toLowerCase() + ".png";
            salvaGraficoConfronto(mapData, m, outputFileName, projectName);
        }

        System.out.println("Tutti i grafici di confronto per " + projectName + " sono stati generati!");
    }

    private static void salvaGraficoConfronto(Map<String, List<Double>> mapData, String metrica, String filename, String projectName) throws Exception {
        DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();

        // Organizziamo i dati: Serie = Configurazione (SMOTE/NoSMOTE), Categoria = Classificatore
        for (String key : mapData.keySet()) {
            if (key.endsWith("|" + metrica)) {
                String[] p = key.split("\\|"); // [0]=Classifier, [1]=Config, [2]=Metric
                dataset.add(mapData.get(key), p[1], p[0]);
            }
        }

        JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
                projectName + ": Impatto SMOTE su " + metrica,
                "Classificatore", "Valore", dataset, true);

        // Estetica Clean
        chart.setBackgroundPaint(java.awt.Color.WHITE);
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(java.awt.Color.WHITE);
        plot.setRangeGridlinePaint(java.awt.Color.LIGHT_GRAY);

        BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
        renderer.setMeanVisible(false);     // Rimuove cerchio nero media
        renderer.setMaxOutlierVisible(false);
        renderer.setMinOutlierVisible(false);// Rimuove cerchi/outlier esterni
        renderer.setFillBox(true);
        renderer.setMedianVisible(true);

        // Colori per il confronto: Azzurro (NoSMOTE) vs Blu (SMOTE)
        renderer.setSeriesPaint(0, new java.awt.Color(173, 216, 230)); // NoSMOTE
        renderer.setSeriesPaint(1, new java.awt.Color(70, 130, 180));  // SMOTE

        plot.setRenderer(renderer);
        ChartUtils.saveChartAsPNG(new File(filename), chart, 1000, 600);
    }

    private static void addValue(Map<String, List<Double>> map, String cl, String cfg, String m, double val) {
        String key = cl + "|" + cfg + "|" + m;
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(val);
    }
}