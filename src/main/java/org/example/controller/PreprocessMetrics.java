package org.example.controller;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.example.utilities.Sink;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class PreprocessMetrics {

    private final GitInjection gitCtrl;
    private final PreProcessJavaClass preProcClass;

    public PreprocessMetrics(GitInjection gitController) {
        this.gitCtrl = gitController;

        Repository repository;
        Git localGit;
        try {
            repository = gitCtrl.getRepository();
            localGit = new Git(repository);
        } catch (Exception e) {
            throw new RuntimeException("Impossibile inizializzare il repository o Git locale", e);
        }

        String repoPath = repository.getDirectory() != null ? repository.getDirectory().getParent() : "";

        try {
            this.preProcClass = new PreProcessJavaClass(
                    repository,
                    gitCtrl.getReleases(),
                    gitCtrl.getTickets(),
                    repoPath
            );
        } catch (Exception e) {
            throw new RuntimeException("Impossibile inizializzare PreProcessJavaClass", e);
        }
    }

    /**
     * Restituisce i nomi delle classi modificate in un commit.
     * Versione statica per BuggyMarker.
     */
    public static List<String> getTouchedClassesNamesStatic(RevCommit commit, Repository repository) {
        List<String> touchedClassesNames = new ArrayList<>();
        Logger logger = SeLogger.getInstance().getLogger();

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            newTreeIter.reset(reader, commit.getTree());

            RevCommit commitParent = commit.getParentCount() > 0 ? commit.getParent(0) : null;

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            if (commitParent != null) {
                oldTreeIter.reset(reader, commitParent.getTree());
            }

            diffFormatter.setRepository(repository);

            List<DiffEntry> entries = commitParent != null ?
                    diffFormatter.scan(oldTreeIter, newTreeIter) :
                    diffFormatter.scan(new CanonicalTreeParser(), newTreeIter);

            for (DiffEntry entry : entries) {
                String path = entry.getNewPath();
                if (path.endsWith(".java") && !path.toLowerCase().contains("/test/")) {
                    touchedClassesNames.add(path);
                }
            }

        } catch (IOException e) {
            logger.warning("Error processing touched classes for commit " + commit.getName() + ": " + e.getMessage());
        }

        return touchedClassesNames;
    }

    /**
     * Calcola tutte le metriche sulle classi già preprocessate.
     */
    public void start() {
        this.computeSize();
        this.computeRevisionsNumber();
        this.computeFixNumber();
        this.computeAuthorsNumber();
        this.computeLOCMetrics();
    }

    private List<JavaClass> classes() {
        return preProcClass.getJavaClasses() != null ? preProcClass.getJavaClasses() : new ArrayList<>();
    }

    private void computeSize() {
        this.classes().parallelStream().forEach(javaClass -> {
            String[] lines = javaClass.getClassBody().split("\r\n|\r|\n");
            javaClass.getMetrics().setSize(lines.length);
        });
    }

    private void computeRevisionsNumber() {
        for (JavaClass javaClass : this.classes()) {
            javaClass.getMetrics().setNumberOfRevisions(javaClass.getClassCommits().size());
        }
    }

    private void computeFixNumber() {
        List<String> commitsWithIssuesNames = gitCtrl.getCommitsWithIssues() == null ?
                List.of() :
                gitCtrl.getCommitsWithIssues().stream()
                        .map(c -> {
                            var rc = c.getRevCommit();
                            return rc == null ? null : rc.getName();
                        })
                        .filter(Objects::nonNull)
                        .toList();

        for (JavaClass javaClass : this.classes()) {
            int fixNumber = 0;
            for (Commit commitThatTouchesTheClass : javaClass.getClassCommits()) {
                RevCommit touched = commitThatTouchesTheClass.getRevCommit();
                if (touched == null) continue;
                String touchedName = touched.getName();

                if (commitsWithIssuesNames.contains(touchedName)) {
                    fixNumber++;
                }
            }
            javaClass.getMetrics().setNumberOfDefectFixes(fixNumber);
        }
    }

    private void computeAuthorsNumber() {
        for (JavaClass javaClass : this.classes()) {
            List<String> authorsOfClass = new ArrayList<>();
            for (Commit commit : javaClass.getClassCommits()) {
                RevCommit revCommit = commit.getRevCommit();
                if (revCommit != null && revCommit.getAuthorIdent() != null) {
                    String name = revCommit.getAuthorIdent().getName();
                    if (name != null && !authorsOfClass.contains(name)) {
                        authorsOfClass.add(name);
                    }
                }
            }
            javaClass.getMetrics().setNumberOfAuthors(authorsOfClass.size());
        }
    }

    private void computeLOCMetrics() {
        this.classes().parallelStream().forEach(javaClass -> {
            LOCMetrics addedLOC = new LOCMetrics();
            LOCMetrics removedLOC = new LOCMetrics();
            LOCMetrics churnLOC = new LOCMetrics();
            LOCMetrics touchedLOC = new LOCMetrics();

            preProcClass.checkLOCInfo(javaClass);

            List<Integer> locAddedByClass = javaClass.getLOCAddedByClass();
            List<Integer> locRemovedByClass = javaClass.getLOCRemovedByClass();

            for (int i = 0; i < Math.max(locAddedByClass.size(), locRemovedByClass.size()); i++) {
                if (i < locAddedByClass.size()) {
                    addedLOC.updateMetrics(locAddedByClass.get(i));
                }
                if (i < locRemovedByClass.size()) {
                    removedLOC.updateMetrics(locRemovedByClass.get(i));
                }
                if (i < locAddedByClass.size() && i < locRemovedByClass.size()) {
                    int added = locAddedByClass.get(i);
                    int removed = locRemovedByClass.get(i);
                    int churn = Math.abs(added - removed);

                    churnLOC.updateMetrics(churn);
                    touchedLOC.updateMetrics(added + removed);
                } else if (i < locAddedByClass.size()) {
                    touchedLOC.updateMetrics(locAddedByClass.get(i));
                } else if (i < locRemovedByClass.size()) {
                    touchedLOC.updateMetrics(locRemovedByClass.get(i));
                }
            }

            setMetrics(removedLOC, churnLOC, addedLOC, touchedLOC, javaClass, locAddedByClass, locRemovedByClass);
        });
    }

    private void setMetrics(LOCMetrics removedLOC, LOCMetrics churnLOC, LOCMetrics addedLOC,
                            LOCMetrics touchedLOC, JavaClass javaClass,
                            List<Integer> locAddedByClass, List<Integer> locRemovedByClass) {
        int nRevisions = Math.max(1, javaClass.getMetrics().getNumberOfRevisions());

        if (!locAddedByClass.isEmpty()) {
            addedLOC.setAvgVal((double) addedLOC.getVal() / nRevisions);
        }
        if (!locRemovedByClass.isEmpty()) {
            removedLOC.setAvgVal((double) removedLOC.getVal() / nRevisions);
        }
        if (!locAddedByClass.isEmpty() || !locRemovedByClass.isEmpty()) {
            churnLOC.setAvgVal((double) churnLOC.getVal() / nRevisions);
            touchedLOC.setAvgVal((double) touchedLOC.getVal() / nRevisions);
        }

        Metrics metrics = javaClass.getMetrics();
        metrics.setAddedLOCMetrics(addedLOC.getVal(), addedLOC.getMaxVal(), addedLOC.getAvgVal());
        metrics.setRemovedLOCMetrics(removedLOC.getVal(), removedLOC.getMaxVal(), removedLOC.getAvgVal());
        metrics.setChurnMetrics(churnLOC.getVal(), churnLOC.getMaxVal(), churnLOC.getAvgVal());
        metrics.setTouchedLOCMetrics(touchedLOC.getVal(), touchedLOC.getMaxVal(), touchedLOC.getAvgVal());
    }

    public void generateDataset(String projectName) throws IOException {
        List<Release> allReleases = this.gitCtrl.getReleases();
        List<Ticket> allTickets = this.gitCtrl.getTickets();
        List<JavaClass> allClasses = this.classes();

        int numberOfTrainingReleases = (int) Math.ceil(allReleases.size() * 0.33);

        if (numberOfTrainingReleases < 1) {
            throw new IllegalArgumentException("Project has too few releases to extract training data.");
        }

        List<Release> trainingReleases = allReleases.subList(0, numberOfTrainingReleases);

        List<JavaClass> trainingClasses = allClasses.stream()
                .filter(javaClass -> javaClass.getRelease().getId() <= trainingReleases.getLast().getId())
                .toList();

        preProcClass.fillClassesInfo(allTickets, trainingClasses);

        Sink.serializeInjectionToCsv(projectName, projectName,
                trainingReleases, trainingClasses, Sink.DataSetType.TRAINING);
        Sink.serializeInjectionToArff(projectName, projectName,
                trainingReleases, trainingClasses, Sink.DataSetType.TRAINING);

        // --- TEST SET ---
        if (numberOfTrainingReleases < allReleases.size()) {
            List<Release> testReleases = List.of(allReleases.get(numberOfTrainingReleases));

            List<JavaClass> testClasses = allClasses.stream()
                    .filter(javaClass -> javaClass.getRelease().getId() == testReleases.getLast().getId())
                    .toList();

            Sink.serializeInjectionToCsv(projectName, projectName,
                    testReleases, testClasses, Sink.DataSetType.TESTING);
            Sink.serializeInjectionToArff(projectName, projectName,
                    testReleases, testClasses, Sink.DataSetType.TESTING);
        }
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
}