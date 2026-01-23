package analyzer.bugginess;

import analyzer.csv.CsvBugLabelerDebug;
import analyzer.git.GitRepository;
import analyzer.model.MethodInfo;
import analyzer.model.TicketInfo;
import analyzer.model.Release;
import util.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.*;
import java.util.logging.Level;

public class BugLabeler {

    /*
    Questa classe:
    - per ogni ticket bug stima le versioni buggy
    - identifica i file toccati dai commit associati al ticket
    - verifica i metodi toccati per etichettarli come buggy nelle versioni corrette
     */

    private static final String TICKET_PREFIX = "Ticket ";

    private BugLabeler() {
        // Utility class → no instance
    }

    // Etichettatura metodi buggy
    public static void labelMethods(List<MethodInfo> methods, Map<String, TicketInfo> tickets, GitRepository repo, List<Release> releases) {

        int buggyFromAV = 0;
        int buggyFromProportion = 0;

        // 1. Raggruppa i metodi per file+release
        Map<String, List<MethodInfo>> methodsByFileAndRelease = groupMethodsByFileAndRelease(methods);

        // 2. Inizializza helper
        MethodTouchAnalyzer analyzer = new MethodTouchAnalyzer(repo);

        // 3. Inizializza ProportionEstimator per stimare IV
        ProportionEstimator estimator = new ProportionEstimator(releases);
        registerValidTickets(tickets, estimator);

        // 4. Lista metodi che verranno etichettati per debug
        List<String[]> debugRows = new ArrayList<>();

        // 5. Per ogni ticket
        for (TicketInfo ticket : tickets.values()) {

            // Stabilisce in quali release il bug era già presente
            Set<String> buggyReleases = estimateBuggyReleases(ticket, estimator);

            // Skip dei ticket non preocessabile
            if (!isProcessable(ticket, buggyReleases, methods)) {
                if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                    Configuration.logger.info(String.format("%s%s: ticket ignorato (non processabile)", TICKET_PREFIX, ticket.getId()));
                }
                continue;
            }

            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info(String.format("%s%s: buggyReleases stimate → %s", TICKET_PREFIX, ticket.getId(), buggyReleases));
            }

            // Etichettatura
            int[] result = processTicketCommits(ticket, repo, buggyReleases, methodsByFileAndRelease, analyzer, debugRows);

