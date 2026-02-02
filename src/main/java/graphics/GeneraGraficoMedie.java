package graphics;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import java.io.File;

public class GeneraGraficoMedie {
    public static void main(String[] args) throws Exception {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        // Dati dal tuo file bookkeeper_summary_results.csv
        dataset.addValue(0.8881, "Naive Bayes", "AUC");
        dataset.addValue(0.4888, "Naive Bayes", "Kappa");
        dataset.addValue(0.5842, "Naive Bayes", "NPofB20");

        dataset.addValue(0.8696, "Random Forest", "AUC");
        dataset.addValue(0.3442, "Random Forest", "Kappa");
        dataset.addValue(0.4781, "Random Forest", "NPofB20");

        dataset.addValue(0.8554, "IBk", "AUC");
        dataset.addValue(0.3884, "IBk", "Kappa");
        dataset.addValue(0.5215, "IBk", "NPofB20");

        JFreeChart barChart = ChartFactory.createBarChart(
                "Performance Medie su Bookkeeper",
                "Metriche", "Punteggio",
                dataset, PlotOrientation.VERTICAL,
                true, true, false);

        // Salva l'immagine pronta per il report
        ChartUtils.saveChartAsPNG(new File("medie_classificatori.png"), barChart, 900, 600);
    }
}