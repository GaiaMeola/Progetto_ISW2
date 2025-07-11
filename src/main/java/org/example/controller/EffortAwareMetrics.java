package org.example.controller;

import java.util.List;
import org.example.model.Module;

public class EffortAwareMetrics {

    /**
     * Calcola POFB@x dato un elenco di moduli.
     * @param modules lista di moduli con info su LOC, rischio e presenza bug
     * @param xPercent valore tra 0 e 100 (es: 20 per POFB@20)
     * @return percentuale di bug trovati ispezionando top x% LOC
     */
    public static double calculatePOFB(List<Module> modules, double xPercent) {
        // Ordina i moduli per rischio decrescente
        modules.sort((a, b) -> Double.compare(b.getRisk(), a.getRisk()));

        // Calcola il totale di LOC da ispezionare
        int totalLOC = modules.stream().mapToInt(Module::getLoc).sum();
        int budgetLOC = (int) Math.ceil((xPercent / 100.0) * totalLOC);

        // Calcola il numero totale di bug
        long totalBugs = modules.stream().filter(Module::isBuggy).count();

        int inspectedLOC = 0;
        long foundBugs = 0;

        for (Module m : modules) {
            if (inspectedLOC + m.getLoc() > budgetLOC) break;

            inspectedLOC += m.getLoc();
            if (m.isBuggy()) {
                foundBugs++;
            }
        }

        return totalBugs == 0 ? 0 : (double) foundBugs / totalBugs * 100.0;
    }
}