package analyzer.metrics;

import analyzer.git.GitRepository;
import analyzer.model.MethodInfo;
import analyzer.csv.CsvHandler;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import analyzer.model.Release;
import util.Configuration;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.*;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.reporting.Report;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.stream.Stream;

public class MethodMetricsExtractor {

    private final JavaParser parser = new JavaParser(); // parser per albero file java
    private final List<MethodInfo> methodInfos = new ArrayList<>(); // lista info metodi analizzati
    private String currentRelease;
    private LocalDate currentReleaseDate;
    private final HistoricalMetricExtractor historicalExtractor;

    // Inizializza calcolatore metriche statiche
    private final StaticMetricCalculator staticCalc = new StaticMetricCalculator();

    public MethodMetricsExtractor(GitRepository gitRepository) {
        // Inizializza calcolatore metriche storiche
        this.historicalExtractor = new HistoricalMetricExtractor(gitRepository);
    }

    public List<MethodInfo> getAnalyzedMethods() {
        return methodInfos;
    }

    public void setCurrentRelease(String releaseId) {
        this.currentRelease = releaseId;
    }

    public void setCurrentReleaseDate(LocalDate currentReleaseDate) {
        this.currentReleaseDate = currentReleaseDate;
    }

    /* Questo metodo:
     - Scorre tutti i file .java nel progetto (dopo il checkout nel main di dataset app)
     - Per farlo esclude alcune directory da non considerare
     - Per ogni file, chiama analyzeFile() per analizzare i metodi
     - Alla fine chiama l'analisi storica sui metodi trovati
     */
    public void analyzeProject(String projectPath, Release currentRelease) {

        int fileCount = 0;

        List<Path> javaFiles;
        try (Stream<Path> paths = Files.walk(Paths.get(projectPath))) {
            javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/test/"))
                    .filter(p -> !p.toString().contains("/generated/"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .toList(); // Risolve il suggerimento di SonarCloud dello Screenshot 8
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        for (Path path : javaFiles) {
            analyzeFile(path);
            fileCount++;
        }

        if(Configuration.BASIC_DEBUG && Configuration.logger.isLoggable(Level.INFO)){
            Configuration.logger.info(String.format("File .java analizzati: %d", fileCount));
            Configuration.logger.info(String.format("Chiamo analisi storica su %d metodi.", methodInfos.size()));
        }

        historicalExtractor.analyzeHistoryForMethods(methodInfos, currentRelease);


    }

    // Cerca metodi nel file
    private void analyzeFile(Path path) {

        try {

            // Parsing del file per ottenere struttura ad albero del source code (AST)
            CompilationUnit cu = parser.parse(path).getResult().orElse(null);
            if (cu == null) return;

            // Cerca dichiarazioni di metodi nel file
            List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
            if (methods.isEmpty()) return;

            // Configura PMD per analisi smells
            LanguageVersion javaVersion = LanguageRegistry.PMD.getLanguageVersionById("java", "1.6");
            Files.readString(path, StandardCharsets.UTF_8);
            PMDConfiguration config = new PMDConfiguration();
            config.setDefaultLanguageVersion(javaVersion);
            config.addRuleSet("category/java/design.xml"); // regole di design
            config.addRuleSet("category/java/bestpractices.xml"); // best practices
            config.addInputPath(path);

            // Avvia PMD
            try (PmdAnalysis pmd = PmdAnalysis.create(config)) {

                Report report = pmd.performAnalysisAndCollectReport();

                // Loop su ogni metodo
                for (MethodDeclaration method : methods) {

                    // Calcolo metriche statiche
                    MethodInfo info = analyzeMethod(method, path);
                    if (info == null) continue;

                    // Salva informazioni su dove inizia e finisce il metodo
                    int start = method.getBegin().map(p -> p.line).orElse(-1);
                    int end = method.getEnd().map(p -> p.line).orElse(-1);
                    info.setStartLine(start);
                    info.setEndLine(end);

                    // Salva codice del metodo (utile per refactoring)
                    info.setMethodCode(method.toString());

                    // Filtra tutti i code smells che cadono dentro il metodo e ne restituisce il nome
                    List<String> smellNames = report.getViolations().stream()
                            .filter(v -> v.getBeginLine() >= start && v.getBeginLine() <= end)
                            .map(v -> v.getRule().getName())
                            .distinct()
                            .toList();

                    info.setDetectedSmells(smellNames); // Imposta nome smell trovati nel metodo
                    info.setNumberOfSmells(smellNames.size()); // Imposta numero di code smell per il databset

                    methodInfos.add(info);

                    if (Configuration.BASIC_DEBUG && methodInfos.size() % 1000 == 0) {
                        String debugPath = Configuration.getDebugSampledMethodsPath();
                        MethodInfo sampled = methodInfos.get(methodInfos.size() - 1);
                        logDebugSample(methodInfos.size(), sampled, debugPath);
                    }

                }
            }

        } catch (Exception e) {
            Configuration.logger.info("Errore analisi file");
        }
    }


    // Analizza un singolo metodo e ne calcola tutte le metriche
    private MethodInfo analyzeMethod(MethodDeclaration method, Path path) {

        try {

            // Oggetto che contiene dati e valori delle metriche di un metodo
            MethodInfo info = new MethodInfo();

            info.setProjectName(Configuration.getProjectColumn()); // nome progetto
            info.setMethodName(path.toString() + "/" + method.getNameAsString()); // path completo + nome metodo
            info.setReleaseId(currentRelease); // release ID
            info.setReleaseDate(currentReleaseDate); // data della release


            // Metriche statiche:
            info.setLoc(staticCalc.calculateLoc(method)); // LOC
            info.setCyclomaticComplexity(staticCalc.calculateCyclomaticComplexity(method)); // Cyclomatic Complexity
            info.setCognitiveComplexity(staticCalc.calculateCognitiveComplexity(method)); // Cognitive Complexity
            info.setParameterCount(staticCalc.calculateParameterCount(method)); // Parameter Count
            info.setNestingDepth(staticCalc.calculateNestingDepth(method)); // Nesting Depth
            info.setStatementCount(staticCalc.calculateStatementCount(method)); // Statement Count
            info.setReturnTypeComplexity(staticCalc.calculateReturnTypeComplexity(method)); // Return Type Complexity
            info.setLocalVariableCount(staticCalc.calculateLocalVariableCount(method)); // Local Variable Count

            // Target, per ora impostiamo sempre false
            info.setBugginess(false);

            return info;

        } catch (Exception e) {
            Configuration.logger.info("Metodo non analizzato");

            return null;
        }
    }

    // Esporta il contenuto analizzato nel file CSV
    public void exportResults(String outputPath) {
        CsvHandler csvHandler = new CsvHandler();
        csvHandler.writeCsv(outputPath, methodInfos);
    }

    // Metodo per debug
    private void logDebugSample(int index, MethodInfo sampled, String debugPath) {
        try (FileWriter fw = new FileWriter(debugPath, true)) {
            fw.write("========== METHOD #" + index + " ==========\n");
            fw.write("Method: " + sampled.getMethodName() + "\n");
            fw.write("Release: " + sampled.getReleaseId() + "\n\n");
            fw.write("Code:\n" + sampled.getMethodCode() + "\n\n");

            fw.write("METRICS:\n");
            fw.write("LOC: " + sampled.getLoc() + "\n");
            fw.write("Cyclomatic Complexity: " + sampled.getCyclomaticComplexity() + "\n");
            fw.write("Cognitive Complexity: " + sampled.getCognitiveComplexity() + "\n");
            fw.write("Parameter Count: " + sampled.getParameterCount() + "\n");
            fw.write("Nesting Depth: " + sampled.getNestingDepth() + "\n");
            fw.write("Smells: " + sampled.getNumberOfSmells() + "\n");

            fw.write("Smell types:\n");
            for (String s : sampled.getDetectedSmells()) {
                fw.write("  - " + s + "\n");
            }

            fw.write("\n\n");

        } catch (IOException e) {
            Configuration.logger.info("No debug");

        }
    }

}
