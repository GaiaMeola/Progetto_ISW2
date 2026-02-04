package analyzer.metrics;

import analyzer.model.MethodHistoryStats;
import analyzer.model.MethodInfo;
import analyzer.model.Release;
import analyzer.git.GitRepository;
import util.Configuration;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HistoricalMetricExtractor {

    private final GitRepository repo;

    public HistoricalMetricExtractor(GitRepository repo) {
        this.repo = repo;
    }

    public void analyzeHistoryForMethods(List<MethodInfo> methods, Release release) {

        // Raggruppa i metodi per file .java con una mappa
        Map<String, List<MethodInfo>> methodsByFile = methods.stream()
                .collect(Collectors.groupingBy(m -> extractFilePathFromMethodName(m.getMethodName())));

        // Inizializza mappe di supporto
        Map<String, MethodHistoryStats> statsMap = new HashMap<>(); // metriche storiche per ogni metodo
        Map<String, MethodInfo> methodByKey = new HashMap<>(); // collegamento con method info

        // Loop su ogni file e i suoi metodi
        for (Map.Entry<String, List<MethodInfo>> entry : methodsByFile.entrySet()) {

            String filePath = entry.getKey();
            List<MethodInfo> methodList = entry.getValue();

            // Costruisce chiave unica per ogni metodo: fullPathMetodo@releaseID#startLine
            for (MethodInfo m : methodList) {
                String key = buildMethodKey(m);
                methodByKey.put(key, m);
            }

            try {
                // Recupera i commit che modificano il file (antecedenti alla release)
                Iterable<RevCommit> commits = repo.getCommitsTouchingFileBefore(filePath, release.getReleaseDate());

                // Analizza il diff tra parent e commit per ogni commit che ha toccato il file.
                for (RevCommit commit : commits) {
                    if (commit.getParentCount() == 0) {
                        continue;
                    }
                    RevCommit parent = repo.parseCommit(commit);
                    analyzeDiffBetweenCommits(filePath, parent, commit, methodList, statsMap);
                }

            } catch (Exception e) {
                Configuration.logger.log(Level.SEVERE,
                        String.format("Errore analizzando la storia per il file: %s", filePath), e);
            }
        }

        // Alla fine, applica i valori raccolti ai MethodInfo
        for (Map.Entry<String, MethodHistoryStats> entry : statsMap.entrySet()) {
            String key = entry.getKey();
            MethodInfo method = methodByKey.get(key);
            MethodHistoryStats stats = entry.getValue();

            method.setMethodHistories(stats.getMethodHistories()); //  numero commit che modificano il metodo
            method.setStmtAdded(stats.getStmtAdded()); // linee aggiunte
            method.setStmtDeleted(stats.getStmtDeleted()); // linee cancellate
            method.setChurn(stats.getChurn()); //  somma righe modificate
            method.setDistinctAuthors(stats.getDistinctAuthors()); // autori distinti
        }
    }

    // Costruttore chiave per il metodo
    private String buildMethodKey(MethodInfo m) {
        return m.getMethodName() + "@" + m.getReleaseId() + "#" + m.getStartLine();
    }

    // Pulisce il path del metodo per ottenere il relativo file .java
    private String extractFilePathFromMethodName(String fullName) {
        int idx = fullName.lastIndexOf(".java");
        if (idx != -1) {
            String relative = fullName.substring(0, idx + 5);
            // Normalizza rimuovendo il path assoluto
            if (relative.contains(Configuration.getProjectSubstring())) {
                return relative.substring(relative.indexOf(Configuration.getProjectSubstring()) + Configuration.getProjectSubstring().length());
            }
            return relative;
        }
        return fullName;
    }

    private void analyzeDiffBetweenCommits(String filePath, RevCommit parent, RevCommit current,
                                           List<MethodInfo> methods,
                                           Map<String, MethodHistoryStats> statsMap) {

        //  Crea un oggetto DiffFormatter che analizza le differenze tra due commit
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            // Configura il DiffFormatter
            df.setRepository(repo.getGit().getRepository());
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);

            // Ottiene la lista dei file modificati tra parent e current
            List<DiffEntry> diffs = df.scan(parent.getTree(), current.getTree());

            //  Filtra i diff per il file filePath attualmente analizzato
            for (DiffEntry diff : diffs) {
                if (!diff.getNewPath().equals(filePath)) continue;

                // Estrae la lista degli Edit, cioè le modifiche riga-per-riga nel file
                List<Edit> edits = df.toFileHeader(diff).toEditList();

                //  Per ogni metodo nel file corrente
                for (MethodInfo method : methods) {
                    calculateStatsForEdit(method, edits, current, statsMap);
                }

            }

        } catch (Exception e) {
            Configuration.logger.log(Level.SEVERE,
                    String.format("Errore nel diff tra commit %s e %s", parent.getName(), current.getName()), e);
        }
    }

    private void calculateStatsForEdit(MethodInfo method, List<Edit> edits, RevCommit current, Map<String, MethodHistoryStats> statsMap) {
        int start = method.getStartLine(); // riga iniziale metodo
        int end = method.getEndLine(); // riga finale metodo
        int added = 0;
        int deleted = 0;
        boolean touched = false;

        // Loop su ogni Edit
        for (Edit edit : edits) {
            // intervallo righe edit nel commit attuale
            int editStart = edit.getBeginB(); // Riga di inizio nella versione nuova
            int editEnd = edit.getEndB(); // Riga di fine nella versione nuova

            // se c'è sovrapposizione tra edit e metodo
            if (editEnd > start && editStart < end) {
                touched = true;

                // Calcolo righe aggiunte nel metodo
                int overlapStart = Math.max(editStart, start);
                int overlapEnd = Math.min(editEnd, end);
                // Se l’edit è completamente fuori dal metodo, questo valore è negativo e mettiamo 0
                added += Math.max(0, overlapEnd - overlapStart);

                // Calcolo delle righe rimosse nel metodo
                int editStartA = edit.getBeginA(); // Riga di inizio nella versione vecchia
                int editEndA = edit.getEndA(); // Riga di fine nella versione vecchia
                int overlapStartA = Math.max(editStartA, start);
                int overlapEndA = Math.min(editEndA, end);
                deleted += Math.max(0, overlapEndA - overlapStartA);
            }
        }

        /* Se il metodo è stato toccato registra:
         - numero righe aggiunte
         - numero righe rimosse
         - autore commit
         */

        if (touched) {
            String key = buildMethodKey(method);
            MethodHistoryStats stats = statsMap.computeIfAbsent(key, k -> new MethodHistoryStats());
            stats.addEdit(added, deleted, current.getAuthorIdent().getName());
        }
    }
}

