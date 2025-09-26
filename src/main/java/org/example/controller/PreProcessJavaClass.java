package org.example.controller;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.example.utilities.Sink;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PreProcessJavaClass {

    private static final String JAVA_EXTENTION = ".java";
    private static final String TEST = "/test/";

    private final Logger logger = SeLogger.getInstance().getLogger();

    private final Repository repository;
    private final List<Release> releases;
    private final List<Ticket> tickets;
    private final String project;

    private List<JavaClass> javaClasses = new ArrayList<>();
    private final List<Commit> commitsWithIssues = new ArrayList<>();

    public PreProcessJavaClass(@NotNull Repository repository,
                               @NotNull List<Release> releases,
                               @NotNull List<Ticket> tickets,
                               @NotNull String project) {
        this.repository = repository;
        this.releases = releases;
        this.tickets = tickets;
        this.project = project;
    }

    // --- preprocessing classi
    public void preprocessJavaClasses() throws IOException {
        this.javaClasses = Collections.synchronizedList(new ArrayList<>());
        logInfo(() -> "Total releases found = " + this.releases.size());

        Map<Integer, List<JavaClass>> classesByRelease = new HashMap<>();

        // parsing classi per commit
        for (Release release : this.releases) {
            for (Commit commit : Optional.ofNullable(release.getCommitList()).orElse(Collections.emptyList())) {
                try {
                    List<String> touchedClasses = getTouchedClassesNames(commit.getRevCommit());
                    touchedClasses.stream()
                            .filter(name -> !name.contains(TEST))
                            .forEach(className -> {
                                try {
                                    String content = getContent(commit.getRevCommit(), className, repository);
                                    JavaClass jc = new JavaClass(className, content, commit.getRelease(), true);
                                    if (jc.isHasMap()) {
                                        javaClasses.add(jc);
                                        classesByRelease
                                                .computeIfAbsent(commit.getRelease().getId(), ignored -> new ArrayList<>())
                                                .add(jc);
                                    }
                                } catch (IOException e) {
                                    logWarning(() -> "Skipping class " + className + " in commit " +
                                            commit.getRevCommit().getName() + " due to IO error: " + e.getMessage());
                                }
                            });

                } catch (IOException e) {
                    logWarning(() -> "Skipping commit " + commit.getRevCommit().getName() +
                            " due to IO error: " + e.getMessage());
                }
            }
        }

        // Analisi buggy tramite BuggyMarker
        this.commitsWithIssues.addAll(BuggyMarker.markClasses(this.javaClasses, this.tickets, this.repository));

        // Salvataggio JSON classi buggy
        JSONArray commitsArray = new JSONArray();

        for (Commit commit : commitsWithIssues) {
            JSONObject entry = new JSONObject();
            entry.put("hash", commit.getRevCommit().getName());
            entry.put("author", commit.getRevCommit().getAuthorIdent().getName());
            entry.put("email", commit.getRevCommit().getAuthorIdent().getEmailAddress());

            // Conversione della data usando Instant e ZonedDateTime
            ZonedDateTime commitDate = commit.getRevCommit()
                    .getAuthorIdent()
                    .getWhenAsInstant()
                    .atZone(ZoneId.systemDefault());
            entry.put("date", commitDate.toString());

            // Lista delle classi toccate
            List<String> touchedClasses = getTouchedClassesNames(commit.getRevCommit());
            entry.put("touchedClasses", touchedClasses);

            commitsArray.put(entry);
        }
        // Salvataggio su file JSON
        String outputPath = Sink.buildProjectPath("buggy_commits", project);
        Files.createDirectories(Paths.get(outputPath));
        String outputFile = outputPath + "buggy_commits_report.json";

        try (FileWriter file = new FileWriter(outputFile)) {
            file.write(commitsArray.toString(4));
            logInfo(() -> "Buggy commits report salvato in: " + outputFile);
        } catch (IOException e) {
            logWarning(() -> "Errore durante il salvataggio del report buggy commits: " + e.getMessage());
        }
    }

    // --- metodi di utilità esistenti (getContent, getTouchedClassesNames, normalizeCode, ecc.) ---
    private String getContent(RevCommit commit, String filePath, Repository repository) throws IOException {
        RevTree tree = commit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));
            if (!treeWalk.next()) throw new IOException("File " + filePath + " non trovato");
            ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
            return new String(loader.getBytes(), StandardCharsets.UTF_8);
        }
    }

    private @NotNull List<String> getTouchedClassesNames(@NotNull RevCommit commit) throws IOException {
        List<String> touchedClassesNames = new ArrayList<>();
        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            if (commit.getParentCount() > 0) oldTreeIter.reset(reader, commit.getParent(0).getTree());

            diffFormatter.setRepository(repository);
            List<DiffEntry> entries = commit.getParentCount() > 0 ?
                    diffFormatter.scan(oldTreeIter, newTreeIter) :
                    diffFormatter.scan(new CanonicalTreeParser(), newTreeIter);

            for (DiffEntry entry : entries) {
                String path = entry.getNewPath();
                if (path.endsWith(JAVA_EXTENTION) && !path.toLowerCase().contains(TEST)) {
                    touchedClassesNames.add(path);
                }
            }
        }
        return touchedClassesNames;
    }

    public List<Commit> getCommitsWithIssues() { return commitsWithIssues; }
    public List<JavaClass> getJavaClasses() { return javaClasses; }
    private void logInfo(Supplier<String> msgSupplier) { if (logger.isLoggable(Level.INFO)) logger.info(msgSupplier.get()); }
    private void logWarning(Supplier<String> msgSupplier) { if (logger.isLoggable(Level.WARNING)) logger.warning(msgSupplier.get()); }
}
