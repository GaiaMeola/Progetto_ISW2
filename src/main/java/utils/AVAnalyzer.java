package utils;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class AVAnalyzer {

    public static void analyzeAndSave(List<BugReport> reports) {
        int total = 0;
        int missingAV = 0;
        int inconsistentAV = 0;

        for (BugReport report : reports) {
            if (!report.hasGitFix()) continue;

            total++;
            List<Integer> avs = report.getAffectedVersions();

            if (avs == null || avs.isEmpty()) {
                missingAV++;
                continue;
            }

            int minAV = avs.stream().min(Integer::compareTo).orElse(Integer.MAX_VALUE);
            if (minAV > report.getOpeningVersion()) {
                inconsistentAV++;
            }
        }

        StringBuilder output = new StringBuilder();
        output.append("Totale bug reports analizzati: ").append(total).append("\n");
        output.append("Bug reports con AV mancanti: ").append(missingAV).append(" (").append(percent(missingAV, total)).append("%)\n");
        output.append("Bug reports con AV inconsistenti: ").append(inconsistentAV).append(" (").append(percent(inconsistentAV, total)).append("%)\n");
        output.append("Totale non affidabili (missing + inconsistent): ").append((missingAV + inconsistentAV)).append(" (")
                .append(percent(missingAV + inconsistentAV, total)).append("%)\n");

        String fileName = "AV_Analysis_" + Initializer.getInstance().getClass() + ".txt";
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(output.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String percent(int num, int total) {
        if (total == 0) return "0.0";
        return String.format("%.1f", (100.0 * num) / total);
    }
}
