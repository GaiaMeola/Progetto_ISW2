package ml.stats;

import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.apache.commons.math3.distribution.TDistribution;

/*
 Classe di utilità per calcolare il coefficiente di correlazione di Spearman (ρ)
 e il relativo p-value, che rappresenta la significatività statistica della correlazione.
 */
public class SpearmanWithPValue {

    private SpearmanWithPValue(){
        // Prevent instantiation
    }

    public static class Result { //NOSONAR
        public final double rho;     // Aggiungi public qui
        public final double pValue;  // Aggiungi public qui

        public Result(double rho, double pValue) {
            this.rho = rho;
            this.pValue = pValue;
        }
    }

    // Calcola il coefficiente di Spearman ρ e il p-value associato per due vettori
    public static Result compute(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Le serie devono avere la stessa lunghezza.");
        }

        // Calcola il coefficiente di Spearman ρ
        SpearmansCorrelation corr = new SpearmansCorrelation();
        double rho = corr.correlation(x, y);

        // Calcolo della statistica t
        int n = x.length;
        double t = rho * Math.sqrt((n - 2.0) / (1 - rho * rho));

        // Distribuzione t di Student con n - 2 gradi di libertà
        TDistribution tDist = new TDistribution((double)n - 2);

        // Calcolo del p-value
        double pValue = 2 * (1 - tDist.cumulativeProbability(Math.abs(t)));

        return new Result(rho, pValue);
    }
}
