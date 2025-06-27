package utilities;

import model.JavaClass;
import model.Release;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

public class Sink {

    public static final String DELIVERY_OUTPUT = "deliveryOutput";
    private static final String INJECTION_PATH = DELIVERY_OUTPUT + File.separator
            + "injection" + File.separator;
    private static final String DATASET_PATH = DELIVERY_OUTPUT + File.separator
            + "datasets" + File.separator;
    private static final String RESULT_PATH = DELIVERY_OUTPUT + File.separator
            + "results" + File.separator;
    private static final String METHOD = "method";

    private static final String CSV_HEADERS_INPUTS = "RELEASE_ID," +
            "FILE_NAME," +
            "SIZE," +
            "LOC_ADDED,LOC_ADDED_AVG,LOC_ADDED_MAX," +
            "LOC_REMOVED,LOC_REMOVED_AVG,LOC_REMOVED_MAX," +
            "LOC_TOUCHED,LOC_TOUCHED_AVG,LOC_TOUCHED_MAX," +
            "CHURN,CHURN_AVG,CHURN_MAX," +
            "NUMBER_OF_REVISIONS," +
            "NUMBER_OF_DEFECT_FIXES," +
            "NUMBER_OF_AUTHORS," +
            "IS_BUGGY\n";

    private static final String CSV_HEADER_RESULTS = "DATASET," +
            "#TRAINING_RELEASES," +
            "%TRAINING_INSTANCES," +
            "CLASSIFIER," +
            "FEATURE_SELECTION," +
            "BALANCING," +
            "COST_SENSITIVE," +
            "PRECISION," +
            "RECALL," +
            "AREA_UNDER_ROC," +
            "KAPPA," +
            "TRUE_POSITIVES," +
            "FALSE_POSITIVES," +
            "TRUE_NEGATIVES," +
            "FALSE_NEGATIVES\n";

    private static final String ARFF_RELATION = "@relation ";
    private static final String ARFF_ATTRIBUTE_AND_DATA = """
            @attribute SIZE numeric
            @attribute LOC_ADDED numeric
            @attribute LOC_ADDED_AVG numeric
            @attribute LOC_ADDED_MAX numeric
            @attribute LOC_REMOVED numeric
            @attribute LOC_REMOVED_AVG numeric
            @attribute LOC_REMOVED_MAX numeric
            @attribute CHURN numeric
            @attribute CHURN_AVG numeric
            @attribute CHURN_MAX numeric
            @attribute LOC_TOUCHED numeric
            @attribute LOC_TOUCHED_AVG numeric
            @attribute LOC_TOUCHED_MAX numeric
            @attribute NUMBER_OF_REVISIONS numeric
            @attribute NUMBER_OF_DEFECT_FIXES numeric
            @attribute NUMBER_OF_AUTHORS numeric
            @attribute IS_BUGGY {'YES', 'NO'}
            
            @data
            """;

    private Sink() {
    }

    public enum FileExtension {
        JSON,
        ARFF, CSV
    }

    public enum DataSetType {
        TRAINING,
        TESTING
    }

    public static void serializeToJson(String projectName, String fileName, Object data,
                                       FileExtension fileExtension) {
        if (data instanceof JSONObject jsonObject) {
            sinkJson(projectName, fileName, jsonObject, fileExtension);
        }
    }

