package org.example.controller;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.PathSuffixFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.example.utilities.JavaParserUtil;
import org.example.utilities.Sink;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.example.utilities.Sink.storeMaxCodeSmells;

public class PreProcessJavaClass {

    private static final String JAVA_EXTENTION = ".java";
    private static final String TEST = "/test/";
    private static final String PMD_ANALYSIS = "pmd_analysis";
    private static final String SYS_PMD_HOME = "PMD_HOME";
    private static final String RELEASE = "Release";

    private final Logger logger = SeLogger.getInstance().getLogger();

    /* dipendenze / stato (tipicamente passate da GitInjection) */
    private final Repository repository;
    private final Git localGithub;
    private final List<Release> releases;
    private final List<Commit> commits;
    private final List<Ticket> tickets;
    private final String project;
    private final String repoPath;
    private String lastBranch = null;

    /* risultati / strutture interne */
    private List<JavaClass> javaClasses = new ArrayList<>();
    private LinkedHashMap<Release, List<JavaClass>> javaClassPerRelease = new LinkedHashMap<>();
    private final List<Commit> commitsWithIssues = new ArrayList<>();
    public final Map<RevCommit, List<String>> modifiedClassesForCommit = new HashMap<>();

    public PreProcessJavaClass(@NotNull Repository repository,
                               @NotNull Git localGithub,
                               @NotNull List<Release> releases,
                               @NotNull List<Commit> commits,
                               @NotNull List<Ticket> tickets,
                               @NotNull String project,
                               @NotNull String repoPath) {
        this.repository = repository;
        this.localGithub = localGithub;
        this.releases = releases;
        this.commits = commits;
        this.tickets = tickets;
        this.project = project;
        this.repoPath = repoPath;
    }

