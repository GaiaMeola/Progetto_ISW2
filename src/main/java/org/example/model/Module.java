package org.example.model;

/*devo tenere traccia di una lista di moduli*/
public class Module {
    public String id;
    /*identificativo del modulo, classe, file oppure metodo*/
    public boolean buggy;
    public int loc;
    public double risk; // probabilità di essere buggy (output classificatore)

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

    public Module(String id, boolean buggy, int loc, double risk) {
        this.id = id;
        this.buggy = buggy;
        this.loc = loc;
        this.risk = risk;
    }
}
