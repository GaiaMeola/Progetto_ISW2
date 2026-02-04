package analyzer.bugginess;

import analyzer.csv.CsvTicketCommitWriter;
import analyzer.exception.GitOperationException;
import analyzer.exception.TicketLinkageException;
import analyzer.git.GitRepository;
import analyzer.model.TicketInfo;
import util.Configuration;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BugLinker {

    private final GitRepository repo;

    public BugLinker(GitRepository repo) {
        this.repo = repo;
    }

    public static void main(String[] args) throws Exception {

        // Disabilita i log di PMD
        ch.qos.logback.classic.Logger pmdLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("net.sourceforge.pmd");
        pmdLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        // Disabilita log DEBUG di JGit
        ch.qos.logback.classic.Logger jgitLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.eclipse.jgit");
        jgitLogger.setLevel(ch.qos.logback.classic.Level.ERROR);

        var repo = new GitRepository(Configuration.getProjectPath());
        var tickets = analyzer.jira.TicketParser.parseTicketsFromJira();

        var linker = new BugLinker(repo);
        linker.linkCommitsToTickets(tickets);

        CsvTicketCommitWriter.write(Configuration.getDebugTicketCommitsPath(), tickets);
    }

    // Trova tutti i commit che contengono l’ID del ticket nel messaggio di commit
    public void linkCommitsToTickets(Map<String, TicketInfo> tickets) throws TicketLinkageException {

        try {

            // Per ogni ticket JIRA (già filtrato da TicketParser)
            for (Map.Entry<String, TicketInfo> entry : tickets.entrySet()) {
                String ticketId = entry.getKey(); // ottieni id (es. BOOKKEEPER-123)
                TicketInfo ticket = entry.getValue(); // ottieni info ticket

                //  Cerca tutti i commit il cui messaggio contiene esattamente ticketId
                Iterable<RevCommit> commits = repo.getCommitsByMessageContaining(ticketId);

                for (RevCommit commit : commits) {

                    // Collega commit al ticket
                    String commitHash = commit.getName();
                    ticket.addCommitId(commitHash);

                    // Salva i nomi dei file toccati nel commit nel TicketInfo
                    Set<String> javaFiles = repo.getTouchedJavaFiles(commit);
                    for (String file : javaFiles) {
                        ticket.addFixedFile(file);
                    }
                }
            }
        } catch (Exception e) {
            throw new TicketLinkageException("Errore durante il collegamento commit-ticket", e);
        }
    }

    /*
    Euristica: commit senza linkage avvenuti nello stesso intervallo temporale [OV, FV],
    toccando file già associati al bug,  e scritti dallo stesso autore,
    sono stati collegati come contributi probabili alla fix
     */
    public void applyMissingCommitLinkageHeuristic(Map<String, TicketInfo> tickets) throws TicketLinkageException {

        try {
            for (TicketInfo ticket : tickets.values()) {

                // Applica l'euristica solo se abbiamo FV e OV
                if (ticket.getFixVersion() == null || ticket.getOpeningVersion() == null) continue;

                LocalDate start = ticket.getOpeningVersion();
                LocalDate end = ticket.getFixVersion();

                // Ottiene tutti i commit avvenuti tra la data di apertura e la data di fix del ticket
                List<RevCommit> candidateCommits = repo.getCommitsBetweenDates(start, end);

                matchingAuthor(candidateCommits, ticket);
            }
        } catch (Exception e) {
            throw new TicketLinkageException("Errore durante il collegamento commit-ticket", e);
        }
    }

    // Verifica se l'autore è lo stesso e fa altri check
    public void matchingAuthor(List<RevCommit> candidateCommits, TicketInfo ticket) throws GitOperationException, IOException {
        for (RevCommit commit : candidateCommits) {
            // Se già collegato via messaggio, salta
            if (ticket.getCommitIds().contains(commit.getName())) continue;

            // Recupera i file toccati da questo commit
            Set<String> touchedFiles = repo.getTouchedJavaFiles(commit);

            for (String file : touchedFiles) {
                // Verifica se il  file toccato dal commit è uno dei file già associati al ticket
                boolean isFileMatch = ticket.getFixedFiles().contains(file);

                // Verifica se autore combacia con commit già collegato
                boolean isAuthorMatch = repo.isAuthorInTicket(commit, ticket);

                // Se entrambi i match sono veri collega il commit al ticket, anche senza ID ticket nel messaggio
                if (isFileMatch && isAuthorMatch) {
                    ticket.addCommitId(commit.getName());
                    for (String f : touchedFiles) {
                        ticket.addFixedFile(f);
                    }
                    break;
                }
            }
        }
    }

}


