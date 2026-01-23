package analyzer.csv;

import analyzer.model.MethodInfo;
import util.Configuration;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class CsvHandler {

    private static final String[] HEADER = {
            "Project", "Method", "ReleaseID", "LOC", "CyclomaticComplexity", "CognitiveComplexity", "Number of Smells", "ParameterCount", "NestingDepth", "StatementCount",
            "LocalVariableCount", "ReturnTypeComplexity", "MethodHistories",
            "StmtAdded", "StmtDeleted", "Churn", "DistinctAuthors", "Bugginess"
    };

    public void writeCsv(String outputPath, List<MethodInfo> methods) {
        // 1. Prepariamo il file e creiamo le cartelle se mancano
        java.io.File file = new java.io.File(outputPath);
        java.io.File parent = file.getParentFile();

        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // 2. Usiamo l'oggetto 'file' invece della stringa 'outputPath'
        try (FileWriter writer = new FileWriter(file)) {

            // Scrive l'intestazione
            writer.append(String.join(";", HEADER));
            writer.append("\n");

            for (MethodInfo method : methods) {
                writer.append(method.getProjectName()).append(";");
                writer.append(method.getMethodName()).append(";");
                writer.append(method.getReleaseId()).append(";");
                writer.append(String.valueOf(method.getLoc())).append(";");
                writer.append(String.valueOf(method.getCyclomaticComplexity())).append(";");
                writer.append(String.valueOf(method.getCognitiveComplexity())).append(";");
                writer.append(String.valueOf(method.getNumberOfSmells())).append(";");
                writer.append(String.valueOf(method.getParameterCount())).append(";");
                writer.append(String.valueOf(method.getNestingDepth())).append(";");
                writer.append(String.valueOf(method.getStatementCount())).append(";");
                writer.append(String.valueOf(method.getLocalVariableCount())).append(";");
                writer.append(String.valueOf(method.getReturnTypeComplexity())).append(";");
                writer.append(String.valueOf(method.getMethodHistories())).append(";");
                writer.append(String.valueOf(method.getStmtAdded())).append(";");
                writer.append(String.valueOf(method.getStmtDeleted())).append(";");
                writer.append(String.valueOf(method.getChurn())).append(";");
                writer.append(String.valueOf(method.getDistinctAuthors())).append(";");
                writer.append(method.isBugginess() ? "Yes" : "No");
                writer.append("\n");
            }

        } catch (IOException e) {
            // Logghiamo l'errore reale per sicurezza
            Configuration.logger.severe("Errore critico nella scrittura del CSV: " + e.getMessage());
        }
    }

}

