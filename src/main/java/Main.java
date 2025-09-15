import org.example.controller.ProgramFlow;
import org.example.logging.SeLogger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    public static final String SYS_PMD_HOME = "SYS_PMD_HOME";

    public static void main(String @NotNull [] args) {

        //recupero del Logger
        Logger logger = SeLogger.getInstance().getLogger();

        try {
            validateArguments(args); //file di input
            String pmdHome = validateEnvironment(); //variabile d'ambiente
            verifyPmdInstallation(pmdHome);

            logger.info("____________________________START____________________________");
            long startTime = System.nanoTime();

            ProgramFlow.run(args[0]);

            long endTime = System.nanoTime();
            double elapsedSeconds = (endTime - startTime) / 1_000_000_000.0;

            logger.log(Level.INFO,
                    "{0}{1}{2}",
                    new Object[]{SeLogger.ELAPSED_TIME, String.format("%.2f", elapsedSeconds), SeLogger.SECONDS});

        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.log(Level.SEVERE, "Execution interrupted", e);
            System.exit(2);
        } catch (Exception e) {
        logger.log(Level.SEVERE, "Unexpected error during execution: " + e.getMessage(), e);
        System.exit(2);
    }
}

    private static void validateArguments(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <input-file>");
        }
    }

    private static String validateEnvironment() {
        String pmdHome = System.getenv(SYS_PMD_HOME);
        if (pmdHome == null || pmdHome.isEmpty()) {
            throw new IllegalArgumentException("Environment variable " + SYS_PMD_HOME + " not set");
        }
        return pmdHome;
    }

    private static void verifyPmdInstallation(String pmdHome) throws IOException, InterruptedException {
        String pmdCommand = pmdHome + File.separator + "bin" + File.separator + (isWindows() ? "pmd.bat" : "pmd");
        Process process = new ProcessBuilder(pmdCommand, "--version").start(); // FIX qui
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("PMD command failed with exit code " + exitCode);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}