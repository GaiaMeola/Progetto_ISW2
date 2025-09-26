package org.example.controller;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.example.utilities.JavaParserUtil;
import org.jetbrains.annotations.NotNull;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MethodMetricsProcessor {

    private static final String JAVA_EXTENSION = ".java";

    private static final String PMD_ANALYSIS = "pmd_analysis";
    private static final String SYS_PMD_HOME = "PMD_HOME";
    private static final String RELEASE = "Release";

    // Input data
    private final List<JavaClass> javaClasses;
    private final List<Commit> commits;

    // Git related (passati dal chiamante)
    private final Repository repository;
    private final Git localGit;
    private final String repoPath;
    private final String project;
    private String lastBranch = null;

    // Derived structures
    private Map<Release, List<JavaClass>> javaClassPerRelease = new LinkedHashMap<>();

    private final Logger logger = SeLogger.getInstance().getLogger();

    public MethodMetricsProcessor(List<JavaClass> javaClasses,
                                  List<Commit> commits,
                                  Repository repository,
                                  Git localGit,
                                  String repoPath,
                                  String project) {
        this.javaClasses = javaClasses != null ? javaClasses : new ArrayList<>();
        this.commits = commits != null ? commits : new ArrayList<>();
        this.repository = repository;
        this.localGit = localGit;
        this.repoPath = repoPath;
        this.project = project;
    }

    /**
     * Esegue tutto il flusso di calcolo delle metriche dei metodi.
     * - organizza le classi per release
     * - associa commit alle classi
     * - calcola churn/autori per metodo (con diff sui commit)
     * - calcola age, fan-in, fan-out
     */
    public void start() throws IOException {
        parseJavaClassPerRelease();
        checkUpdateInClassCommitted();    // associa alla classe i commit che la toccano
        updateMethodPerClassCommits();    // per ogni commit intermedio calcola diff/metodo
        computeMethodAge();
        computeFanInFanOut();
        // eventualmente: setupCodeSmellPMD() -> se vuoi lanciare PMD esternamente
    }

    // ------------------------
    // parsing / associazioni
    // ------------------------
    private void parseJavaClassPerRelease() {
        this.javaClassPerRelease = new LinkedHashMap<>();
        for (JavaClass javaClass : this.javaClasses) {
            this.javaClassPerRelease
                    .computeIfAbsent(javaClass.getRelease(), r -> new ArrayList<>())
                    .add(javaClass);
        }
    }

    /**
     * Associa commit alle classi in base alle release e ai file toccati dal commit.
     */
    private void checkUpdateInClassCommitted() throws IOException {
        for (Commit commit : this.commits) {
            Release release = commit.getRelease();
            // copia temporanea delle classi relative a questa release
            List<JavaClass> tempProjClasses = new ArrayList<>(this.javaClasses);
            tempProjClasses.removeIf(c -> !Objects.equals(c.getRelease(), release));

            List<String> modifiedClassesNames = getTouchedClassesNames(commit.getRevCommit());
            for (String modifiedClassPath : modifiedClassesNames) {
                // match su path o nome a seconda del tuo JavaClass.getName() (qui assumiamo path-like)
                for (JavaClass javaClass : tempProjClasses) {
                    if (javaClass.getName().equals(modifiedClassPath) && !javaClass.getClassCommits().contains(commit)) {
                        javaClass.addCommitToClass(commit);
                    }
                }
            }
        }
    }

    /**
     * Per ogni classe e per ogni commit (tranne l'ultimo della classe) esegue l'analisi del metodo e aggiorna metrics.
     */
    private void updateMethodPerClassCommits() {
        AtomicLong counter = new AtomicLong(0);
        try {
            for (Map.Entry<Release, List<JavaClass>> e : this.javaClassPerRelease.entrySet()) {
                List<JavaClass> jcList = e.getValue();
                for (JavaClass javaClass : jcList) {
                    List<Commit> classCommits = javaClass.getClassCommits();
                    if (classCommits == null || classCommits.size() <= 1) continue;

                    for (int i = 0; i < classCommits.size() - 1; i++) {
                        Commit commit = classCommits.get(i);
                        RevCommit rev = commit.getRevCommit();
                        if (rev == null) continue;
                        moreClassInfo(javaClass, rev);
                        counter.incrementAndGet();
                    }
                }
            }
        } finally {
            restoreRepositoryState();
        }
        logInfo(() -> "updateMethodPerClassCommits commits processed: " + counter.get());
    }

    /**
     * Scorre l'albero del commit, trova il file java corrispondente alla classe e per ogni metodo calcola il diff
     * rispetto al body memorizzato in JavaClass (se presente).
     */
    private void moreClassInfo(@NotNull JavaClass javaClass, @NotNull RevCommit revCommit) {
        RevTree tree = revCommit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(JAVA_EXTENSION));
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (!path.contains(javaClass.getName())) continue;

                byte[] raw = repository.open(treeWalk.getObjectId(0)).getBytes();
                String content = new String(raw, StandardCharsets.UTF_8);
                try {
                    CompilationUnit cu = StaticJavaParser.parse(content);
                    cu.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
                        String methodKey = methodDeclaration.getDeclarationAsString();
                        if (javaClass.getMethods().containsKey(methodKey)) {
                            methodDeclaration.getBody().ifPresent(body -> calcDiff(javaClass, methodDeclaration, methodKey, revCommit));
                        }
                    });
                } catch (ParseProblemException ppe) {
                    logWarning(() -> "Parse problem for class " + javaClass.getName() + " in commit " + revCommit.getName() + ": " + ppe.getMessage());
                }
            }
        } catch (IOException ioe) {
            logWarning(() -> "IO problem reading tree for commit " + revCommit.getName() + ": " + ioe.getMessage());
        }
    }

    /**
     * Confronta il body del metodo corrente con quello memorizzato in JavaClass e aggiorna MethodMetrics
     */
    private static void calcDiff(@NotNull JavaClass javaClass,
                                 @NotNull MethodDeclaration methodDeclaration,
                                 @NotNull String methodName,
                                 @NotNull RevCommit revCommit) {
        String newBody = JavaParserUtil.getStringBody(methodDeclaration);
        String oldBody = javaClass.getMethods().get(methodName); // quale snapshot era stato preso in preprocess
        if (oldBody == null) oldBody = "";
        if (!newBody.equals(oldBody)) {
            int added = 0;
            int removed = 0;
            List<String> oldLines = oldBody.isEmpty() ? Collections.emptyList() : Arrays.asList(oldBody.split("\n"));
            List<String> newLines = Arrays.asList(newBody.split("\n"));
            for (String line : newLines) if (!oldLines.contains(line)) added++;
            for (String line : oldLines) if (!newLines.contains(line)) removed++;

            MethodMetrics mm = javaClass.getMethodsMetrics().computeIfAbsent(methodName, k -> new MethodMetrics());
            mm.incChanges();
            mm.addChurn(added, removed);
            mm.addAuthor(revCommit.getAuthorIdent() != null ? revCommit.getAuthorIdent().getName() : "unknown");
        }
    }

    /**
     * Ottiene la lista dei file .java toccati da un commit (per confronto tree-parent -> tree)
     */
    private @NotNull List<String> getTouchedClassesNames(@NotNull RevCommit commit) throws IOException {
        List<String> touched = new ArrayList<>();
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            RevCommit parent;
            try {
                parent = commit.getParent(0);
            } catch (ArrayIndexOutOfBoundsException ex) {
                // no parent (initial commit)
                return touched;
            }

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, parent.getTree());

            List<DiffEntry> entries = diffFormatter.scan(oldTreeIter, newTreeIter);
            for (DiffEntry entry : entries) {
                String newPath = entry.getNewPath();
                if (newPath != null && newPath.endsWith(JAVA_EXTENSION) && !newPath.contains("/test/")) {
                    touched.add(newPath);
                }
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // no parent, skip
        }
        return touched;
    }

    // ------------------------
    // metriche sui metodi
    // ------------------------

    /**
     * Calcola l'età dei metodi rispetto alla prima release in cui appaiono.
     */
    private void computeMethodAge() {
        Map<String, Integer> firstAppearance = new HashMap<>();
        // ordina le release in ordine crescente
        List<Release> releases = new ArrayList<>(this.javaClassPerRelease.keySet());
        releases.sort(Comparator.comparingInt(Release::getId));

        for (Release r : releases) {
            List<JavaClass> list = this.javaClassPerRelease.get(r);
            if (list == null) continue;
            for (JavaClass jc : list) {
                for (String methodKey : jc.getMethodsMetrics().keySet()) {
                    firstAppearance.putIfAbsent(methodKey, r.getId());
                }
            }
        }

        // imposta age per ogni metodo nelle classi
        for (Release r : releases) {
            List<JavaClass> list = this.javaClassPerRelease.get(r);
            if (list == null) continue;
            for (JavaClass jc : list) {
                for (Map.Entry<String, MethodMetrics> e : jc.getMethodsMetrics().entrySet()) {
                    Integer first = firstAppearance.get(e.getKey());
                    if (first != null) {
                        e.getValue().setAge(r.getId() - first);
                    }
                }
            }
        }
    }

    /**
     * Calcola Fan-in e Fan-out per i metodi, esaminando i bodies delle classi
     */
    private void computeFanInFanOut() {
        // callerSig -> set(callees)
        Map<String, Set<String>> methodCalls = new HashMap<>();

        // STEP 1: costruisci mapping caller -> callees
        for (JavaClass jc : this.javaClasses) {
            String className = jc.getClassName();
            String body = jc.getClassBody();
            if (body == null || body.isBlank()) continue;
            try {
                CompilationUnit cu = StaticJavaParser.parse(body);
                cu.findAll(MethodDeclaration.class).forEach(md -> {
                    String callerSig = className + "." + JavaParserUtil.getSignature(md);
                    Set<String> callees = methodCalls.computeIfAbsent(callerSig, k -> new HashSet<>());
                    md.findAll(MethodCallExpr.class).forEach(mce -> {
                        // mce.getNameAsString() è solo il nome, potremmo usare arg count per matching
                        String calleeName = mce.getNameAsString();
                        int args = mce.getArguments().size();
                        // salviamo in forma "methodName#args" per matching più semplice
                        callees.add(calleeName + "#" + args);
                    });
                });
            } catch (ParseProblemException ppe) {
                logWarning(() -> "Parse problem computing fan-in/out in class " + jc.getName() + ": " + ppe.getMessage());
            }
        }

        // STEP 2: aggiorna fanOut
        for (JavaClass jc : this.javaClasses) {
            String className = jc.getClassName();
            for (Map.Entry<String, MethodMetrics> entry : jc.getMethodsMetrics().entrySet()) {
                String methodKey = entry.getKey();
                String fullSig = className + "." + methodKey; // methodKey already includes signature
                Set<String> callees = methodCalls.getOrDefault(fullSig, Collections.emptySet());
                entry.getValue().setFanOut(callees.size());
            }
        }

        // STEP 3: calcola fanIn sommando i callers che invocano callee
        Map<String, Integer> fanInCount = new HashMap<>();
        for (Set<String> callees : methodCalls.values()) {
            for (String callee : callees) {
                fanInCount.merge(callee, 1, Integer::sum);
            }
        }
        // ora, per ogni methodMetrics proviamo a ricavare la chiave che abbiamo usato (name#args)
        for (JavaClass jc : this.javaClasses) {
            for (Map.Entry<String, MethodMetrics> entry : jc.getMethodsMetrics().entrySet()) {
                MethodMetrics mm = entry.getValue();
                // produce key in formato "simpleName#paramCount"
                String key = mm.getSimpleName() + "#" + mm.getParameterCount();
                mm.setFanIn(fanInCount.getOrDefault(key, 0));
            }
        }
    }

    // ------------------------
    // helpers (logging / restore)
    // ------------------------
    private void logInfo(Supplier<String> msg) { if (logger.isLoggable(Level.INFO)) logger.info(msg.get()); }
    private void logWarning(Supplier<String> msg) { if (logger.isLoggable(Level.WARNING)) logger.warning(msg.get()); }

    private void restoreRepositoryState() {
        try {
            if (this.localGit != null) {
                this.localGit.reset().setMode(ResetCommand.ResetType.HARD).call();
                this.localGit.clean().setCleanDirectories(true).call();
                // non sempre abbiamo branch salvato qui; lascia come fallback
            }
        } catch (Exception e) {
            logWarning(() -> "Could not fully restore repo state: " + e.getMessage());
        }
    }

    /** Espone le classi aggiornate con metriche sui metodi */
    public List<JavaClass> getProcessedClasses() {
        return javaClasses;
    }
}
