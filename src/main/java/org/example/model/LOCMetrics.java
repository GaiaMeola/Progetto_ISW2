package org.example.model;

public class LOCMetrics { //to count lines of codes
    private int maxVal;
    private double avgVal;
    private int val;

    public void updateMetrics(int newValue) {
        this.val += newValue;
        if (newValue > this.maxVal) {
            this.maxVal = newValue;
        }
    }

    @Override
    public String toString() {
        return "LOCMetrics{" +
                "maxVal=" + maxVal +
                ", avgVal=" + avgVal +
                ", val=" + val +
                '}';
    }

    public int getMaxVal() {
        return maxVal;
    }

    public void setMaxVal(int maxVal) {
        this.maxVal = maxVal;
    }

    public double getAvgVal() {
        return avgVal;
    }

    public void setAvgVal(double avgVal) {
        this.avgVal = avgVal;
    }

    public int getVal() {
        return val;
    }

    public void setVal(int val) {
        this.val = val;
    }
}
