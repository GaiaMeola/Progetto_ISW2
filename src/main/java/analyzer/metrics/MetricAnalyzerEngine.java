package analyzer.metrics;

import analyzer.model.MethodInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.reporting.Report;
import org.slf4j.LoggerFactory;
import util.Configuration;
import util.ProjectType;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class MetricAnalyzerEngine {

    public static void main(String[] args) {
        setupLoggers();

        // Legge tutto dalla configurazione centralizzata
        String fileName = getFilePath();
        String outputName = getOutputPath();

        File javaFile = new File(fileName);
        if (!javaFile.exists()) {
            Configuration.logger.severe("ERRORE: Il file non esiste: " + fileName);
            return;
        }

        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) return;

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            if (methods.isEmpty()) return;

            // Esegue l'analisi PMD e salva
            List<MethodInfo> results = performAnalysis(javaFile, methods);
            saveToCsv(outputName, results);

            Configuration.logger.info("Analisi completata. File analizzato: " + fileName);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore durante l'analisi", e);
        }
    }

    private static String getFilePath() {
        boolean ref = Configuration.ANALYZE_REFACTORED;
        if (Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER) {
            return ref ? "refactored_methods/BenchReadThroughputLatencyRefactoredMain.java"
                    : "refactored_methods/BenchReadThroughputLatency.java";
        } else {
            return ref ? "refactored_methods/FieldMetaDataRefactoredCopy.java"
                    : "refactored_methods/FieldMetaData.java";
        }
    }

    private static String getOutputPath() {
        String suffix = Configuration.ANALYZE_REFACTORED ? "_refactored.csv" : ".csv";
        return "ml_results/" + Configuration.getProjectName().toLowerCase() + "_AFMethod2_metrics" + suffix;
    }

    private static List<MethodInfo> performAnalysis(File javaFile, List<MethodDeclaration> methods) {
        StaticMetricCalculator staticCalc = new StaticMetricCalculator();
        PMDConfiguration config = new PMDConfiguration();
        config.setDefaultLanguageVersion(LanguageRegistry.PMD.getLanguageVersionById("java", "1.6"));
        config.addRuleSet("category/java/design.xml");
        config.addRuleSet("category/java/bestpractices.xml");
        config.addInputPath(Paths.get(javaFile.getAbsolutePath()));

        List<MethodInfo> results = new ArrayList<>();
        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            Report report = pmd.performAnalysisAndCollectReport();

            for (MethodDeclaration method : methods) {
                MethodInfo info = new MethodInfo();
                info.setMethodName(method.getNameAsString());

                // Calcolo metriche
                info.setLoc(staticCalc.calculateLoc(method));
                info.setCyclomaticComplexity(staticCalc.calculateCyclomaticComplexity(method));
                info.setCognitiveComplexity(staticCalc.calculateCognitiveComplexity(method));
                info.setParameterCount(staticCalc.calculateParameterCount(method));
                info.setNestingDepth(staticCalc.calculateNestingDepth(method));
                info.setStatementCount(staticCalc.calculateStatementCount(method));
                info.setReturnTypeComplexity(staticCalc.calculateReturnTypeComplexity(method));
                info.setLocalVariableCount(staticCalc.calculateLocalVariableCount(method));

                int start = method.getBegin().map(p -> p.line).orElse(-1);
                int end = method.getEnd().map(p -> p.line).orElse(-1);

                List<String> smellNames = report.getViolations().stream()
                        .filter(v -> v.getBeginLine() >= start && v.getBeginLine() <= end)
                        .map(v -> v.getRule().getName())
                        .distinct().toList();

                info.setDetectedSmells(smellNames);
                info.setNumberOfSmells(smellNames.size());
                results.add(info);
            }
        }
        return results;
    }

    private static void saveToCsv(String outputName, List<MethodInfo> results) throws Exception {
        new File("ml_results").mkdirs();
        try (FileWriter fw = new FileWriter(outputName)) {
            fw.write("Method;LOC;Cyclomatic;Cognitive;ParameterCount;NestingDepth;StatementCount;ReturnTypeComplexity;LocalVarCount;Smells;SmellTypes\n");
            for (MethodInfo m : results) {
                fw.write(String.format("%s;%d;%d;%d;%d;%d;%d;%d;%d;%d;\"%s\"%n",
                        m.getMethodName(), m.getLoc(), m.getCyclomaticComplexity(), m.getCognitiveComplexity(),
                        m.getParameterCount(), m.getNestingDepth(), m.getStatementCount(),
                        m.getReturnTypeComplexity(), m.getLocalVariableCount(), m.getNumberOfSmells(),
                        String.join(";", m.getDetectedSmells())));
            }
        }
    }

    private static void setupLoggers() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("net.sourceforge.pmd")).setLevel(ch.qos.logback.classic.Level.ERROR);
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit")).setLevel(ch.qos.logback.classic.Level.ERROR);
    }
}