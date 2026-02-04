package analyzer;

import analyzer.bugginess.BugLabeler;
import analyzer.bugginess.BugLinker;
import analyzer.csv.CsvDebugWriter;
import analyzer.csv.CsvHandler;
import analyzer.git.GitRepository;
import analyzer.jira.GetReleaseInfo;
import analyzer.jira.TicketParser;
import analyzer.metrics.MethodMetricsExtractor;
import analyzer.model.Commit;
import analyzer.model.MethodInfo;
import analyzer.model.Release;
import analyzer.model.TicketInfo;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.LoggerFactory;
import util.Configuration;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatasetApp {

    /*
    Questa classe contiene il metodo main che va a gestire il flusso di esecuzione
    necessario a realizzare la milestone 1, quindi ha creare i dataset richiesti
    */


    public static void main(String[] args) {

        if (!Configuration.ACTIVATE_LOG) {
            // Disabilita i log di PMD
            ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
            pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
            // Disabilita log di JGit
            ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
            jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);
        }

        try {

            // Lista dei metodi che verranno analizzati
            List<MethodInfo> methods;

            // Recupera primo 33% release del progetto
            List<Release> datasetReleases = GetReleaseInfo.getDatasetReleases();

            // Mappa per asscoaire le date delle release agli ID
            Map<String, LocalDate> releaseDatesById = new HashMap<>();
            for (Release r : datasetReleases) {
                releaseDatesById.put(r.getName(), r.getReleaseDate());
            }

            // Inizializza Git + estrattore delle metriche
            GitRepository repo = new GitRepository(Configuration.getProjectPath());
            MethodMetricsExtractor extractor = new MethodMetricsExtractor(repo);

            // Inizializza lista dei commit selezionati per ogni release (a cui fare checkout)
            List<Commit> selectedCommits = new ArrayList<>();

            if (Configuration.BASIC_DEBUG) Configuration.logger.info("Analisi delle metriche statiche avviata:");

            // Itera su ogni release valida
            for (Release rel : datasetReleases) {

                if (Configuration.BASIC_DEBUG)
                    Configuration.logger.info("Analizzo release: " + rel.getName() + " (" + rel.getReleaseDate() + ")");

                // Trova il commit più recente prima della data di release
                RevCommit commit = repo.findLastCommitBefore(rel.getReleaseDate());
                if (commit == null) {
                    Configuration.logger.info("Nessun commit trovato prima della release " + rel.getName());
                    continue;
                }

                if (Configuration.BASIC_DEBUG) {
                    Configuration.logger.info(" Commit selezionato:");
                    Configuration.logger.info(" → ID: " + commit.getId().getName());
                    Configuration.logger.info(" → Data: " + commit.getAuthorIdent().getWhen());
                    Configuration.logger.info(" → Messaggio: " + commit.getShortMessage());
                }

                // Salva info sul commit
                Commit c = new Commit();
                c.setId(commit.getName());
                c.setAuthor(commit.getAuthorIdent().getName());
                c.setDate(commit.getAuthorIdent().getWhen().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
                c.setMessage(commit.getShortMessage());
                c.setFilesTouched(null);
                selectedCommits.add(c);

                // Fai il checkout al commit
                repo.checkoutCommit(commit);

                // Imposta la release in corso
                extractor.setCurrentRelease(rel.getName());
                extractor.setCurrentReleaseDate(rel.getReleaseDate());

                // Analizza il progetto per la release corrente e calcola le metriche
                extractor.analyzeProject(Configuration.getProjectPath(), rel);

                // Salva una versione CSV dei commit a cui facciamo il checkout in ogni release
                CsvDebugWriter.writeCommitCsv(Configuration.getCommitDebugCsvPath(), selectedCommits);
            }

            // Scrivi i risultati nel file CSV
            extractor.exportResults(Configuration.getOutputCsvPath());

            // Chiude correttamente la connessione con la repository Git
            repo.close();

            // Ottieni info metodi analizzati
            methods = extractor.getAnalyzedMethods();

            if (Configuration.BASIC_DEBUG) Configuration.logger.info("Inizio fase di etichettatura ...");

            // Estrai ticket da JIRA
            Map<String, TicketInfo> tickets = TicketParser.parseTicketsFromJira();

            // Collega commit ai ticket
            BugLinker linker = new BugLinker(repo);
            linker.linkCommitsToTickets(tickets);
            linker.applyMissingCommitLinkageHeuristic(tickets);

            // Etichetta i metodi usando tutti i dati a disposizione
            List<Release> allReleases = GetReleaseInfo.getAllReleases();
            BugLabeler.labelMethods(methods, tickets, repo, allReleases);

            // Riscrivi CSV aggiornato
            CsvHandler csvHandler = new CsvHandler();
            csvHandler.writeCsv(Configuration.getOutputCsvPath(), methods);

            if (Configuration.BASIC_DEBUG) Configuration.logger.info("Analisi completata. File salvato in: " + Configuration.getOutputCsvPath());

        } catch (Exception e) {
            Configuration.logger.info("Errore durante l'esecuzione.");
            e.printStackTrace(); // <--- Aggiungi questo
        }
    }
}