    private static void sinkJson(String projectName, String filename, JSONObject jsonObject, FileExtension fileExtension) {
        String dirPath = DELIVERY_OUTPUT + File.separator + projectName + File.separator + fileExtension.name().toLowerCase();
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, filename + "." + fileExtension.name().toLowerCase());
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(jsonObject.toString(4)); // pretty print with indentation
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeInjectionToCsv(String projectName, String filename,
                                               List<Release> releases, List<JavaClass> javaClasses,
                                               DataSetType dataSetType) {
        String datasetPath = DATASET_PATH + projectName + File.separator + FileExtension.CSV.name().toLowerCase() + File.separator
                + dataSetType.name().toLowerCase();
        File dir = new File(datasetPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, filename + ".csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(CSV_HEADERS_INPUTS);
            appendInjectionData(writer, releases, javaClasses, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeInjectionToArff(String projectName, String filename,
                                                List<Release> releases, List<JavaClass> javaClasses,
                                                @NotNull DataSetType dataSetType) {
        String datasetPath = DATASET_PATH + projectName + File.separator + FileExtension.ARFF.name().toLowerCase() + File.separator
                + dataSetType.name().toLowerCase();
        File dir = new File(datasetPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, filename + ".arff");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(ARFF_RELATION + filename + "\n\n");
            writer.write(ARFF_ATTRIBUTE_AND_DATA);
            appendInjectionData(writer, releases, javaClasses, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void serializeResultsToCsv(@NotNull String projectName, List<ClassifierResult> results) {
        String resultsPath = RESULT_PATH + projectName + File.separator;
        File dir = new File(resultsPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, projectName.toLowerCase() + "_report.csv");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(CSV_HEADER_RESULTS);
            appendResultsData(writer, results);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void appendInjectionData(Writer writer, List<Release> releases,
                                            List<JavaClass> javaClasses, boolean arffFile) throws IOException {
        for (Release release : releases) {
            for (JavaClass javaClass : javaClasses) {
                if (javaClass.getRelease().getId() == release.getId()) {
                    doAppendData(writer, release, javaClass, arffFile);
                }
            }
        }
    }

    private static void doAppendData(Writer writer, Release release, JavaClass javaClass,
                                     boolean isArff) throws IOException {
        String releaseID = Integer.toString(release.getId());
        String isClassBugged = javaClass.isBug() ? "YES" : "NO";
        String sizeOfClass = String.valueOf(javaClass.getSize());
        String addedLOC = String.valueOf(javaClass.getAddedLoc());
        String avgAddedLOC = String.valueOf(javaClass.getAvgAddedLoc());
        String maxAddedLOC = String.valueOf(javaClass.getMaxAddedLoc());
        String removedLOC = String.valueOf(javaClass.getRemovedLoc());
        String avgRemovedLOC = String.valueOf(javaClass.getAvgRemovedLoc());
        String maxRemovedLOC = String.valueOf(javaClass.getMaxRemovedLoc());
        String touchedLOC = String.valueOf(javaClass.getTouchedLoc());
        String avgTouchedLOC = String.valueOf(javaClass.getAvgTouchedLoc());
        String maxTouchedLOC = String.valueOf(javaClass.getMaxTouchedLoc());
        String churn = String.valueOf(javaClass.getChurn());
        String avgChurn = String.valueOf(javaClass.getAvgChurn());
        String maxChurn = String.valueOf(javaClass.getMaxChurn());
        String nRevisions = String.valueOf(javaClass.getNumberOfRevisions());
        String nDefectFixes = String.valueOf(javaClass.getNumberOfDefectFixes());
        String nAuthors = String.valueOf(javaClass.getNumberOfAuthors());

        if (isArff) {
            // ARFF format: values separated by commas
            writer.write(String.join(",",
                    sizeOfClass,
                    addedLOC,
                    avgAddedLOC,
                    maxAddedLOC,
                    removedLOC,
                    avgRemovedLOC,
                    maxRemovedLOC,
                    churn,
                    avgChurn,
                    maxChurn,
                    touchedLOC,
                    avgTouchedLOC,
                    maxTouchedLOC,
                    nRevisions,
                    nDefectFixes,
                    nAuthors,
                    isClassBugged) + "\n");
        } else {
            // CSV format: include release id and class name as well
            writer.write(String.join(",",
                    releaseID,
                    javaClass.getName(),
                    sizeOfClass,
                    addedLOC,
                    avgAddedLOC,
                    maxAddedLOC,
                    removedLOC,
                    avgRemovedLOC,
                    maxRemovedLOC,
                    touchedLOC,
                    avgTouchedLOC,
                    maxTouchedLOC,
                    churn,
                    avgChurn,
                    maxChurn,
                    nRevisions,
                    nDefectFixes,
                    nAuthors,
                    isClassBugged) + "\n");
        }
    }

    private static void appendResultsData(Writer writer, List<ClassifierResult> results) throws IOException {
        for (ClassifierResult result : results) {
            writer.write(String.join(",",
                    result.getDataset(),
                    String.valueOf(result.getTrainingReleases()),
                    String.valueOf(result.getTrainingInstancesPercent()),
                    result.getClassifierName(),
                    result.getFeatureSelection(),
                    result.getBalancing(),
                    result.getCostSensitive(),
                    String.valueOf(result.getPrecision()),
                    String.valueOf(result.getRecall()),
                    String.valueOf(result.getAreaUnderROC()),
                    String.valueOf(result.getKappa()),
                    String.valueOf(result.getTruePositives()),
                    String.valueOf(result.getFalsePositives()),
                    String.valueOf(result.getTrueNegatives()),
                    String.valueOf(result.getFalseNegatives())
            ) + "\n");
        }
    }
}
