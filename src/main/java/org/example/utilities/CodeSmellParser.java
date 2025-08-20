package org.example.utilities;

import org.example.model.JavaClass;
import org.example.model.Release;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CodeSmellParser {

    private static final String CSV_SEPARATOR = ",";

    private CodeSmellParser() {
        throw new IllegalStateException("Utility class");
    }

    public static void  parseCsvFile(String csvFilePath, @NotNull List<JavaClass> classes) {

        List<JavaCsvInfo> javaCsvInfos = new ArrayList<>();
        extractCsvInfo(csvFilePath, javaCsvInfos);

        for (JavaClass jc : classes) {

            javaCsvInfos.stream().filter(
                    javaCsvInfo -> javaCsvInfo.getFilename().contains(jc.getName())
            ).forEach(
                    info -> jc.getMethodsMetrics().forEach(
                            (key, value) -> {
                                if (info.getLine() >= value.getBeginLine()
                                        && info.getLine() <= value.getEndLine()){
                                    value.incCodeSmells();
                                }
                            }
                    )
            );
        }


    }

    private static void extractCsvInfo(String csvFilePath, List<JavaCsvInfo> javaCsvInfos) {
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String row = reader.readLine();
            String[] columns = row.split(CSV_SEPARATOR);
            checkRowStructure(columns);
            while ((row = reader.readLine()) != null){
                javaCsvInfos.add(new JavaCsvInfo(row));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("File not found: " + csvFilePath + ": " + e.getMessage());
        }
    }

    private static void checkRowStructure(String @NotNull [] columns) {
        if (columns.length != CsvHeader.values().length){
            throw new IllegalArgumentException("bad csv file format passed");
        }
        for (CsvHeader header : CsvHeader.values()) {
            if ( !columns[header.ordinal()].equals(header.getValue())){
                throw new IllegalArgumentException("header not match: actual=" + columns[header.ordinal()] +
                        ", expected=" + header.getValue());
            }
        }
    }

    public static void extractCodeSmell(Map<Release, List<JavaClass>> javaClassPerRelease, String project) {

    }

    private static class JavaCsvInfo{
        private String problem;
        private String packagePath;
        private String filename;
        private String priority;
        private int line;

        public JavaCsvInfo(@NotNull String row) {
            String[] values = row.split(CSV_SEPARATOR);
            problem = values[CsvHeader.PROBLEM.ordinal()];
            packagePath = values[CsvHeader.PACKAGE.ordinal()];
            filename = values[CsvHeader.FILE.ordinal()];
            priority = values[CsvHeader.PRIORITY.ordinal()];
            line = Integer.parseInt(values[CsvHeader.LINE.ordinal()].replace("\"", ""));
        }

        public String getProblem() {
            return problem;
        }

        public void setProblem(String problem) {
            this.problem = problem;
        }

        public String getPackagePath() {
            return packagePath;
        }

        public void setPackagePath(String packagePath) {
            this.packagePath = packagePath;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public int getLine() {
            return line;
        }

        public void setLine(int line) {
            this.line = line;
        }
    }

    private enum CsvHeader {
        PROBLEM("\"Problem\""),
        PACKAGE("\"Package\""),
        FILE("\"File\""),
        PRIORITY("\"Priority\""),
        LINE("\"Line\""),
        DESCRIPTION("\"Description\""),
        RULE_SET("\"Rule set\""),
        RULE("\"Rule\"");

        private final String value;

        CsvHeader(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
