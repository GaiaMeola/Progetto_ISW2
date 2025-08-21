package org.example.controller;

import org.example.logging.SeLogger;
import org.example.model.Ticket;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreProcessProportion {

    public static final String NAME_OF_THIS_CLASS = PreProcessProportion.class.getName();
    private static final Logger logger = Logger.getLogger(NAME_OF_THIS_CLASS);

    private static final String TICKET_SIZE = "ticket_size";
    private static final String AVERAGE_PROPORTION = "average_proportion";
    private static final String MESSAGE_PROPORTION = "no changes";

    public static final int THRESHOLD_FOR_COLD_START = 5;
    private static final String COLD_START = "COLD_START_PROPORTIONS";
    private static final String COLD_START_MEDIAN = "COLD_START_MEDIAN";
    private static final String PROJECT_ANALYZED = "PROJECTS";
    private static final String COLD_START_ANALYZE = "(COLD_START_ANALYZE)";

    // cold start viene salvato per progetto, non più come double globale
    private static final Map<String, Double> coldStartCache = new HashMap<>();
    private static final ReentrantLock coldStartLock = new ReentrantLock();

    private enum OtherProjects {
        AVRO,
        SYNCOPE,
        STORM,
        TAJO,
        ZOOKEEPER
    }


    private static double incrementalProportionComputation(List<Ticket> filteredTicketsList,
                                                           Ticket ticket, boolean newEntry, boolean computation,
                                                           JSONObject reportJson) {
        if (writeUsedOrNot(ticket, computation, newEntry, reportJson)) return 0.0;

        filteredTicketsList.sort(Comparator.comparing(Ticket::getResolutionDate));
        double totalProportion = getTotalProportion(filteredTicketsList);
        double mean = totalProportion / filteredTicketsList.size();

        if (newEntry) {
            JSONObject sizeAndMean = new JSONObject();
            sizeAndMean.put(TICKET_SIZE, filteredTicketsList.size());
            sizeAndMean.put(AVERAGE_PROPORTION, mean);
            reportJson.put(ticket.getTicketKey(), sizeAndMean);
        }
        return mean;
    }

    private static double getTotalProportion(List<Ticket> tickets) {
        double totalProportion = 0.0;
        for (Ticket correctTicket : tickets) {
            double denominator = (correctTicket.getFixedVersion().getId() != correctTicket.getOpeningVersion().getId())
                    ? (double) correctTicket.getFixedVersion().getId() - correctTicket.getOpeningVersion().getId()
                    : 1.0;

            double propForTicket = ((double) correctTicket.getFixedVersion().getId()
                    - correctTicket.getInjectedVersion().getId()) / denominator;
            totalProportion += propForTicket;
        }
        return totalProportion;
    }

    private static boolean writeUsedOrNot(Ticket ticket, boolean compute, boolean newEntry, JSONObject reportJson) {
        if (!compute && newEntry) {
            JSONObject entry = new JSONObject();
            entry.put(AVERAGE_PROPORTION, MESSAGE_PROPORTION);
            reportJson.put(ticket.getTicketKey(), entry);
            return true;
        }
        return false;
    }

    private static void addProportion(OtherProjects projName, JSONArray array,
                                      @NotNull List<Double> proportions, double proportion) {
        JSONObject entry = new JSONObject();
        entry.put("name", projName.name());
        entry.put("mean_proportion", proportion);
        proportions.add(proportion);
        array.put(entry);
    }

    private static double coldStartProportionComputation(Ticket ticket, boolean doActualComputation,
                                                         JSONObject reportJson) {
        writeUsedOrNot(ticket, doActualComputation, false, reportJson);

        String projectKey = ticket.getTicketKey().split("-")[0]; // es. BOOKKEEPER-123 → BOOKKEEPER

        // se già calcolato per questo progetto → prendo da cache
        if (coldStartCache.containsKey(projectKey)) {
            double cached = coldStartCache.get(projectKey);
            JSONObject entry = new JSONObject();
            entry.put(AVERAGE_PROPORTION, cached);
            entry.put(COLD_START, true);
            reportJson.put(COLD_START_ANALYZE, entry);
            return cached;
        }

        coldStartLock.lock();
        try {
            if (coldStartCache.containsKey(projectKey)) {
                double cached = coldStartCache.get(projectKey);
                JSONObject entry = new JSONObject();
                entry.put(AVERAGE_PROPORTION, cached);
                entry.put(COLD_START, true);
                reportJson.put(COLD_START_ANALYZE, entry);
                return cached;
            }

            logger.log(Level.INFO, "called cold start for project {0}", projectKey);

            List<Double> proportionList = new ArrayList<>();
            JSONArray array = new JSONArray();

            for (OtherProjects projName : OtherProjects.values()) {
                JiraInjection jiraInjection = new JiraInjection(projName.toString());
                try {
                    jiraInjection.injectReleases();
                    jiraInjection.pullIssues();
                    jiraInjection.filterFixedNormally();
                } catch (IOException | URISyntaxException e) {
                    logger.severe(e.getMessage());
                }

                List<Ticket> filteredTickets = jiraInjection.getTicketsWithAffectedVersion();
                if (filteredTickets.size() >= THRESHOLD_FOR_COLD_START) {
                    double proportion = incrementalProportionComputation(
                            filteredTickets, ticket, false, doActualComputation, reportJson
                    );
                    addProportion(projName, array, proportionList, proportion);
                }
            }

            Collections.sort(proportionList);
            double median = 0.0;
            int size = proportionList.size();
            if (size > 0) {
                median = (size % 2 == 0)
                        ? (proportionList.get((size / 2) - 1) + proportionList.get(size / 2)) / 2
                        : proportionList.get(size / 2);
            }

            JSONObject entry = new JSONObject();
            entry.put(COLD_START_MEDIAN, median);
            entry.put(PROJECT_ANALYZED, array);
            reportJson.put(COLD_START_ANALYZE, entry);

            coldStartCache.put(projectKey, median); // salva in cache
            SeLogger.getInstance().getLogger()
                    .log(java.util.logging.Level.INFO, "cold start ({0}): {1}", new Object[]{projectKey, median});
            return median;

        } finally {
            coldStartLock.unlock();
        }
    }

    public double computeProportion(List<Ticket> fixedTicketsList,
                                    Ticket ticket, boolean doActualComputation,
                                    JSONObject reportJson) {
        if (fixedTicketsList.size() >= THRESHOLD_FOR_COLD_START) {
            return incrementalProportionComputation(fixedTicketsList, ticket, true, doActualComputation, reportJson);
        } else {
            return coldStartProportionComputation(ticket, doActualComputation, reportJson);
        }
    }

    public double computeProportionMovingWindow(List<Ticket> previousTickets,
                                                Ticket ticket,
                                                boolean doActualComputation,
                                                JSONObject reportJson) {
        if (previousTickets == null || previousTickets.isEmpty()) {
            return coldStartProportionComputation(ticket, doActualComputation, reportJson);
        }

        // 1% oppure almeno 5
        int windowSize = Math.max(5, previousTickets.size() / 100);
        List<Ticket> window = previousTickets.subList(
                Math.max(0, previousTickets.size() - windowSize),
                previousTickets.size()
        );

        return incrementalProportionComputation(window, ticket, true, doActualComputation, reportJson);
    }
}