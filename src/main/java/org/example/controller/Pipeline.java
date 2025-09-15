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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Pipeline implements Runnable {

    private final String targetName;
    private final String targetUrl;
    private final Logger logger;
    private final CountDownLatch latch;
    private final String threadIdentity;

    public Pipeline(int threadId, CountDownLatch countDownLatch, @NotNull String projectName, String projectUrl) {
        this.targetName = projectName.toUpperCase();
        this.targetUrl = projectUrl;
        this.logger = SeLogger.getInstance().getLogger();
        this.threadIdentity = threadId + "-" + projectName;
        this.latch = countDownLatch;
    }

    private void injectAndProcess() {
        logInfo(() -> "Starting processing project");

        if (targetName == null || targetName.isBlank()) {
            logSevere(() -> "targetName è null o vuoto! url=" + targetUrl);
            return;
        }
        if (targetUrl == null || targetUrl.isBlank()) {
            logSevere(() -> "targetUrl è null o vuoto! name=" + targetName);
            return;
        }

        long overallStart = System.nanoTime();

        try {
            // Jira Injection --> popolamento delle release di un progetto
            JiraInjection jiraInjection = new JiraInjection(this.targetName);
            measureExecutionChecked("Releases injection", jiraInjection::injectReleases);

            //recupero della lista di release di un progetto
            List<Release> releases = measureExecutionWithResultChecked(jiraInjection::getReleases);

            try (GitInjection gitInjection = new GitInjection(this.targetName, this.targetUrl, releases)) {
                // Commits injection --> popolamento dei commits di un progetto
                measureExecutionChecked("Commits injection", gitInjection::injectCommits);

                // Tickets Injection & preprocessing
                measureExecutionChecked("Tickets injection and commit preprocessing", () -> {
                    jiraInjection.injectTickets();
                    gitInjection.setTickets(jiraInjection.getFixedTickets());
                    gitInjection.preprocessCommitsWithIssue();
                });

                // Java Class Injection
                measureExecutionChecked("Java class injection", gitInjection::preprocessJavaClasses);

                // Log conteggi
                logInfo(() -> "Releases count: " + jiraInjection.getMapReleases().size());
                logInfo(() -> "Tickets count: " + gitInjection.getMapTickets().size());
                logInfo(() -> "Commits count: " + gitInjection.getMapCommits().size());
                logInfo(() -> "Summary count: " + gitInjection.getMapSummary().size());
                logInfo(() -> "Fixed tickets count: " + jiraInjection.getFixedTickets().size());

                // Salvataggio dati base
                measureExecutionChecked("Store injection data", () -> storeCurrentData(jiraInjection, gitInjection));

                // Export tickets_commits
                measureExecutionChecked("Export tickets_commits", () ->
                        exportTicketsAndCommits(targetName, jiraInjection.getFixedTickets(), Sink.FileExtension.JSON));

//                // 🔹 Preprocessing Project
//                measureExecutionChecked("Preprocessing project", () -> {
//                    Sink.serializeProjectAsCsv(gitInjection);
//                    PreprocessMetrics preprocessMetrics = new PreprocessMetrics(gitInjection);
//                    preprocessMetrics.start();
//                    storeCurrentData(jiraInjection, gitInjection);
//                    preprocessMetrics.generateDataset(targetName);
//
//                    // 🔹 Classification Phase
//                    WekaProcessing = new WekaProcessing(this.targetName,
//                            jiraInjection.getReleases().size() / 2);
//                    wekaProcessing.classify();
//                    wekaProcessing.sinkResults();
//                });
            }

        } catch (IOException e) {
            logSevere(() -> "I/O error in pipeline: " + e.getMessage());
            throw new RuntimeException("Pipeline I/O failed for project " + targetName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(() -> "Pipeline interrupted: " + e.getMessage());
            throw new RuntimeException("Pipeline interrupted for project " + targetName, e);
        } catch (RuntimeException e) {
            logSevere(() -> "Runtime error in pipeline: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            logSevere(() -> "Unexpected error in pipeline: " + e.getMessage());
            throw new RuntimeException("Pipeline failed for project " + targetName, e);
        } finally {
            logInfo(() -> "Total processing took: " + getTimeInSeconds(overallStart, System.nanoTime()) + " seconds");
        }
    }

    private String logPrefix() {
        return String.format("[%s][%s] ", threadIdentity, targetName);
    }

    private void logInfo(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(logPrefix() + msgSupplier.get());
        }
    }

    private void logSevere(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(logPrefix() + msgSupplier.get());
        }
    }

    private void measureExecutionChecked(String taskName, CheckedRunnable task) throws Exception {
        long start = System.nanoTime();
        logInfo(() -> "Start " + taskName);
        try {
            task.run();
        } finally {
            logInfo(() -> taskName + " took: " + getTimeInSeconds(start, System.nanoTime()) + " seconds");
        }
    }

    private <T> T measureExecutionWithResultChecked(CheckedSupplier<T> supplier) throws Exception {
        long start = System.nanoTime();
        logInfo(() -> "Start " + "Get releases");
        T result = supplier.get();
        logInfo(() -> "Get releases" + " took: " + getTimeInSeconds(start, System.nanoTime()) + " seconds");
        return result;
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Contract(pure = true)
    private @NotNull String getTimeInSeconds(long start, long end) {
        return String.format("%.2f", (end - start) / 1_000_000_000.0);
    }

    private void storeCurrentData(@NotNull JiraInjection jiraInjection, @NotNull GitInjection gitInjection) {
        Sink.serializeToJson(this.targetName, "Releases", new JSONObject(jiraInjection.getMapReleases()), Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Tickets", new JSONObject(gitInjection.getMapTickets()), Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Commits", new JSONObject(gitInjection.getMapCommits()), Sink.FileExtension.JSON);
        Sink.serializeToJson(this.targetName, "Summary", new JSONObject(gitInjection.getMapSummary()), Sink.FileExtension.JSON);
    }

    public void exportTicketsAndCommits(String projectName, List<Ticket> tickets, Sink.FileExtension fe) throws IOException {
        String filename = projectName.toLowerCase(Locale.getDefault()) + "_tickets_commits";
        Sink.serializeTicketsAndCommits(projectName, filename, tickets, fe);
    }

    @Override
    public void run() {
        long startTime = System.nanoTime();
        String oldName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadIdentity);

        try {
            this.injectAndProcess();
        } finally {
            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            logger.info(() -> String.format("%s Elapsed Time: %.3f seconds", logPrefix(), elapsedSeconds));
            latch.countDown();
            Thread.currentThread().setName(oldName);
        }
    }
}