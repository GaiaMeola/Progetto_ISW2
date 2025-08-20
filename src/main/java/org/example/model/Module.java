package org.example.model;

public class Module {
    private String id;
    private boolean buggy;
    private int loc;
    private double risk;

    public Module(String id, boolean buggy, int loc, double risk) {
        this.id = id;
        this.buggy = buggy;
        this.loc = loc;
        this.risk = risk;
    }

    // Getters
    public String getId() {
        return id;
    }

    public boolean isBuggy() {
        return buggy;
    }

    public int getLoc() {
        return loc;
    }

    public double getRisk() {
        return risk;
    }

    // Setters (se servono)
    public void setId(String id) {
        this.id = id;
    }

    public void setBuggy(boolean buggy) {
        this.buggy = buggy;
    }

    public void setLoc(int loc) {
        this.loc = loc;
    }

    public void setRisk(double risk) {
        this.risk = risk;
    }
}