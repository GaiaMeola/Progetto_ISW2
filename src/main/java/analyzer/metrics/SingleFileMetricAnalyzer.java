package analyzer.metrics;

import analyzer.model.MethodInfo;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import org.slf4j.LoggerFactory;
import util.Configuration;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Paths;
import java.util.logging.Level;

// Classe per ricalcolare le metriche su un singolo metodo che è stato rifattorizzato
public class SingleFileMetricAnalyzer {

    private static final String FILE_NAME = Configuration.SELECTED_PROJECT == util.ProjectType.BOOKKEEPER
            ? "/home/denni/isw2/bookkeeper/bookkeeper-benchmark/src/main/java/org/apache/bookkeeper/benchmark/BenchReadThroughputLatency.java"
            : "/home/denni/isw2/openjpa/openjpa-persistence/src/main/java/org/apache/openjpa/persistence/HintHandler.java";

    private static final String OUTPUT_NAME = Configuration.SELECTED_PROJECT == util.ProjectType.BOOKKEEPER
            ? "ml_results/bookkeeper_AFMethod2_metrics.csv"
            : "ml_results/openjpa_AFMethod2_metrics.csv";

    public static void main(String[] args) {

        // Disabilita i log di PMD
        ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
        pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        // Disabilita log DEBUG di JGit
        ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
        jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        // Specifica il file Java da analizzare
        File javaFile = new File(FILE_NAME);
        if (!javaFile.exists()) {
            return;
        }

        try {
            // Il file Java viene parsato in una AST
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) return;

            //  Estrazione metodi dal file
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            if (methods.isEmpty()) return;

            // Serve per calcolo delle metriche statiche
            StaticMetricCalculator staticCalc = new StaticMetricCalculator();

            // Configura PMD
            LanguageVersion javaVersion = LanguageRegistry.PMD.getLanguageVersionById("java", "1.6");
            PMDConfiguration config = new PMDConfiguration();
            config.setDefaultLanguageVersion(javaVersion);
            config.addRuleSet("category/java/design.xml");
            config.addRuleSet("category/java/bestpractices.xml");
            config.addInputPath(Paths.get(javaFile.getAbsolutePath()));

            List<MethodInfo> results = new ArrayList<>();
            try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
                Report report = pmd.performAnalysisAndCollectReport();

                for (MethodDeclaration method : methods) {
                    // Costruzione di ogni MethodInfo
                    MethodInfo info = new MethodInfo();
                    info.setMethodName(javaFile.getAbsolutePath() + "/" + method.getNameAsString());
                    info.setProjectName(Configuration.getProjectColumn());
                    info.setReleaseId("AFMethod");
                    info.setReleaseDate(null);

                    int start = method.getBegin().map(p -> p.line).orElse(-1);
                    int end = method.getEnd().map(p -> p.line).orElse(-1);

                    info.setStartLine(start);
                    info.setEndLine(end);
                    info.setMethodCode(method.toString());

                    info.setLoc(staticCalc.calculateLoc(method));
                    info.setCyclomaticComplexity(staticCalc.calculateCyclomaticComplexity(method));
                    info.setCognitiveComplexity(staticCalc.calculateCognitiveComplexity(method));
                    info.setParameterCount(staticCalc.calculateParameterCount(method));
                    info.setNestingDepth(staticCalc.calculateNestingDepth(method));
                    info.setStatementCount(staticCalc.calculateStatementCount(method));
                    info.setReturnTypeComplexity(staticCalc.calculateReturnTypeComplexity(method));
                    info.setLocalVariableCount(staticCalc.calculateLocalVariableCount(method));

                    List<String> smellNames = report.getViolations().stream()
                            .filter(v -> v.getBeginLine() >= start && v.getBeginLine() <= end)
                            .map(v -> v.getRule().getName())
                            .distinct().toList();

                    info.setDetectedSmells(smellNames);
                    info.setNumberOfSmells(smellNames.size());
                    info.setBugginess(false);

                    results.add(info);
                }
            }

            // Scrivi output CSV
            try (FileWriter fw = new FileWriter(OUTPUT_NAME)) {
                fw.write("Method;LOC;Cyclomatic;Cognitive;ParameterCount;NestingDepth;StatementCount;ReturnTypeComplexity;LocalVarCount;Smells;SmellTypes\n");
                for (MethodInfo m : results) {
                    fw.write(String.format("%s;%d;%d;%d;%d;%d;%d;%d;%d;%d;\"%s\"%n",
                            m.getMethodName(), m.getLoc(), m.getCyclomaticComplexity(), m.getCognitiveComplexity(),
                            m.getParameterCount(), m.getNestingDepth(), m.getStatementCount(),
                            m.getReturnTypeComplexity(), m.getLocalVariableCount(), m.getNumberOfSmells(),
                            String.join(";", m.getDetectedSmells())));
                }
            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nel calcolo della correlazione Spearman", e);
        }
    }
}

