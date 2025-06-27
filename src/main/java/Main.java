import controller.JiraInjection;
import controller.ProportionComparator;
import model.Ticket;

import controller.ProgramFlow;
import logging.SeLogger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.logging.Logger;

public class Main {

    public static final String SYS_PMD_HOME = "SYS_PMD_HOME";

    public static void main(String @NotNull [] args) {
        Logger logger = SeLogger.getInstance().getLogger();

        // Controllo argomenti: voglio 2 argomenti (file input e projectName Jira)
        if (args.length != 2) {
            logger.severe("Usage: Main <input-file> <jira-project-name>");
            System.exit(-1);
        }

        String inputFile = args[0];
        String jiraProjectName = args[1];

        // Controllo variabile d'ambiente PMD
        String pmdHome = System.getenv(SYS_PMD_HOME);
        if (pmdHome == null || pmdHome.isEmpty()) {
            logger.severe("Environment variable " + SYS_PMD_HOME + " not set");
            System.exit(-1);
        }

        // Verifico che pmd sia eseguibile
        try {
            String pmdCommand = pmdHome + File.separator + "bin" + File.separator + (isWindows() ? "pmd.bat" : "pmd");
            new ProcessBuilder(
                    pmdCommand,
                    "--",
                    "version"
            ).start().waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.exit(-1);
        } catch (IOException e) {
            logger.severe("Cannot find pmd in the system: " + e.getMessage());
            System.exit(-1);
        }

        logger.info("____________________________START____________________________");
        long startTime = System.nanoTime();

        // Eseguo ProgramFlow con file input
        ProgramFlow.run(inputFile);

        // Eseguo la logica Jira
        try {
            JiraInjection jiraInjection = new JiraInjection(jiraProjectName);
            jiraInjection.injectReleases();
            jiraInjection.injectTickets();

            List<Ticket> tickets = jiraInjection.getFixedTickets();

            ProportionComparator comparator = new ProportionComparator(tickets);
            String outputFile = "output_comparison.csv";
            comparator.evaluate(outputFile);

            logger.info("Jira comparison finished. Output saved to " + outputFile);

        } catch (IOException | URISyntaxException e) {
            logger.severe("Error during Jira processing: " + e.getMessage());
            e.printStackTrace();
        }

        long endTime = System.nanoTime();
        String finalMessage = SeLogger.ELAPSED_TIME + ((endTime - startTime) / Math.pow(10, 9)) + SeLogger.SECONDS;
        logger.info(finalMessage);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
