package org.example.controller;

import org.example.logging.SeLogger;
import org.example.model.Release;
import org.example.model.Ticket;
import org.example.utilities.Sink;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class Pipeline implements Runnable{

    private final String targetName;
    private final String targetUrl;
    private final Logger logger;
    private final CountDownLatch latch;
    private final String threadIdentity;
    private int msgCount = 0;
    public Pipeline(int threadId, CountDownLatch countDownLatch, @NotNull String projectName, String projectUrl){
        this.targetName = projectName.toUpperCase();
        this.targetUrl = projectUrl;
        this.logger = SeLogger.getInstance().getLogger();
        this.threadIdentity = threadId + "-" + projectName;
        this.latch = countDownLatch;
    }

    private void injectAndProcess() {


        String info = getPipeMsg("starting processing project");
        logger.info(info);

        // Controllo iniziale dei parametri
        if (targetName == null || targetName.isBlank()) {
            logger.severe("targetName è null o vuoto!");
            return;
        }
        if (targetUrl == null || targetUrl.isBlank()) {
            logger.severe("targetUrl è null o vuoto!");
            return;
        }

        long overallStart = System.nanoTime();

        final String seconds = " seconds";
        try {
            long start;
            long end;

            // Start Injection Phase
            start = System.nanoTime();
            info = getPipeMsg("starting injection phase");
            logger.info(info);

            JiraInjection jiraInjection = new JiraInjection(this.targetName);
            logger.info("Start injectReleases");
            jiraInjection.injectReleases();
            logger.info("End injectReleases");
            end = System.nanoTime();
            info = getPipeMsg("start releases injection took: " + getTimeInSeconds(start, end) + seconds);
            logger.info(info);

            start = System.nanoTime();
            logger.info("Start getReleases");
            List<Release> releases = jiraInjection.getReleases();
            logger.info("End getReleases");
            end = System.nanoTime();
            info = getPipeMsg("releases injection complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);

            // Commit Injection
            start = System.nanoTime();
            info = getPipeMsg("start commit injection");
            logger.info(info);

            GitInjection gitInjection = new GitInjection(this.targetName, this.targetUrl, releases);
            logger.info("Start injectCommits");
            gitInjection.injectCommits();
            logger.info("End injectCommits");
            end = System.nanoTime();
            info = getPipeMsg("commits injection complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);

            start = System.nanoTime();
            logger.info("Start injectTickets");
            jiraInjection.injectTickets();
            gitInjection.setTickets(jiraInjection.getFixedTickets());
            gitInjection.preprocessCommitsWithIssue();
            logger.info("End injectTickets");
            end = System.nanoTime();
            info = getPipeMsg("ticket injection and commit preprocessing took: " +
                    getTimeInSeconds(start, end) + seconds);
            logger.info(info);

            // Java Class Injection
            start = System.nanoTime();
            info = getPipeMsg("start java class injection");
            logger.info(info);

            logger.info("Start preprocessJavaClasses");
            gitInjection.preprocessJavaClasses();
            logger.info("End preprocessJavaClasses");

            end = System.nanoTime();
            info = getPipeMsg("java class injection complete took: " + getTimeInSeconds(start, end)
                    + seconds);
            logger.info(info);
            gitInjection.closeRepo();

            // **Export tickets and commits**
            exportTicketsAndCommits(targetName, jiraInjection.getFixedTickets(), Sink.FileExtension.JSON);
            exportTicketsAndCommits(targetName, jiraInjection.getFixedTickets(), Sink.FileExtension.CSV);

            // Preprocessing Project
            start = System.nanoTime();
            info = getPipeMsg("start preprocessing project");
            logger.info(info);
            // now must start preprocessing also Method Metrics
            Sink.serializeProjectAsCsv(gitInjection);
            PreprocessMetrics preprocessMetrics = new PreprocessMetrics(gitInjection);
            preprocessMetrics.start();
            storeCurrentData(jiraInjection, gitInjection);
            end = System.nanoTime();
            info = getPipeMsg("preprocessing project complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);

            info = getPipeMsg("injection phase complete");
            logger.info(info);

            // Dataset Generation
            start = System.nanoTime();
            preprocessMetrics.generateDataset(targetName);
            end = System.nanoTime();
            info = getPipeMsg("dataset generation complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);

            /*
            // Classification Phase
            start = System.nanoTime();
            info = getPipeMsg("start processing phase");
            logger.info(info);

            info = getPipeMsg("starting classification");
            logger.info(info);

            WekaProcessing wekaProcessing = new WekaProcessing(this.targetName,
                    jiraInjection.getReleases().size() / 2);
            wekaProcessing.classify();
            end = System.nanoTime();
            info = getPipeMsg("classification complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);

            // Sinking Results
            start = System.nanoTime();
            wekaProcessing.sinkResults();
            end = System.nanoTime();
            info = getPipeMsg("sink results complete took: " + getTimeInSeconds(start, end) +
                    seconds);
            logger.info(info);
             */

        } catch (Exception e) {
            logger.severe("error: " + e.getMessage());
        } finally {
            long overallEnd = System.nanoTime();
            info = getPipeMsg("total processing took: " + getTimeInSeconds(overallStart, overallEnd) +
                    seconds);
            logger.info(info);

        }
    }

    private String getPipeMsg(String msg) {
        return String.format("{\"Thread-%s-%d\": {\"project\": %s, \"message\": \"%s\"}}",
                threadIdentity,
                msgCount++,
                this.targetName, msg);
    }

    // Helper method to convert nanoseconds to seconds
    @Contract(pure = true)
    private @NotNull String getTimeInSeconds(long start, long end) {
        return String.format("%.2f", (end - start) / 1_000_000_000.0);
    }

    private void storeCurrentData(@NotNull JiraInjection jiraInjection, @NotNull GitInjection gitInjection) {

        Sink.serializeToJson(this.targetName, "Releases", new JSONObject(jiraInjection.getMapReleases()),
                Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Tickets",  new JSONObject(gitInjection.getMapTickets()),
                Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Commits", new JSONObject(gitInjection.getMapCommits()),
                Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Summary", new JSONObject(gitInjection.getMapSummary()),
                Sink.FileExtension.JSON);
    }

    public void exportTicketsAndCommits(String projectName, List<Ticket> tickets, Sink.FileExtension fe) {
        String filename = projectName.toLowerCase(Locale.getDefault()) + "_tickets_commits";
        try {
            Sink.serializeTicketsAndCommits(projectName, filename, tickets, fe);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        long startTime = System.nanoTime();
        this.injectAndProcess();
        long endTime = System.nanoTime();
        String finalMessage = this.threadIdentity+ SeLogger.ELAPSED_TIME + ((endTime - startTime) / Math.pow(10, 9)) +
                SeLogger.SECONDS;
        logger.info(finalMessage);
        this.latch.countDown();

    }
}