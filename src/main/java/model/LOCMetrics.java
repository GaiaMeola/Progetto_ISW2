package model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
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
}
