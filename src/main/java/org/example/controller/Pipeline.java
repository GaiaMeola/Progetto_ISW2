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

    public Pipeline(int threadId, CountDownLatch countDownLatch, @NotNull String projectName, String projectUrl){
        this.targetName = projectName.toUpperCase();
        this.targetUrl = projectUrl;
        this.logger = SeLogger.getInstance().getLogger();
        this.threadIdentity = threadId + "-" + projectName;
        this.latch = countDownLatch;
    }

    private void injectAndProcess() {
        logInfo(() -> "Starting processing project");

        if (targetName == null || targetName.isBlank()) {
            logSevere(() -> "targetName è null o vuoto!");
            return;
        }
        if (targetUrl == null || targetUrl.isBlank()) {
            logSevere(() -> "targetUrl è null o vuoto!");
            return;
        }

        long overallStart = System.nanoTime();
        final String seconds = " seconds";

        try {
            // Jira Injection
            JiraInjection jiraInjection = new JiraInjection(this.targetName);
            measureExecutionChecked("Releases injection", jiraInjection::injectReleases);
            List<Release> releases = measureExecutionWithResultChecked(jiraInjection::getReleases);

            // Git Injection
            GitInjection gitInjection = new GitInjection(this.targetName, this.targetUrl, releases);
            measureExecutionChecked("Commits injection", gitInjection::injectCommits);

//            // Tickets Injection & preprocessing
            measureExecutionChecked("Tickets injection and commit preprocessing", () -> {
                jiraInjection.injectTickets();
                gitInjection.setTickets(jiraInjection.getFixedTickets());
                gitInjection.preprocessCommitsWithIssue();
            });


            // forse da aggiungere: confronto tra i vari proportion calcolati

                        // Java Class Injection
                        measureExecutionChecked("Java class injection", gitInjection::preprocessJavaClasses);
            //            gitInjection.closeRepo();
            //
            //            // Export tickets and commits
            //            exportTicketsAndCommits(targetName, jiraInjection.getFixedTickets(), Sink.FileExtension.JSON);
            //            exportTicketsAndCommits(targetName, jiraInjection.getFixedTickets(), Sink.FileExtension.CSV);
            //
            //
            //
            //            // Preprocessing Project
            //            PreprocessMetrics preprocessMetrics = new PreprocessMetrics(gitInjection);
            //            measureExecutionChecked("Preprocessing project", () -> {
            //                Sink.serializeProjectAsCsv(gitInjection);
            //                preprocessMetrics.start();
            //                storeCurrentData(jiraInjection, gitInjection);
            //            });
            //
            //            // Dataset Generation
            //            measureExecutionChecked("Dataset generation", () -> preprocessMetrics.generateDataset(targetName));
            //
            //            // **Weka Classification Phase**
            //            measureExecutionChecked("Weka classification", () -> {
            //                WekaProcessing wekaProcessing = new WekaProcessing(this.targetName,
            //                        jiraInjection.getReleases().size() / 2);
            //                wekaProcessing.classify();
            //                wekaProcessing.sinkResults();
            //            });

        } catch (Exception e) {
            logSevere(() -> "Error: " + e.getMessage() + " (" + e.getClass().getSimpleName() + ")");
            // se vuoi la trace completa in log dettagliati: logger.log(Level.FINEST, "stacktrace", e);
        } finally {
            long overallEnd = System.nanoTime();
            logInfo(() -> "Total processing took: " + getTimeInSeconds(overallStart, overallEnd) + seconds);
            // ATTENZIONE: il countDown lo facciamo in run() (non qui) per evitare doppio decremento
        }
    }

    // prefisso comune inserito nei messaggi per chiarezza in multi-thread
    private String logPrefix() {
        return String.format("[%s][%s] ", threadIdentity, targetName);
    }

    // Sonar-friendly lazy logging helpers
    private void logInfo(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(logPrefix() + msgSupplier.get());
        }
    }

    private void logWarning(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.warning(logPrefix() + msgSupplier.get());
        }
    }

    private void logSevere(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(logPrefix() + msgSupplier.get());
        }
    }

    /**
     * Misura il tempo di esecuzione di un Runnable che può lanciare checked exceptions
     */
    private void measureExecutionChecked(String taskName, CheckedRunnable task) throws Exception {
        long start = System.nanoTime();
        logInfo(() -> "Start " + taskName);
        task.run();
        long end = System.nanoTime();
        logInfo(() -> taskName + " took: " + getTimeInSeconds(start, end) + " seconds");
    }

    /**
     * Misura il tempo di esecuzione di un Supplier con checked exception
     */
    private <T> T measureExecutionWithResultChecked(CheckedSupplier<T> supplier) throws Exception {
        long start = System.nanoTime();
        T result = supplier.get();
        long end = System.nanoTime();
        logInfo(() -> "Execution took: " + getTimeInSeconds(start, end) + " seconds");
        return result;
    }

    /**
     * Functional interfaces per lambda con checked exceptions
     */
    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
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

        try {
            this.injectAndProcess();
        } finally {
            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;
            // Sonar-friendly
            logger.info(() -> String.format("%s Elapsed Time: %.3f seconds", logPrefix(), elapsedSeconds));
            latch.countDown();
        }
    }
}