            buggyFromAV += result[0];
            buggyFromProportion += result[1];

            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info("STATISTICHE FINE ETICHETTATURA:");
                Configuration.logger.info(String.format(
                        "→ Etichettati AV: %d | Proportion: %d | Totale: %d",
                        buggyFromAV, buggyFromProportion, buggyFromAV + buggyFromProportion
                ));
            }
        }

        // 6. CSV di debug opzionale
        writeDebugCsv(debugRows);
    }

    // Raggruppa i metodi per file e release
    private static Map<String, List<MethodInfo>> groupMethodsByFileAndRelease(List<MethodInfo> methods) {
        Map<String, List<MethodInfo>> map = new HashMap<>();
        for (MethodInfo m : methods) {
            String filePath = extractFileFromMethodName(m.getMethodName());
            String key = filePath + "@" + m.getReleaseId(); // key: filePath@releaseId
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(m); // value: lista dei metodi in quel file per quella release
        }
        return map;
    }

    // Dato un nome metodo completo con l'intero path estrae solo la parte del file .java
    private static String extractFileFromMethodName(String methodName) {
        int idx = methodName.lastIndexOf(".java");
        if (idx != -1) {
            String relative = methodName.substring(0, idx + 5);
            if (relative.contains(Configuration.getProjectSubstring())) {
                return relative.substring(relative.indexOf(Configuration.PROJECT1_SUBSTRING) + Configuration.PROJECT1_SUBSTRING.length());
            }
            return relative;
        }
        return methodName;
    }

    // Registra nel ProportionEstimator tutti i ticket che hanno almeno una AV
    private static void registerValidTickets(Map<String, TicketInfo> tickets, ProportionEstimator estimator) {
        for (TicketInfo t : tickets.values()) {
            if (!t.getAffectedVersions().isEmpty()) {
                estimator.registerValidTicket(t);
            }
        }
    }

    // Restituisce un Set<String> con le release affette dal bug corrente
    private static Set<String> estimateBuggyReleases(TicketInfo ticket, ProportionEstimator estimator) {
        Set<String> buggyReleases = new HashSet<>();

        if (!ticket.getAffectedVersions().isEmpty()) { //  AV esplicite → le usiamo direttamente
            buggyReleases.addAll(ticket.getAffectedVersions());
        } else { // AV mancante → stimiamo IV con il modello proportion

            logNoAV(ticket); // print debug

            // Stima mediante proportion
            String estIV = estimator.estimateIV(ticket); // IV = FV - (FV - OV) × P
            String fv = estimator.normalizeVersionName(ticket.getFixVersionName());

            logEstimationResult(estIV, ticket);

            //  Costruzione dell’intervallo [IV, FV) e add alle buggy release
            buggyReleases.addAll(computeIntervalReleases(estIV, fv, estimator));
        }

        return buggyReleases;
    }

    // Print debug
    private static void logNoAV(TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info(TICKET_PREFIX + ticket.getId() + " NON ha AV → provo stima IV");
            Configuration.logger.info("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());
        }
    }
    private static void logEstimationResult(String estIV, TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            if (estIV == null) {
                Configuration.logger.info(String.format("Ticket %s: stima IV fallita.", ticket.getId()));
            } else {
                Configuration.logger.info(String.format("Ticket %s: stima IV riuscita -> %s", ticket.getId(), estIV));
            }
        }
    }

    private static Set<String> computeIntervalReleases(String estIV, String fv, ProportionEstimator estimator) {

        // Inizializza il set
        Set<String> releases = new HashSet<>();

        // Se IV o FV non sono noti, ritorna un set vuoto
        if (estIV == null || fv == null) return releases;

        // Ottiene l’indice numerico della Injected Version e della Fix Version
        ReleaseIndexMapper mapper = estimator.getMapper();
        int ivIndex = mapper.getIndex(estIV);
        int fvIndex = mapper.getIndex(fv);

        // Crea l’intervallo semi-aperto [IV, FV)
        for (int i = ivIndex; i < fvIndex; i++) {
            String rel = mapper.getReleaseName(i);
            if (rel != null) releases.add(rel);
        }

        return releases;
    }

    /*
    Stabilisce se un ticket può essere effettivamente usato per etichettare metodi.
    - ha FV definita
    - ha commit collegati
    - ha almeno una buggyRelease associata
     */
    private static boolean isProcessable(TicketInfo ticket, Set<String> buggyReleases, List<MethodInfo> methods) {
        if (ticket.getFixVersion() == null || ticket.getCommitIds().isEmpty()) return false;
        if (buggyReleases.isEmpty()) return false;
        return filterValidBuggyReleases(ticket, buggyReleases, methods);
    }

    private static boolean filterValidBuggyReleases(TicketInfo ticket, Set<String> buggyReleases, List<MethodInfo> methods) {
        // Crea il set di tutte le release effettivamente presenti nel dataset dei metodi analizzati
        Set<String> availableReleases = new HashSet<>();
        for (MethodInfo m : methods) {
            availableReleases.add(m.getReleaseId());
        }

        // Rimuove le buggyReleases che non sono presenti tra le release disponibili
        buggyReleases.retainAll(availableReleases);

        // Se dopo l’intersezione non rimane nessuna release utile, il ticket viene scartato
        if (buggyReleases.isEmpty()) {
            if (Configuration.LABELING_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
                Configuration.logger.info(String.format("%s%s: tutte le buggyReleases fuori dal dataset", TICKET_PREFIX, ticket.getId()));
            }
            return false;
        }

        return true;
    }

    /*
    Per ogni commit associato a un ticket bug:
    - Trova i file .java toccati dal commit
    - Per ciascun file, verifica se è presente in una delle release buggy
    - Se sì, individua i metodi toccati in quel file
    - Etichetta quei metodi come buggy
     */
    private static int[] processTicketCommits(
            TicketInfo ticket,
            GitRepository repo,
            Set<String> buggyReleases,
            Map<String, List<MethodInfo>> methodsByFileAndRelease,
            MethodTouchAnalyzer analyzer,
            List<String[]> debugRows
    ) {
        int[] counters = new int[]{0, 0}; // counters[0] = buggyFromAV, counters[1] = buggyFromProportion

        // Scorre tutti i commit legati al ticket
        for (String commitHash : ticket.getCommitIds()) {
            RevCommit commit = resolveCommit(commitHash, repo); // Lo risolve (resolveCommit) da hash a RevCommit
            if (commit == null) continue; // Se fallisce, lo salta

            Set<String> files = new HashSet<>(ticket.getFixedFiles());

            //  Per ogni file toccato dal commit
            for (String filePath : files) {
                for (String releaseId : buggyReleases) { // E per ogni release considerata "buggy"
                    String key = filePath + "@" + releaseId; // Costruisce la chiave file@release per accedere alla lista di metodi corrispondenti
                    if (!methodsByFileAndRelease.containsKey(key)) continue;

                    // Trova i metodi modificati
                    List<MethodInfo> candidates = methodsByFileAndRelease.get(key);
                    Set<MethodInfo> touched = analyzer.getTouchedMethods(commit, filePath, candidates);

                    // Etichetta i metodi toccati
                    processTouchedMethods(touched, ticket, commit, debugRows, counters);
                }
            }
        }

        return counters;
    }

    // Recupera l’oggetto RevCommit a partire dall'hash del commit
    private static RevCommit resolveCommit(String commitHash, GitRepository repo) {
        try {
            return repo.getGit().log()
                    .add(repo.getGit().getRepository().resolve(commitHash))
                    .call().iterator().next();
        } catch (Exception e) {
            Configuration.logger.severe(String.format("Errore leggendo commit %s", commitHash));
            return null;
        }
    }

    /*
    Etichetta come buggy tutti i metodi che sono stati modificati (toccati) da un commit bug-fix,
    se non sono già stati etichettati.
     */
    private static void processTouchedMethods(
            Set<MethodInfo> touched,
            TicketInfo ticket,
            RevCommit commit,
            List<String[]> debugRows,
            int[] counters
    ) {
        for (MethodInfo m : touched) {
            if (!m.isBugginess()) {
                m.setBugginess(true);
                if (!ticket.getAffectedVersions().isEmpty()) {
                    counters[0]++;
                } else {
                    counters[1]++;
                }

                if (Configuration.LABELING_DEBUG) {
                    debugRows.add(new String[]{
                            ticket.getId(),
                            commit.getName(),
                            m.getMethodName(),
                            m.getReleaseId()
                    });
                }
            }
        }
    }

    // Stampa file debug
    private static void writeDebugCsv(List<String[]> debugRows) {
        if (Configuration.LABELING_DEBUG) {
            CsvBugLabelerDebug.writeCsv(Configuration.getDebugBuggyMethods(), debugRows);
        }
    }

}
