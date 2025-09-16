//package org.example.controller;
//
//import org.example.model.*;
//import org.example.utilities.Sink;
//import org.eclipse.jgit.revwalk.RevCommit;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Objects;
//
//public class PreprocessMetrics {
//
//    private final GitInjection gitCtrl;
//    private final PreProcessJavaClass preProcClass;
//
//    public PreprocessMetrics(GitInjection gitController) {
//        this.gitCtrl = gitController;
//
//        // PreProcessJavaClass -> usa repository + Git + releases + commits + tickets + project + repoPath
//        try {
//
//            var repository = gitCtrl.getRepository();
//            var localGit = new org.eclipse.jgit.api.Git(repository);
//
//            String repoPath = repository.getDirectory() != null ?
//                    repository.getDirectory().getParent() : "";
//
//            this.preProcClass = new PreProcessJavaClass(
//                    repository,
//                    localGit,
//                    gitCtrl.getReleases(),
//                    gitCtrl.getCommits(),
//                    gitCtrl.getTickets(),
//                    gitCtrl.getProject(),
//                    repoPath
//            );
//
//            // se GitInjection espone lastBranch o branch corrente, puoi settarlo:
//            try {
//                String branch = repository.getBranch();
//                this.preProcClass.setLastBranch(branch);
//            } catch (Exception ignored) {
//            }
//
//        } catch (Exception e) {
//            throw new RuntimeException("Impossibile inizializzare PreProcessJavaClass", e);
//        }
//    }
//
//    /**
//     * Esegue preprocess (popola / etichetta le classi) e poi calcola le metriche.
//     */
//    public void start() {
//        try {
//            // esegue tutta la pipeline di analisi
//            preProcClass.preprocessJavaClasses();
//        } catch (IOException e) {
//            throw new RuntimeException("Errore durante preprocessJavaClasses", e);
//        }
//
//        /* principali metodi di calcolo */
//        this.computeSize();
//        this.computeRevisionsNumber();
//        this.computeFixNumber();
//        this.computeAuthorsNumber();
//        this.computeLOCMetrics();
//    }
//
//    private List<JavaClass> classes() {
//        // prendiamo le classi generate dall'istanza PreProcessJavaClass
//        return preProcClass.getJavaClasses() != null ? preProcClass.getJavaClasses() : new ArrayList<>();
//    }
//
//    private void computeSize() {
//        this.classes().parallelStream().forEach(javaClass -> {
//            String[] lines = javaClass.getClassBody().split("\r\n|\r|\n");
//            javaClass.getMetrics().setSize(lines.length);
//        });
//    }
//
//    private void computeRevisionsNumber() {
//        for (JavaClass javaClass : this.classes()) {
//            javaClass.getMetrics().setNumberOfRevisions(javaClass.getClassCommits().size());
//        }
//    }
//
//    private void computeFixNumber() {
//        List<String> commitsWithIssuesNames = gitCtrl.getCommitsWithIssues() == null ?
//                List.of() :
//                gitCtrl.getCommitsWithIssues().stream()
//                        .map(c -> {
//                            var rc = c.getRevCommit();
//                            return rc == null ? null : rc.getName();
//                        })
//                        .filter(Objects::nonNull)
//                        .toList();
//
//        for (JavaClass javaClass : this.classes()) {
//            int fixNumber = 0;
//            for (Commit commitThatTouchesTheClass : javaClass.getClassCommits()) {
//                RevCommit touched = commitThatTouchesTheClass.getRevCommit();
//                if (touched == null) continue;
//                String touchedName = touched.getName();
//
//                if (commitsWithIssuesNames.contains(touchedName)) {
//                    fixNumber++;
//                }
//            }
//            javaClass.getMetrics().setNumberOfDefectFixes(fixNumber);
//        }
//    }
//
//    private void computeAuthorsNumber() {
//        for (JavaClass javaClass : this.classes()) {
//            List<String> authorsOfClass = new ArrayList<>();
//            for (Commit commit : javaClass.getClassCommits()) {
//                RevCommit revCommit = commit.getRevCommit();
//                if (revCommit != null && revCommit.getAuthorIdent() != null) {
//                    String name = revCommit.getAuthorIdent().getName();
//                    if (name != null && !authorsOfClass.contains(name)) {
//                        authorsOfClass.add(name);
//                    }
//                }
//            }
//            javaClass.getMetrics().setNumberOfAuthors(authorsOfClass.size());
//        }
//    }
//
//    private void computeLOCMetrics() {
//        this.classes().parallelStream().forEach(javaClass -> {
//            LOCMetrics addedLOC = new LOCMetrics();
//            LOCMetrics removedLOC = new LOCMetrics();
//            LOCMetrics churnLOC = new LOCMetrics();
//            LOCMetrics touchedLOC = new LOCMetrics();
//
//            // Check for LOC information for the current Java class (PreProcessJavaClass espone il metodo)
//            preProcClass.checkLOCInfo(javaClass);
//
//            List<Integer> locAddedByClass = javaClass.getLOCAddedByClass();
//            List<Integer> locRemovedByClass = javaClass.getLOCRemovedByClass();
//
//            // Compute metrics in a single pass through the lists
//            for (int i = 0; i < Math.max(locAddedByClass.size(), locRemovedByClass.size()); i++) {
//                if (i < locAddedByClass.size()) {
//                    addedLOC.updateMetrics(locAddedByClass.get(i));
//                }
//                if (i < locRemovedByClass.size()) {
//                    removedLOC.updateMetrics(locRemovedByClass.get(i));
//                }
//                if (i < locAddedByClass.size() && i < locRemovedByClass.size()) {
//                    int added = locAddedByClass.get(i);
//                    int removed = locRemovedByClass.get(i);
//                    int churn = Math.abs(added - removed);
//
//                    churnLOC.updateMetrics(churn);
//                    touchedLOC.updateMetrics(added + removed);
//                } else if (i < locAddedByClass.size()) {
//                    // If only additions exist, update touched metrics
//                    touchedLOC.updateMetrics(locAddedByClass.get(i));
//                } else if (i < locRemovedByClass.size()) {
//                    // If only removals exist, update touched metrics
//                    touchedLOC.updateMetrics(locRemovedByClass.get(i));
//                }
//            }
//
//            // Set the computed metrics in the JavaClass
//            setMetrics(removedLOC, churnLOC, addedLOC, touchedLOC, javaClass, locAddedByClass, locRemovedByClass);
//        });
//    }
//
//    private void setMetrics(LOCMetrics removedLOC, LOCMetrics churnLOC, LOCMetrics addedLOC,
//                            LOCMetrics touchedLOC, JavaClass javaClass,
//                            List<Integer> locAddedByClass, List<Integer> locRemovedByClass) {
//        int nRevisions = Math.max(1, javaClass.getMetrics().getNumberOfRevisions()); // evita divisione per zero
//
//        if (!locAddedByClass.isEmpty()) {
//            addedLOC.setAvgVal((double) addedLOC.getVal() / nRevisions);
//        }
//        if (!locRemovedByClass.isEmpty()) {
//            removedLOC.setAvgVal((double) removedLOC.getVal() / nRevisions);
//        }
//        if (!locAddedByClass.isEmpty() || !locRemovedByClass.isEmpty()) {
//            churnLOC.setAvgVal((double) churnLOC.getVal() / nRevisions);
//            touchedLOC.setAvgVal((double) touchedLOC.getVal() / nRevisions);
//        }
//
//        Metrics metrics = javaClass.getMetrics();
//        metrics.setAddedLOCMetrics(addedLOC.getVal(), addedLOC.getMaxVal(), addedLOC.getAvgVal());
//        metrics.setRemovedLOCMetrics(removedLOC.getVal(), removedLOC.getMaxVal(), removedLOC.getAvgVal());
//        metrics.setChurnMetrics(churnLOC.getVal(), churnLOC.getMaxVal(), churnLOC.getAvgVal());
//        metrics.setTouchedLOCMetrics(touchedLOC.getVal(), touchedLOC.getMaxVal(), touchedLOC.getAvgVal());
//    }
//
//    public void generateDataset(String projectName) throws IOException {
//        // Recupera tutte le release, ticket e classi del progetto
//        List<Release> allReleases = this.gitCtrl.getReleases();
//        List<Ticket> allTickets = this.gitCtrl.getTickets();
//        List<JavaClass> allClasses = this.classes(); // ora prendo dalle classi preprocessate
//
//        // Calcola quante release rientrano nel primo 33%
//        int numberOfTrainingReleases = (int) Math.ceil(allReleases.size() * 0.33);
//
//        if (numberOfTrainingReleases < 1) {
//            throw new IllegalArgumentException("Project has too few releases to extract training data.");
//        }
//
//        List<Release> trainingReleases = allReleases.subList(0, numberOfTrainingReleases);
//
//        List<JavaClass> trainingClasses = allClasses.stream()
//                .filter(javaClass -> javaClass.getRelease().getId() <= trainingReleases.getLast().getId())
//                .toList();
//
//       preProcClass.fillClassesInfo(allTickets, trainingClasses);
//
//        Sink.serializeInjectionToCsv(projectName, projectName, trainingReleases, trainingClasses, Sink.DataSetType.TRAINING);
//        Sink.serializeInjectionToArff(projectName, projectName, trainingReleases, trainingClasses, Sink.DataSetType.TRAINING);
//    }
//}
