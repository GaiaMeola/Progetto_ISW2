package org.example.model;

public class Metrics {
    private final LOCMetrics removedLOCMetrics;
    private final LOCMetrics churnMetrics;
    private final LOCMetrics addedLOCMetrics;

    private final LOCMetrics touchedLOCMetrics;
    private boolean bug;
    private int size;
    private int numberOfRevisions;
    private int numberOfDefectFixes;
    private int numberOfAuthors;

    public Metrics() {
        bug = false;
        size = 0;
        numberOfRevisions = 0;
        numberOfDefectFixes = 0;
        numberOfAuthors = 0;
        removedLOCMetrics = new LOCMetrics();
        churnMetrics = new LOCMetrics();
        addedLOCMetrics = new LOCMetrics();
        touchedLOCMetrics = new LOCMetrics();
    } //default


    public void setAddedLOCMetrics(int addedLOC, int maxAddedLOC, double avgAddedLOC) {
        this.addedLOCMetrics.setVal(addedLOC);
        this.addedLOCMetrics.setMaxVal(maxAddedLOC);
        this.addedLOCMetrics.setAvgVal(avgAddedLOC);
    }

    public void setRemovedLOCMetrics(int removedLOC, int maxRemovedLOC, double avgRemovedLOC) {
        this.removedLOCMetrics.setVal(removedLOC);
        this.removedLOCMetrics.setMaxVal(maxRemovedLOC);
        this.removedLOCMetrics.setAvgVal(avgRemovedLOC);
    }

    public void setChurnMetrics(int churn, int maxChurningFactor, double avgChurningFactor) {
        this.churnMetrics.setVal(churn);
        this.churnMetrics.setMaxVal(maxChurningFactor);
        this.churnMetrics.setAvgVal(avgChurningFactor);
    }

    public void setTouchedLOCMetrics(int touchedLOC, int maxTouchedLOC, double avgTouchedLOC) {
        this.touchedLOCMetrics.setVal(touchedLOC);
        this.touchedLOCMetrics.setMaxVal(maxTouchedLOC);
        this.touchedLOCMetrics.setAvgVal(avgTouchedLOC);

    }

    @Override
    public String toString() {
        return "bug=" + bug;
    }

    public LOCMetrics getRemovedLOCMetrics() {
        return removedLOCMetrics;
    }

    public LOCMetrics getChurnMetrics() {
        return churnMetrics;
    }

    public LOCMetrics getAddedLOCMetrics() {
        return addedLOCMetrics;
    }

    public LOCMetrics getTouchedLOCMetrics() {
        return touchedLOCMetrics;
    }

    public boolean isBug() {
        return bug;
    }

    public void setBug(boolean bug) {
        this.bug = bug;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getNumberOfRevisions() {
        return numberOfRevisions;
    }

    public void setNumberOfRevisions(int numberOfRevisions) {
        this.numberOfRevisions = numberOfRevisions;
    }

    public int getNumberOfDefectFixes() {
        return numberOfDefectFixes;
    }

    public void setNumberOfDefectFixes(int numberOfDefectFixes) {
        this.numberOfDefectFixes = numberOfDefectFixes;
    }

    public int getNumberOfAuthors() {
        return numberOfAuthors;
    }

    public void setNumberOfAuthors(int numberOfAuthors) {
        this.numberOfAuthors = numberOfAuthors;
    }
}
