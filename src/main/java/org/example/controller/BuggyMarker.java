package org.example.controller;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BuggyMarker {

    private static final String JAVA_EXTENSION = ".java";

    /**
     * Marca le classi buggy e ritorna i commit che hanno introdotto bug
     */
    // Ritorna i commit buggy per poterli salvare
    public static List<Commit> markClasses(List<JavaClass> javaClasses, List<Ticket> tickets, Repository repository) {
        List<Commit> buggyCommits = new ArrayList<>();

        for (JavaClass jc : javaClasses) {
            jc.getMetrics().setBug(false);
        }

        for (Ticket ticket : tickets) {
            List<Commit> ticketCommits = ticket.getCommitList();
            Release injectedVersion = ticket.getInjectedVersion();

            for (Commit commit : ticketCommits) {
                RevCommit revCommit = commit.getRevCommit();
                if (revCommit == null) continue;

                java.time.LocalDate commitDate = java.time.LocalDate.ofInstant(
                        revCommit.getCommitterIdent().getWhenAsInstant(),
                        java.time.ZoneId.systemDefault()
                );

                if (!commitDate.isBefore(ticket.getCreationDate()) && !commitDate.isAfter(ticket.getResolutionDate())) {
                    List<String> touchedClasses;
                    try {
                        touchedClasses = getTouchedClassesNamesStatic(repository, revCommit);
                    } catch (IOException e) {
                        continue;
                    }

                    boolean isBuggyCommit = false;
                    for (String modifiedClass : touchedClasses) {
                        if (markBuggyClass(javaClasses, modifiedClass, injectedVersion, commit.getRelease())) {
                            isBuggyCommit = true;
                        }
                    }

                    if (isBuggyCommit) {
                        buggyCommits.add(commit); // aggiungi alla lista
                    }
                }
            }
        }

        return buggyCommits;
    }

    /**
     * Marca una singola classe come buggy e ritorna true se è stata marcata
     */
    private static boolean markBuggyClass(List<JavaClass> javaClasses, String modifiedClass,
                                          Release injectedVersion, Release fixedVersion) {

        List<JavaClass> fixedClasses = javaClasses.stream()
                .filter(jc -> jc.getRelease().getId() == fixedVersion.getId())
                .toList();

        boolean marked = false;

        for (JavaClass javaClass : javaClasses) {
            if (javaClass.getName().equals(modifiedClass)
                    && javaClass.getRelease().getId() < fixedVersion.getId()
                    && javaClass.getRelease().getId() >= injectedVersion.getId()) {

                javaClass.getMetrics().setBug(true);
                marked = true;

                javaClass.getMethods().entrySet().forEach(entry ->
                        fixedClasses.stream()
                                .filter(fc -> fc.getName().equals(modifiedClass))
                                .findAny()
                                .ifPresent(fixedClass -> markMethodDiff(javaClass, entry, fixedClass.getMethods()))
                );
            }
        }

        return marked;
    }

    private static void markMethodDiff(JavaClass javaClass, Map.Entry<String, String> entry,
                                       Map<String, String> methodMap) {
        MethodMetrics metrics = javaClass.getMethodsMetrics().get(entry.getKey());
        if (metrics == null) {
            metrics = new MethodMetrics();
            javaClass.getMethodsMetrics().put(entry.getKey(), metrics);
        }

        if (!methodMap.containsKey(entry.getKey())) {
            metrics.setBug(true);
            return;
        }

        String fixedBody = normalizeCode(methodMap.get(entry.getKey()));
        String oldBody = normalizeCode(entry.getValue());

        if (!fixedBody.equals(oldBody)) {
            metrics.setBug(true);
        }
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "").trim();
    }

    public static List<String> getTouchedClassesNamesStatic(Repository repository, RevCommit revCommit) throws IOException {
        List<String> touchedClassesNames = new ArrayList<>();

        try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);

            List<DiffEntry> entries;
            if (revCommit.getParentCount() > 0) {
                entries = diffFormatter.scan(revCommit.getParent(0).getTree(), revCommit.getTree());
            } else {
                entries = diffFormatter.scan(null, revCommit.getTree()); // initial commit
            }

            for (DiffEntry entry : entries) {
                String path = entry.getNewPath();
                if (path.endsWith(JAVA_EXTENSION) && !path.toLowerCase().contains("/test/")) {
                    touchedClassesNames.add(path);
                }
            }
        }

        return touchedClassesNames;
    }
}