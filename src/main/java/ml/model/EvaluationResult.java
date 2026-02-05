package ml.model;

public class EvaluationResult {

    private final double accuracy;
    private final double precision;
    private final double recall;
    private final double f1;
    private final double auc;
    private final double kappa;
    private final String classifierName;
    private double npofb20;

    public EvaluationResult(String name, double accuracy, double precision, double recall, double f1, double auc, double kappa) {
        this.classifierName = name;
        this.accuracy = accuracy;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.auc = auc;
        this.kappa = kappa;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public double getF1() {
        return f1;
    }

    public String getClassifierName() {
        return classifierName;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public double getAuc() {
        return auc;
    }

    public double getKappa() {
        return kappa;
    }

    public double getNpofb20() {
        return npofb20;
    }

    public void setNpofb20(double npofb20) {
        this.npofb20 = npofb20;
    }


    @Override
    public String toString() {
        return String.format("[%s] Acc: %.4f  Prec: %.4f  Rec: %.4f  F1: %.4f  AUC: %.4f  Kappa: %.4f  NPofB20=%.3f",
                classifierName, accuracy, precision, recall, f1, auc, kappa, npofb20);

    }

}
