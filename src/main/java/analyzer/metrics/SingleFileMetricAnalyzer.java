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
import util.ProjectType;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class SingleFileMetricAnalyzer {

    private static final String FILE_NAME = Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER
            ? "refactored_methods/BenchReadThroughputLatency.java"
            : "refactored_methods/FieldMetaData.java";

    // Output salvato nella cartella dei risultati definita nella config
    private static final String OUTPUT_NAME = Configuration.SELECTED_PROJECT == ProjectType.BOOKKEEPER
            ? "ml_results/bookkeeper_AFMethod2_metrics.csv"
            : "ml_results/openjpa_AFMethod2_metrics.csv";

    public static void main(String[] args) {

        // Setup Loggers
        ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
        pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
        jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        File javaFile = new File(FILE_NAME);
        if (!javaFile.exists()) {
            Configuration.logger.severe("ERRORE: Il file specificato non esiste: " + FILE_NAME);
            return;
        }

        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
            if (cu == null) return;

            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            if (methods.isEmpty()) return;

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
                    MethodInfo info = new MethodInfo();
                    info.setMethodName(method.getNameAsString());

                    int start = method.getBegin().map(p -> p.line).orElse(-1);
                    int end = method.getEnd().map(p -> p.line).orElse(-1);

                    // Calcolo metriche
                    info.setLoc(staticCalc.calculateLoc(method));
                    info.setCyclomaticComplexity(staticCalc.calculateCyclomaticComplexity(method));
                    info.setCognitiveComplexity(staticCalc.calculateCognitiveComplexity(method));
                    info.setParameterCount(staticCalc.calculateParameterCount(method));
                    info.setNestingDepth(staticCalc.calculateNestingDepth(method));
                    info.setStatementCount(staticCalc.calculateStatementCount(method));
                    info.setReturnTypeComplexity(staticCalc.calculateReturnTypeComplexity(method));
                    info.setLocalVariableCount(staticCalc.calculateLocalVariableCount(method));

                    // Conteggio Smell nel range del metodo
                    List<String> smellNames = report.getViolations().stream()
                            .filter(v -> v.getBeginLine() >= start && v.getBeginLine() <= end)
                            .map(v -> v.getRule().getName())
                            .distinct().toList();

                    info.setDetectedSmells(smellNames);
                    info.setNumberOfSmells(smellNames.size());

                    results.add(info);
                }
            }

            // Assicuriamoci che la cartella ml_results esista
            new File("ml_results").mkdirs();

            // Scrittura CSV
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
            Configuration.logger.info("Analisi singolo file completata. Output: " + OUTPUT_NAME);

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE, "Errore nel calcolo delle metriche del singolo file", e);
        }
    }
}