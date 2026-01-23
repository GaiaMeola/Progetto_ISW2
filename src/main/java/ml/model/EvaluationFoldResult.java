package ml.model;

public class EvaluationFoldResult {
    private final String classifierName;
    private final boolean applyFS;
    private final boolean applySMOTE;
    private final int seed;
    private final int repeat;
    private final int fold;
    private double accuracy;
    private double precision;
    private double recall;
    private double f1;
    private double auc;
    private double kappa;
    private double npofb20;


    public EvaluationFoldResult(String classifierName, boolean applyFS, boolean applySMOTE,
                                int seed, int repeat, int fold) {
        this.classifierName = classifierName;
        this.applyFS = applyFS;
        this.applySMOTE = applySMOTE;
        this.seed = seed;
        this.repeat = repeat;
        this.fold = fold;
    }

    public String getClassifierName() {
        return classifierName;
    }

    public boolean isApplyFS() {
        return applyFS;
    }

    public boolean isApplySMOTE() {
        return applySMOTE;
    }

    public int getSeed() {
        return seed;
    }

    public int getRepeat() {
        return repeat;
    }

    public int getFold() {
        return fold;
    }

    public double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(double accuracy) {
        this.accuracy = accuracy;
    }

    public double getPrecision() {
        return precision;
    }

    public double getRecall() {
        return recall;
    }

    public void setRecall(double recall) {
        this.recall = recall;
    }

    public double getF1() {
        return f1;
    }

    public void setF1(double f1) {
        this.f1 = f1;
    }

    public double getAuc() {
        return auc;
    }

    public void setAuc(double auc) {
        this.auc = auc;
    }

    public double getKappa() {
        return kappa;
    }

    public void setKappa(double kappa) {
        this.kappa = kappa;
    }

    public double getNpofb20() {
        return npofb20;
    }

    public void setNpofb20(double npofb20) {
        this.npofb20 = npofb20;
    }

    public void setPrecision(double v) { this.precision = v;}
}


