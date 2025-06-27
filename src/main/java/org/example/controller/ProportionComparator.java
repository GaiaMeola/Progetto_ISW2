package org.example.controller;

import org.example.model.Release;
import org.example.model.Ticket;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class ProportionComparator {

    private final PreProcessProportion proportionProcessor = new PreProcessProportion();
    private final List<Ticket> allTickets;

    public ProportionComparator(List<Ticket> allTickets) {
        this.allTickets = allTickets;
    }

    public void evaluate(String outputFilePath) {
        List<String> csvLines = new ArrayList<>();
        csvLines.add("TICKET_KEY;TRUE_IV;EST_IV_INCREMENTAL;CORRECT_INC;" +
                "EST_IV_COLDSTART;CORRECT_COLD;EST_IV_MOVING;CORRECT_MOVING");

        for (int i = 0; i < allTickets.size(); i++) {
            Ticket current = allTickets.get(i);
            List<Ticket> previous = allTickets.subList(0, i); // solo quelli precedenti

            Release fv = current.getFixedVersion();
            Release ov = current.getOpeningVersion();
            Release iv = current.getInjectedVersion(); // può essere null

            if (fv == null || ov == null || iv == null) continue;

            int fvId = fv.getId();
            int ovId = ov.getId();
            int trueIvId = iv.getId();

            JSONObject report = new JSONObject();

            // --- Incremental ---
            double pInc = proportionProcessor.computeProportion(previous, current, true, report);
            double estIvInc = fvId - (fvId - ovId) * pInc;
            boolean correctInc = estIvInc <= trueIvId;

            // --- Cold Start ---
            double pCold = proportionProcessor.computeProportion(Collections.emptyList(), current, true, report);
            double estIvCold = fvId - (fvId - ovId) * pCold;
            boolean correctCold = estIvCold <= trueIvId;

            // --- Moving Window ---
            double pMov = proportionProcessor.computeProportionMovingWindow(previous, current, true, report);
            double estIvMov = fvId - (fvId - ovId) * pMov;
            boolean correctMov = estIvMov <= trueIvId;

            // CSV row
            String row = String.format(Locale.US,
                    "%s;%d;%.3f;%b;%.3f;%b;%.3f;%b",
                    current.getTicketKey(), trueIvId,
                    estIvInc, correctInc,
                    estIvCold, correctCold,
                    estIvMov, correctMov);
            csvLines.add(row);
        }

        try (FileWriter fw = new FileWriter(outputFilePath)) {
            for (String line : csvLines) {
                fw.write(line + "\n");
            }
            System.out.println("Risultati scritti in: " + outputFilePath);
        } catch (IOException e) {
            System.err.println("Errore scrittura file: " + e.getMessage());
        }
    }
}