    /**
     * Returning the ClassName and Body
     *
     * @param revCommit the commit in Git repo
     * @return The class name touched in that commit and class Body
     */
    public static @NotNull Map<String, String> getAllClassesNameAndContent(@NotNull RevCommit revCommit,
                                                                           Repository repository) throws IOException {
        Map<String, String> allClasses = new HashMap<>();
        RevTree tree = revCommit.getTree();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (path.contains(JAVA_EXTENTION) && !path.contains(TEST)) {
                    ObjectId objId = treeWalk.getObjectId(0);
                    byte[] bytes = repository.open(objId).getBytes();
                    allClasses.put(path, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }
        return allClasses;
    }

    public void preprocessJavaClasses() throws IOException {
        // Lista thread-safe, non serve synchronized manuale
        this.javaClasses = Collections.synchronizedList(new ArrayList<>());

        logInfo(() -> "Total releases found = " + this.releases.size());

        // Mappa releaseId -> lista classi (per ottimizzare marking buggy)
        Map<Integer, List<JavaClass>> classesByRelease = new HashMap<>();

        // --- PARSING DELLE CLASSI PER OGNI COMMIT ---
        for (Release release : this.releases) {
            List<Commit> commitsInRelease = release.getCommitList();
            if (commitsInRelease == null || commitsInRelease.isEmpty()) continue;

            for (Commit commit : commitsInRelease) {
                try {
                    // 1️⃣ Ottieni solo i nomi delle classi modificate in questo commit
                    List<String> touchedClasses = getTouchedClassesNames(commit.getRevCommit());

                    // 2️⃣ Per ogni classe modificata, recupera il contenuto e crea la JavaClass
                    touchedClasses.stream()
                            .filter(name -> !name.contains(TEST))
                            .forEach(className -> {
                                try {
                                    String content = getContent(commit.getRevCommit(), className, this.repository);
                                    JavaClass jc = new JavaClass(className, content, commit.getRelease(), true);
                                    if (jc.isHasMap()) {
                                        this.javaClasses.add(jc);
                                        classesByRelease
                                                .computeIfAbsent(commit.getRelease().getId(), k -> new ArrayList<>())
                                                .add(jc);
                                    }
                                } catch (IOException e) {
                                    logWarning(() -> "Skipping class " + className + " in commit " +
                                            commit.getRevCommit().getName() + " due to IO error: " + e.getMessage());
                                }
                            });

                    logInfo(() -> "Processed " + touchedClasses.size() +
                            " touched classes for commit " + commit.getRevCommit().getName());

                } catch (IOException e) {
                    logWarning(() -> "Skipping commit " + commit.getRevCommit().getName() +
                            " due to IO error: " + e.getMessage());
                }
            }
        }

        // --- ANALISI BUGGY/NON BUGGY ---
        this.fillClassesInfo();
        logInfo(() -> "fillClassInfo");

        this.javaClasses.forEach(jc -> jc.getMetrics().setBug(false));

        for (Ticket ticket : this.tickets) {
            List<Commit> ticketCommits = ticket.getCommitList();
            Release injectedVersion = ticket.getInjectedVersion();

            for (Commit commit : ticketCommits) {
                RevCommit revCommit = commit.getRevCommit();
                LocalDate commitDate = LocalDate.ofInstant(revCommit.getCommitterIdent().getWhenAsInstant(), ZoneId.systemDefault());

                if (!commitDate.isAfter(ticket.getResolutionDate()) && !commitDate.isBefore(ticket.getCreationDate())) {
                    List<String> touchedClasses = getTouchedClassesNames(revCommit);

                    for (String modifiedClass : touchedClasses) {
                        classesByRelease.entrySet().stream()
                                .filter(e -> e.getKey() >= injectedVersion.getId() && e.getKey() < commit.getRelease().getId())
                                .flatMap(e -> e.getValue().stream())
                                .filter(jc -> jc.getName().equals(modifiedClass))
                                .forEach(jc -> jc.getMetrics().setBug(true));
                    }
                }
            }
        }

        // --- SALVATAGGIO JSON BUGGY/NON BUGGY ---
        JSONArray buggyClassesArray = new JSONArray();
        for (JavaClass jc : this.javaClasses) {
            JSONObject entry = new JSONObject();
            entry.put("release", jc.getRelease().getReleaseName());
            entry.put("class", jc.getClassName());
            entry.put("buggy", jc.getMetrics().isBug());
            buggyClassesArray.put(entry);
        }

        // Usa la struttura centralizzata di Sink
        String buggyClassesPath = Sink.buildProjectPath("buggy_classes", this.project);
        logInfo(() -> "Tentativo di creare directory: " + buggyClassesPath);
        Files.createDirectories(Paths.get(buggyClassesPath));
        logInfo(() -> "Directory creata, javaClasses.size()=" + javaClasses.size());

        String buggyClassesFile = buggyClassesPath + "buggy_classes_report.json";
        try (FileWriter file = new FileWriter(buggyClassesFile)) {
            file.write(buggyClassesArray.toString(4));
            logInfo(() -> "Buggy classes report salvato in: " + buggyClassesFile);
        } catch (IOException e) {
            logWarning(() -> "Errore durante il salvataggio del report buggy classes: " + e.getMessage());
        }
    }

    /**
     * Restituisce il contenuto di un file in un commit specifico.
     */
    private String getContent(RevCommit commit, String filePath, Repository repository) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevTree tree = commit.getTree();

            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(tree);
                treeWalk.setRecursive(true);
                treeWalk.setFilter(PathFilter.create(filePath));

                if (!treeWalk.next()) {
                    throw new FileNotFoundException("File " + filePath + " non trovato nel commit " + commit.getName());
                }

                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);
                return new String(loader.getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    // ---- PARTE COMMENTATA ORIGINALE ----

//    this.checkUpdateInClassCommitted();
//    logInfo(() -> "checkUpdateCommitted");
//
//    this.javaClasses.sort(Comparator.comparing(JavaClass::getName));
//    this.parseJavaClassPerRelease();
//    logInfo(() -> "parsingClassPerRelease: release-size=" + this.javaClassPerRelease.size());
//
//    this.logBugPercentage();
//
//    // ---- Analisi metodi ----
//
//    logTime("check-Age", this::checkMethodAge);
//    logTime("check usage", this::checkMethodUsage);
//    logTime("updateMethodPerClassCommits", this::updateMethodPerClassCommits);
//    logTime("check code smells", () -> {
//        this.setupCodeSmellPMD();
//        CodeSmellParser.extractCodeSmell(this.javaClassPerRelease, this.project);
//    });
//
//    this.foundMostCodeSmells();

    private void fillClassesInfo() throws IOException {
        this.fillClassesInfo(this.tickets, this.javaClasses);
    }

    public void fillClassesInfo(List<Ticket> theTickets, @NotNull List<JavaClass> theClasses) throws IOException {

        //setup iniziale; ogni classe innanzitutto parte come NON BUGGY
        for (JavaClass javaClass : theClasses) javaClass.getMetrics().setBug(false);

        for (Ticket ticket : theTickets) {
            List<Commit> commitsContainingTicket = ticket.getCommitList();
            //per ogni ticket, si guardano tutti i commit collegati a quel ticket
            Release injectedVersion = ticket.getInjectedVersion();
            for (Commit commit : commitsContainingTicket) {
                SimpleDateFormat formatter = new SimpleDateFormat(GitInjection.LOCAL_DATE_FORMAT);
                RevCommit revCommit = commit.getRevCommit();
                LocalDate commitDate = LocalDate.parse(formatter.format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant())));
                if (!commitDate.isAfter(ticket.getResolutionDate()) && !commitDate.isBefore(ticket.getCreationDate())) {
                    //si prendono solo i commit la cui data è compresa tra la creazione e la risoluzione del ticket
                    List<String> modifiedClassesNames = getTouchedClassesNames(revCommit);
                    //verifico tutte le classi che sono state toccate in quel periodo temporale
                    Release releaseOfCommit = commit.getRelease();
                    modifiedClassesForCommit.putIfAbsent(revCommit, modifiedClassesNames);
                    //lista che tiene traccia per ogni commit delle classi modificate
                    for (String modifiedClass : modifiedClassesNames) {
                        checkForAnyBug(modifiedClass, injectedVersion, releaseOfCommit);
                        //eventuale marcatura come classe BUGGY
                    }
                }
            }
        }
    }

    private void checkForAnyBug(String modifiedClass, Release injectedVersion, Release fixedVersion) {

        List<JavaClass> fixedClasses = this.javaClasses.stream().filter(javaClass -> javaClass.getRelease().getId() == fixedVersion.getId()).toList();

        for (JavaClass javaClass : this.javaClasses) {
            //si guardano tutte le classi di tutte le release
            if (javaClass.getName().equals(modifiedClass)
                    //la classe deve avere lo stesso nome della classe modificata nel commit
                    && javaClass.getRelease().getId() < fixedVersion.getId()
                    //release della classe deve essere prima del fix, ma dopo injected
                    && javaClass.getRelease().getId() >= injectedVersion.getId()) {
                //se tutte e tre le condizioni sono vere la marco come buggy
                javaClass.getMetrics().setBug(true);

                javaClass.getMethods().entrySet().forEach(entry ->
                        fixedClasses.stream().filter(jc -> jc.getName().equals(modifiedClass)).findAny().ifPresent(
                                //qui prendiamo solamente le versioni corrette delle classi
                                fixedClass -> {
                                    Map<String, String> methodMap = fixedClass.getMethods();
                                    checkMethodDiff(javaClass, entry, methodMap);
                                    //qui invece: marcatura a livello di metodo
                                }
                        ));
            }
        }
    }

    private static void checkMethodDiff(JavaClass javaClass, Map.Entry<String, String> entry, Map<String, String> methodMap) {
        //riceve la classe Java marcata come BUGGY di cui si vogliono marcare i metodi
        //poi la copia (nomeMetodo, corpoMetodo)
        //lista di tutti i metodi nella versione fixed
        MethodMetrics metrics = javaClass.getMethodsMetrics().get(entry.getKey());
        if (metrics == null) {
            metrics = new MethodMetrics();
            javaClass.getMethodsMetrics().put(entry.getKey(), metrics);
        } //viene creato l'oggetto MethodMetrics
        if (!methodMap.containsKey(entry.getKey())) {
            metrics.setBug(true); //se sparisce era buggy
        } else { //se esiste ancora
            String fixedBody = methodMap.get(entry.getKey());
            String oldBody = entry.getValue();
            if (!fixedBody.equals(oldBody)) { //confronta i corpi; se sono cambiati allora era buggy
                metrics.setBug(true);
            }
        }
    }

    private @NotNull List<String> getTouchedClassesNames(@NotNull RevCommit commit) throws IOException {
        List<String> touchedClassesNames = new ArrayList<>();
        //lista inizialmente vuota in cui ci andranno le classi coinvolte per quel commit

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            ObjectId newTree = commit.getTree();
            newTreeIter.reset(reader, newTree);
            RevCommit commitParent = commit.getParent(0);
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            ObjectId oldTree = commitParent.getTree();
            oldTreeIter.reset(reader, oldTree);
            diffFormatter.setRepository(repository);
            //ogni DiffEntry rappresenta un file che è stato modificato
            List<DiffEntry> entries = diffFormatter.scan(oldTreeIter, newTreeIter);
            for (DiffEntry entry : entries) {
                //controllo che sia un file.java; non un file di test
                if (entry.getNewPath().contains(JAVA_EXTENTION) && !entry.getNewPath().contains("/test/")) {
                    touchedClassesNames.add(entry.getNewPath());
                }
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // ignoring when no parent is found
        }
        return touchedClassesNames;
    }

    private void foundMostCodeSmells() {
        AtomicInteger num = new AtomicInteger(0);
        AtomicInteger globalMax = new AtomicInteger(-1);

        List<Release> sortedReleases = new ArrayList<>(this.javaClassPerRelease.keySet());
        sortedReleases.sort(Comparator.comparing(Release::getId));
        if (sortedReleases.isEmpty()) return;
        Release last = sortedReleases.get(sortedReleases.size() - 1);

        List<JavaClass> lastList = this.javaClassPerRelease.get(last);
        if (lastList == null) return;

        lastList.forEach(jc -> {
            int max = jc.getMethodsMetrics().values().stream()
                    .filter(value -> (value.getStatementCount() != 1 && value.isBug()))
                    .mapToInt(MethodMetrics::getNumberOfCodeSmells)
                    .max().orElse(0);
            globalMax.updateAndGet(g -> Math.max(g, max));
        });

        lastList.forEach(jc -> jc.getMethodsMetrics().entrySet().stream()
                .filter(entry -> (entry.getValue().getStatementCount() != 1) && entry.getValue().isBug())
                .filter(entry -> entry.getValue().getNumberOfCodeSmells() == globalMax.get())
                .forEach(entry -> {
                    storeMaxCodeSmells(last.getId() + "smell-" + globalMax.get() + "-" + this.project + (num.incrementAndGet()), jc, entry);
                    extractPreviousMethod(jc, entry, last, globalMax, num);
                }));
    }

    private void storeMaxCodeSmells(String s, JavaClass jc, Map.Entry<String, MethodMetrics> entry) {
    }

    private void extractPreviousMethod(JavaClass jc, Map.Entry<String, MethodMetrics> entry, Release last, AtomicInteger globalMax, AtomicInteger num) {
        Optional<Release> previousReleaseOpt = this.javaClassPerRelease.keySet().stream()
                .filter(r -> r.getId() == last.getId() - 1)
                .findFirst();

        previousReleaseOpt.ifPresent(previousRelease -> {
            Optional<JavaClass> matchingPrevClassOpt = this.javaClassPerRelease.get(previousRelease).stream()
                    .filter(prevClass -> prevClass.getName().equals(jc.getName()))
                    .findFirst();

            matchingPrevClassOpt.ifPresent(prevClass -> {
                if (prevClass.getMethodsMetrics().containsKey(entry.getKey())) {
                    Map.Entry<String, MethodMetrics> prevEntry = Map.entry(entry.getKey(), prevClass.getMethodsMetrics().get(entry.getKey()));
                    storeMaxCodeSmells(previousRelease.getId() + "-prev-smell-" + globalMax.get() + "-" + this.project + num.get(), prevClass, prevEntry);
                }
            });
        });
    }

    private void logBugPercentage() {
        AtomicLong bugCounter = new AtomicLong(0);
        AtomicLong totalCounter = new AtomicLong(0);

        this.javaClassPerRelease.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().getId()))
                .forEach(entry -> {
                    Release theRelease = entry.getKey();
                    List<JavaClass> javaClassesForRelease = entry.getValue();
                    if (javaClassesForRelease == null || javaClassesForRelease.isEmpty()) return;

                    String commitName = "";
                    try {
                        List<Commit> commitList = theRelease.getCommitList();
                        if (commitList != null && !commitList.isEmpty()) {
                            commitList.sort(Comparator.comparing(c -> Date.from(c.getRevCommit().getCommitterIdent().getWhenAsInstant())));
                            commitName = commitList.getLast().getRevCommit().getName();
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }

                    javaClassesForRelease.forEach(javaClass -> {
                        bugCounter.addAndGet(javaClass.getMethodsMetrics().entrySet().stream().filter(e -> e.getValue().isBug()).count());
                        totalCounter.addAndGet(javaClass.getMethods().size());
                    });

                    StringBuilder msg = new StringBuilder("{\"Release\": ")
                            .append(theRelease.getId())
                            .append("\"Commit\": ")
                            .append(commitName)
                            .append(", \"Bugs\": ")
                            .append(bugCounter.get())
                            .append(", \"Total\": ").append(totalCounter.get())
                            .append(", \"Percentage\": \"")
                            .append(String.format("%.2f%%", 100.0 * (bugCounter.get() / Math.max(1.0, totalCounter.get()))))
                            .append("\"}");
                    bugCounter.set(0);
                    totalCounter.set(0);
                    logInfo(msg::toString);
                });
    }

    /**
     * Check method age relative current release
     */
    private void checkMethodAge() {
        Map<String, Integer> methodFirstAppearance = new HashMap<>();
        this.javaClassPerRelease.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getId()))
                .forEach(entry -> {
                    Release release = entry.getKey();
                    for (JavaClass javaClass : entry.getValue()) {
                        for (String methodSig : javaClass.getMethodsMetrics().keySet()) {
                            methodFirstAppearance.putIfAbsent(methodSig, release.getId());
                        }
                    }
                });

        this.javaClassPerRelease.forEach((release, classList) -> {
            for (JavaClass jc : classList) {
                for (Map.Entry<String, MethodMetrics> entry : jc.getMethodsMetrics().entrySet()) {
                    String methodSig = entry.getKey();
                    MethodMetrics metrics = entry.getValue();
                    Integer firstAppearance = methodFirstAppearance.get(methodSig);
                    if (firstAppearance != null) {
                        int age = release.getId() - firstAppearance;
                        metrics.setAge(age);
                    }
                }
            }
        });
    }

    /**
     * Fan-in / Fan-out calculation method
     */
    private void checkMethodUsage() {
        this.javaClassPerRelease.forEach((release, classes) -> {
            // STEP 1: build caller -> callee map
            Map<String, Set<String>> methodCalls = new HashMap<>();

            classes.forEach(jc -> {
                final String className = jc.getClassName();
                try {
                    final CompilationUnit cu = StaticJavaParser.parse(jc.getClassBody());
                    cu.findAll(MethodDeclaration.class).forEach(methodDecl -> {
                        final String callerSig = className + "." + JavaParserUtil.getSignature(methodDecl);
                        final Set<String> callees = new HashSet<>();
                        methodDecl.findAll(MethodCallExpr.class)
                                .stream()
                                .map(MethodCallExpr::getNameAsString)
                                .forEach(callees::add);
                        methodCalls.put(callerSig, callees);
                    });
                } catch (ParseProblemException ppe) {
                    logWarning(() -> "Parse problem for class " + jc.getName() + ": " + ppe.getMessage());
                }
            });

            // STEP 2: compute fan-out
            classes.forEach(jc -> {
                final String className = jc.getClassName();
                jc.getMethodsMetrics().forEach((methodSig, mm) -> {
                    final String fullMethodSig = className + "." + methodSig;
                    final int fanOut = methodCalls.getOrDefault(fullMethodSig, Collections.emptySet()).size();
                    mm.setFanOut(fanOut);
                });
            });

            // STEP 3: compute fan-in
            classes.forEach(callerJc -> {
                try {
                    final CompilationUnit cu = StaticJavaParser.parse(callerJc.getClassBody());
                    cu.findAll(MethodDeclaration.class).forEach(methodDecl ->
                            methodDecl.getBody().ifPresent(block ->
                                    block.findAll(MethodCallExpr.class).forEach(callStmt -> {
                                        final String calleeName = callStmt.getNameAsString();
                                        final int argCount = callStmt.getArguments().size();

                                        classes.forEach(possibleCallee ->
                                                possibleCallee.getMethodsMetrics().values().stream()
                                                        .filter(mm -> Objects.equals(mm.getSimpleName(), calleeName)
                                                                && mm.getParameterCount() == argCount)
                                                        .forEach(mm -> mm.setFanIn(mm.getFanIn() + 1))
                                        );
                                    })
                            )
                    );
                } catch (ParseProblemException ppe) {
                    logWarning(() -> "Parse problem while calculating usage for class " + callerJc.getName() + ": " + ppe.getMessage());
                }
            });
        });
    }

    /**
     * Use PMD to calculate codeSmells
     */
    private void setupCodeSmellPMD() {
        String outputDirPath = PMD_ANALYSIS + File.separator + this.project;
        File outputDir = new File(outputDirPath);

        if (outputDir.exists() && outputDir.isDirectory()) {
            File[] existingFiles = outputDir.listFiles((dir, name) -> name.endsWith(".csv"));
            if (existingFiles != null && existingFiles.length >= this.javaClassPerRelease.size()) {
                return;
            }
        }

        // iterate releases in order
        this.javaClassPerRelease.keySet().stream()
                .sorted(Comparator.comparing(Release::getId))
                .forEach(release -> {
                    if (release.getCommitList() == null || release.getCommitList().isEmpty()) return;

                    if (release.getId() <= 1) {
                        logInfo(() -> RELEASE + " " + 0 + " starting");
                        doPmd(0, release.getCommitList().get(0).getRevCommit());
                    }

                    logInfo(() -> RELEASE + " " + release.getId() + " starting");
                    doPmd(release.getId(), release.getCommitList().get(release.getCommitList().size() - 1).getRevCommit());
                });
    }

    private void doPmd(int releaseId, @NotNull RevCommit commit) {
        String commitId = commit.getName();
        final String reportPath = PMD_ANALYSIS + File.separator + this.project + File.separator + releaseId + ".csv";
        if (new File(reportPath).exists()) return;

        try {
            logInfo(() -> "do pmd for " + this.project + " previous " + RELEASE + " " + releaseId +
                    " with commit " + commitId + " for next " + RELEASE + " " + (releaseId + 1));
            this.localGithub.checkout().setForced(true).setName(commitId).call();
        } catch (GitAPIException e) {
            logWarning(() -> "Initial checkout failed for " + commitId + ": " + e.getMessage());
            try {
                cleanGitState();
                this.localGithub.reset().setMode(ResetCommand.ResetType.HARD).call();
                this.localGithub.clean().setCleanDirectories(true).call();
                this.localGithub.checkout().setForced(true).setName(commitId).call();
            } catch (GitAPIException | IOException ex) {
                logError(() -> commitId + " still has problems after reset: " + ex.getMessage());
            }
        } finally {
            restoreRepositoryState();
        }

        try {
            Process process = getProcess(releaseId);
            process.waitFor(5L, TimeUnit.MINUTES);
            logInfo(() -> "done pmd " + this.project + " " + RELEASE + " " + releaseId);
            process.destroy();
        } catch (IOException ignored) {
            // continue
        } catch (InterruptedException e) {
            logInfo(() -> "Some problem occurred here unexpected interruption " + this.project + " " + RELEASE + " " + releaseId);
            Thread.currentThread().interrupt();
        }
    }

    private void cleanGitState() throws IOException {
        String gitDir = repoPath + File.separator + ".git" + File.separator;
        String[] mergeFiles = {"MERGE_HEAD", "MERGE_MSG", "MERGE_MODE", "index"};
        for (String fName : mergeFiles) {
            File f = new File(gitDir + fName);
            if (f.exists()) Files.delete(f.toPath());
        }
    }

    @Contract("_ -> new")
    private @NotNull Process getProcess(int id) throws IOException {
        final String pmd = System.getenv(SYS_PMD_HOME) + File.separator + "bin" + File.separator + "pmd";
        final String reportPath = PMD_ANALYSIS + File.separator + this.project + File.separator + id + ".csv";
        return new ProcessBuilder(
                pmd,
                "check",
                "-d", this.repoPath,
                "-R", Objects.requireNonNull(PreProcessJavaClass.class.getClassLoader().getResource("pmd/custom.xml")).getPath(),
                "-f", "csv",
                "--no-cache",
                "-r", reportPath
        ).redirectErrorStream(true).start();
    }

    private void updateMethodPerClassCommits() {
        AtomicLong counter = new AtomicLong(0);
        try {
            this.javaClassPerRelease.forEach((release, jcList) -> jcList.stream()
                    .filter(javaClass -> javaClass.getClassCommits() != null && javaClass.getClassCommits().size() > 1)
                    .forEach(javaClass -> javaClass.getClassCommits().forEach(commit -> {
                        if (!commit.equals(javaClass.getClassCommits().get(javaClass.getClassCommits().size() - 1))) {
                            moreClassInfo(javaClass, commit.getRevCommit());
                            counter.incrementAndGet();
                        }
                    })));
        } catch (NullPointerException ignored) {
            // skip
        } finally {
            restoreRepositoryState();
        }
        logInfo(() -> "updateMethodPerClassCommits commits: " + counter.get());
    }

    private void moreClassInfo(@NotNull JavaClass javaClass, @NotNull RevCommit revCommit) {
        RevTree tree = revCommit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathSuffixFilter.create(JAVA_EXTENTION));
            while (treeWalk.next()) {
                if (treeWalk.getPathString().contains(javaClass.getName())) {
                    CompilationUnit cu = StaticJavaParser.parse(new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
                    cu.findAll(MethodDeclaration.class).forEach(methodDeclaration -> {
                        String methodName = methodDeclaration.getDeclarationAsString();
                        if (javaClass.getMethods().get(methodName) != null) {
                            methodDeclaration.getBody().ifPresent(body -> calcDiff(javaClass, methodDeclaration, methodName, revCommit));
                        }
                    });
                }
            }
        } catch (IOException | ParseProblemException ipe) {
            logError(() -> ipe.getClass().getSimpleName() + " problem with this javaClass in release: " + javaClass.getRelease().getId());
        } catch (NullPointerException e) {
            logError(() -> "java class: " + javaClass.getName() + " nullptr: " + e.getMessage());
        }
    }

    private static void calcDiff(@NotNull JavaClass javaClass, MethodDeclaration methodDeclaration,
                                 String methodName, @NotNull RevCommit revCommit) {
        String newBody = JavaParserUtil.getStringBody(methodDeclaration);
        String oldBody = javaClass.getMethods().get(methodName);
        if (!newBody.equals(oldBody)) {
            int added = 0;
            int removed = 0;
            List<String> oldLines = oldBody == null ? Collections.emptyList() : Arrays.asList(oldBody.split("\n"));
            List<String> newLines = Arrays.asList(newBody.split("\n"));
            for (String line : newLines) if (!oldLines.contains(line)) added++;
            for (String line : oldLines) if (!newLines.contains(line)) removed++;
            javaClass.getMethodsMetrics().computeIfAbsent(methodName, k -> new MethodMetrics()).incChanges();
            javaClass.getMethodsMetrics().get(methodName).addChurn(added, removed);
            javaClass.getMethodsMetrics().get(methodName).addAuthor(revCommit.getAuthorIdent().getName());
        }
    }

    private void parseJavaClassPerRelease() {
        this.javaClassPerRelease = new LinkedHashMap<>();
        for (JavaClass javaClass : this.javaClasses) {
            this.javaClassPerRelease.putIfAbsent(javaClass.getRelease(), new ArrayList<>());
            this.javaClassPerRelease.get(javaClass.getRelease()).add(javaClass);
        }
    }

    private void checkUpdateInClassCommitted() throws IOException {
        List<JavaClass> tempProjClasses;
        for (Commit commit : this.commits) {
            Release release = commit.getRelease();
            tempProjClasses = new ArrayList<>(this.javaClasses);
            tempProjClasses.removeIf(tempProjClass -> !Objects.equals(tempProjClass.getRelease(), release));
            List<String> modifiedClassesNames = this.getTouchedClassesNames(commit.getRevCommit());
            for (String modifiedClass : modifiedClassesNames) {
                for (JavaClass javaClass : tempProjClasses) {
                    if (javaClass.getName().equals(modifiedClass) && !javaClass.getClassCommits().contains(commit)) {
                        javaClass.addCommitToClass(commit);
                    }
                }
            }
        }
    }

    public List<Commit> getCommitsWithIssues() {
        return commitsWithIssues;
    }

    public List<JavaClass> getJavaClasses() {
        return this.javaClasses;
    }

    public void checkLOCInfo(@NotNull JavaClass javaClass) {
        for (Commit commit : javaClass.getClassCommits()) {
            RevCommit revCommit = commit.getRevCommit();
            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                RevCommit parentComm = revCommit.getParent(0);
                diffFormatter.setRepository(repository);
                diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                List<DiffEntry> diffEntries = diffFormatter.scan(parentComm.getTree(), revCommit.getTree());
                for (DiffEntry diffEntry : diffEntries) {
                    if (diffEntry.getNewPath().equals(javaClass.getName())) {
                        javaClass.addLOCAddedByClass(getAddedLines(diffFormatter, diffEntry));
                        javaClass.addLOCRemovedByClass(getDeletedLines(diffFormatter, diffEntry));
                    }
                }
            } catch (ArrayIndexOutOfBoundsException | IOException ignored) {
                // ignoring when no parent is found
            }
        }
    }

    private int getAddedLines(@NotNull DiffFormatter diffFormatter, DiffEntry entry) throws IOException {
        int addedLines = 0;
        for (Edit edit : diffFormatter.toFileHeader(entry).toEditList()) {
            addedLines += edit.getEndB() - edit.getBeginB();
        }
        return addedLines;
    }

    private int getDeletedLines(@NotNull DiffFormatter diffFormatter, DiffEntry entry) throws IOException {
        int deletedLines = 0;
        for (Edit edit : diffFormatter.toFileHeader(entry).toEditList()) {
            deletedLines += edit.getEndA() - edit.getBeginA();
        }
        return deletedLines;
    }

    public Map<String, String> getMapTickets() {
        Map<String, String> mapTickets = new HashMap<>();
        this.tickets.sort(Comparator.comparing(Ticket::getCreationDate));
        for (Ticket ticket : this.tickets) {
            List<String> ids = new ArrayList<>();
            for (Release release : ticket.getAffectedVersions()) ids.add(release.getReleaseName());
            Map<String, String> inner = new LinkedHashMap<>();
            inner.put("injectedVersion", ticket.getInjectedVersion().toString());
            inner.put("openingVersion", ticket.getOpeningVersion().toString());
            inner.put("fixedVersion", ticket.getFixedVersion().toString());
            inner.put("affectedVersions", ids.toString());
            inner.put("commits", String.valueOf(ticket.getCommitList().size()));
            inner.put("creationDate", ticket.getCreationDate().toString());
            inner.put("resolutionDate", ticket.getResolutionDate().toString());
            mapTickets.put(ticket.getTicketKey(), inner.toString());
        }
        return mapTickets;
    }

    public Map<String, String> getMapCommits() {
        Map<String, String> mapCommits = new HashMap<>();
        for (Commit commit : this.commits) {
            Map<String, String> inner = new LinkedHashMap<>();
            RevCommit revCommit = commit.getRevCommit();
            Ticket ticket = commit.getTicket();
            Release release = commit.getRelease();
            if (ticket != null) inner.put("ticketKey", commit.getTicket().getTicketKey());
            inner.put(RELEASE, release.getReleaseName());
            inner.put("creationDate", String.valueOf(LocalDate.parse((new SimpleDateFormat(GitInjection.LOCAL_DATE_FORMAT)
                    .format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant()))))));
            mapCommits.put(revCommit.getName(), inner.toString());
        }
        return mapCommits;
    }

    public Map<String, String> getMapSummary() {
        Map<String, String> summaryMap = new HashMap<>();
        summaryMap.put("Releases", String.valueOf(this.releases.size()));
        summaryMap.put("Tickets", String.valueOf(this.tickets.size()));
        summaryMap.put("Commits", String.valueOf(this.commits.size()));
        summaryMap.put("Commits with bugs", String.valueOf(this.commitsWithIssues.size()));
        return summaryMap;
    }

    private void restoreRepositoryState() {
        if (lastBranch == null) {
            logWarning(() -> "No branch to restore to");
            return;
        }
        try {
            this.localGithub.reset().setMode(ResetCommand.ResetType.HARD).call();
            this.localGithub.clean().setCleanDirectories(true).call();
            this.localGithub.checkout().setName(lastBranch).call();
            logInfo(() -> "Repository restored to branch " + lastBranch + " at HEAD");
        } catch (GitAPIException e) {
            logError(() -> "reset the repo: " + e.getMessage());
        }
    }

    public void setLastBranch(String lastBranch) {
        this.lastBranch = lastBranch;
    }

    // --- logging lazy helpers (Sonar-friendly) ---
    private void logInfo(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.INFO)) logger.info(msgSupplier.get());
    }
    private void logWarning(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.WARNING)) logger.warning(msgSupplier.get());
    }
    private void logError(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.SEVERE)) logger.severe(msgSupplier.get());
    }
    private void logTime(String name, Runnable action) {
        logInfo(() -> "start " + name);
        long start = System.currentTimeMillis();
        action.run();
        long end = System.currentTimeMillis();
        logInfo(() -> "done " + name + " took=" + ((end - start) / 1e3) + "s");
    }
}