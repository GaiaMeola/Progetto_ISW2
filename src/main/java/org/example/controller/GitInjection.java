package org.example.controller;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Class controller responsible to link Jira-Ticket with GitHub commits and fill all the metadata
 */
public class GitInjection {

    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";
    private static final String TEMP = ".temp" + File.separator;
    private static final String GIT = File.separator + ".git";
    public static final String PMD_ANALYSIS = "pmdAnalysis";
    public static final String RELEASE = "release";
    public static final String TEST = "Test";
    public static final String JAVA_EXTENTION = ".java";
    private static final String SYS_PMD_HOME = "PMD_HOME";

    private final String repoPath;
    private final String lastBranch;

    private List<Ticket> tickets;
    private final List<Release> releases;
    protected final Git localGithub;
    private final Repository repository;

    private List<Commit> commits;
    private List<Commit> commitsWithIssues;

    private List<JavaClass> javaClasses;
    private Map<Release, List<JavaClass>> javaClassPerRelease = new LinkedHashMap<>();
    public final Map<RevCommit, List<String>> modifiedClassesForCommit;

    private final String project;
    private final Logger logger = SeLogger.getInstance().getLogger();
    private final String logName;

    /**
     * Constructor of GitInjection
     *
     * @param targetName  the project target
     * @param targetUrl   the GitHub repository URL
     * @param releaseList all release retrieved in Jira
     */
    public GitInjection(@NotNull String targetName, String targetUrl, List<Release> releaseList)
            throws GitAPIException, IOException {
        this.project = targetName;
        this.repoPath = TEMP + targetName.toLowerCase(Locale.getDefault());
        File directory = new File(repoPath);
        if (!directory.exists()) {
            localGithub = Git.cloneRepository().setURI(targetUrl).setDirectory(directory).call();
            repository = localGithub.getRepository();
        } else {
            repository = new FileRepository(repoPath + GIT);
            localGithub = new Git(repository);
        }
        this.releases = releaseList != null ? releaseList : new ArrayList<>();
        this.tickets = new ArrayList<>();
        this.commits = new ArrayList<>();
        this.commitsWithIssues = new ArrayList<>();
        this.javaClasses = new ArrayList<>();
        this.modifiedClassesForCommit = new HashMap<>();
        this.logName = this.getClass().getSimpleName() + "#" + targetName;
        String branch = null;
        try {
            branch = repository.getBranch();
        } catch (Exception ignored) {
        }
        this.lastBranch = branch;
    }

    /**
     * Used to inject commits in the revCommitList
     */
    public void injectCommits() throws GitAPIException, IOException {
        Set<RevCommit> revCommitSet = new HashSet<>();
        List<Ref> allBranches = localGithub.branchList()
                .setListMode(ListBranchCommand.ListMode.ALL)
                .call();

        for (Ref branch : allBranches) {
            Iterable<RevCommit> branchCommits = localGithub.log()
                    .add(this.repository.resolve(branch.getName()))
                    .call();
            for (RevCommit branchCommit : branchCommits) {
                revCommitSet.add(branchCommit); // HashSet evita duplicati
            }
        }

        // Filtra commit senza committer o senza data
        List<RevCommit> revCommits = revCommitSet.stream()
                .filter(rc -> rc.getCommitterIdent() != null)
                .filter(rc -> rc.getCommitterIdent().getWhenAsInstant() != null)
                .sorted(Comparator.comparing(rc -> rc.getCommitterIdent().getWhenAsInstant()))
                .toList();

        this.commits = new ArrayList<>();

        for (RevCommit revCommit : revCommits) {
            Instant commitInstant = revCommit.getCommitterIdent().getWhenAsInstant();
            LocalDateTime commitDateTime = LocalDateTime.ofInstant(commitInstant, ZoneId.systemDefault());

            // Trova la release giusta con ricerca binaria
            Release targetRelease = findReleaseForCommit(commitDateTime, this.releases);
            if (targetRelease != null) {
                Commit newCommit = new Commit(revCommit, targetRelease);
                this.commits.add(newCommit);
                targetRelease.addCommit(newCommit);
            }
        }

        // Rimuove release senza commit
        this.releases.removeIf(release -> release.getCommitList() == null || release.getCommitList().isEmpty());

        // Riassegna ID progressivi
        int i = 0;
        for (Release release : this.releases) {
            release.setId(++i);
        }

        // Ordina i commit cronologicamente
        this.commits.sort(Comparator.comparing(c -> c.getRevCommit().getCommitterIdent().getWhenAsInstant()));

        logInfo(() -> "injectCommits finished: commits=" + this.commits.size() + ", releases=" + this.releases.size());
    }

    /**
     * Cerca nei messaggi dei commit riferimenti a ticket e collega ticket<->commit
     */
    public void preprocessCommitsWithIssue() {

        //lista di commit filtrati
        this.commitsWithIssues = new ArrayList<>();

        for (Commit commit : Optional.ofNullable(this.commits).orElse(Collections.emptyList())) {
            String fullMessageRaw = null;
            //per ogni commit, viene recuperato il messaggio completo associato
            try {
                if (commit.getRevCommit() != null) fullMessageRaw = commit.getRevCommit().getFullMessage();
            } catch (Exception e) {
                logWarning(() -> "Error getting fullMessage for commit: " + e.getMessage());
            }

            String fullMessage = Optional.ofNullable(fullMessageRaw).orElse("");
            if (fullMessageRaw == null) {
                logWarning(() -> "Found null fullMessage in commit");
            }

            for (Ticket ticket : Optional.ofNullable(this.tickets).orElse(Collections.emptyList())) {
                String ticketKeyRaw = null;
                //per ogni ticket, invece, viene recuperata la ticket key
                try {
                    ticketKeyRaw = ticket.getTicketKey();
                } catch (Exception e) {
                    logWarning(() -> "Error getting ticketKey for ticket: " + e.getMessage());
                }

                String ticketKey = Optional.ofNullable(ticketKeyRaw).map(String::trim).orElse("");
                if (ticketKeyRaw == null) {
                    logWarning(() -> "Found null ticketKey in ticket");
                }

                if (ticketKey.isEmpty()) continue;

                // regex per match esatto come parola --> controlliamo se il commit ha al suo interno la ticket_key
                if (Pattern.compile("\\b" + Pattern.quote(ticketKey) + "\\b").matcher(fullMessage).find()) {
                    this.commitsWithIssues.add(commit); //se presente lo aggiungo
                    ticket.addCommit(commit);
                    commit.setTicket(ticket);
                }
            }
        }

        // Rimuove ticket senza commit associati
        if (this.tickets != null) {
            this.tickets.removeIf(ticket -> ticket.getCommitList() == null || ticket.getCommitList().isEmpty());
        }
        logInfo(() -> "preprocessCommitsWithIssue finished: commitsWithIssues=" + this.commitsWithIssues.size());
    }

    public void closeRepo() {
        try {
            this.localGithub.getRepository().close();
        } catch (Exception e) {
            logWarning(() -> "Error closing repository: " + e.getMessage());
        }
    }

    /**
     * Delegates heavy Java-class preprocessing to PreProcessJavaClass, then updates local maps
     */
    public void preprocessJavaClasses() throws IOException {
        PreProcessJavaClass pre = new PreProcessJavaClass(
                this.repository, this.localGithub, this.releases,
                this.commits, this.tickets, this.project, this.repoPath
        );

        pre.setLastBranch(this.lastBranch);
        pre.preprocessJavaClasses();

        // adopt results
        this.javaClasses = Optional.ofNullable(pre.getJavaClasses()).orElse(new ArrayList<>());
        this.commitsWithIssues = Optional.ofNullable(pre.getCommitsWithIssues()).orElse(new ArrayList<>());

        // rebuild javaClassPerRelease map here (grouping)
        this.javaClassPerRelease = new LinkedHashMap<>();
        for (JavaClass jc : this.javaClasses) {
            this.javaClassPerRelease.computeIfAbsent(jc.getRelease(), k -> new ArrayList<>()).add(jc);
        }
        logInfo(() -> "preprocessJavaClasses finished: javaClasses=" + this.javaClasses.size());
    }

    /**
     * Returning the ClassName and Body (utility kept for backward compatibility)
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
                if (path.contains(JAVA_EXTENTION) && !path.contains("/test/")) {
                    allClasses.put(path, new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return allClasses;
    }

    // --- helper per PMD/processi esterni (invariati logicamente) ---
    private void cleanGitState() throws IOException {
        String gitDir = repoPath + File.separator + ".git" + File.separator;
        String[] mergeFiles = {"MERGE_HEAD", "MERGE_MSG", "MERGE_MODE", "index"};
        for (String file : mergeFiles) {
            File f = new File(gitDir + file);
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
                "-R", Objects.requireNonNull(GitInjection.class.getClassLoader().getResource("pmd/custom.xml")).getPath(),
                "-f", "csv",
                "--no-cache",
                "-r", reportPath
        ).redirectErrorStream(true).start();
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
                //ignoring when no parent is found
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
        if (this.tickets != null) this.tickets.sort(Comparator.comparing(Ticket::getCreationDate));
        for (Ticket ticket : Optional.ofNullable(this.tickets).orElse(Collections.emptyList())) {
            List<String> ids = new ArrayList<>();
            for (Release release : Optional.ofNullable(ticket.getAffectedVersions()).orElse(Collections.emptyList())) {
                ids.add(release.getReleaseName());
            }
            Map<String, String> inner = new LinkedHashMap<>();
            inner.put("injectedVersion", ticket.getInjectedVersion() != null ? ticket.getInjectedVersion().toString() : "null");
            inner.put("openingVersion", ticket.getOpeningVersion() != null ? ticket.getOpeningVersion().toString() : "null");
            inner.put("fixedVersion", ticket.getFixedVersion() != null ? ticket.getFixedVersion().toString() : "null");
            inner.put("affectedVersions", ids.toString());
            inner.put("commits", String.valueOf(Optional.ofNullable(ticket.getCommitList()).map(List::size).orElse(0)));
            inner.put("creationDate", ticket.getCreationDate() != null ? ticket.getCreationDate().toString() : "null");
            inner.put("resolutionDate", ticket.getResolutionDate() != null ? ticket.getResolutionDate().toString() : "null");
            mapTickets.put(ticket.getTicketKey(), inner.toString());
        }

        return mapTickets;
    }

    public Map<String, String> getMapCommits() {
        Map<String, String> mapCommits = new HashMap<>();
        for (Commit commit : Optional.ofNullable(this.commits).orElse(Collections.emptyList())) {
            Map<String, String> inner = new LinkedHashMap<>();
            RevCommit revCommit = commit.getRevCommit();
            Ticket ticket = commit.getTicket();
            Release release = commit.getRelease();
            if (ticket != null) inner.put("ticketKey", commit.getTicket().getTicketKey());
            inner.put(RELEASE, release != null ? release.getReleaseName() : "null");
            inner.put("creationDate", String.valueOf(LocalDate.parse((new SimpleDateFormat(LOCAL_DATE_FORMAT)
                    .format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant()))))));
            mapCommits.put(revCommit.getName(), inner.toString());
        }
        return mapCommits;
    }

    public Map<String, String> getMapSummary() {
        Map<String, String> summaryMap = new HashMap<>();
        summaryMap.put("Releases", String.valueOf(Optional.ofNullable(this.releases).map(List::size).orElse(0)));
        summaryMap.put("Tickets", String.valueOf(Optional.ofNullable(this.tickets).map(List::size).orElse(0)));
        summaryMap.put("Commits", String.valueOf(Optional.ofNullable(this.commits).map(List::size).orElse(0)));
        summaryMap.put("Commits with bugs", String.valueOf(Optional.ofNullable(this.commitsWithIssues).map(List::size).orElse(0)));
        return summaryMap;
    }

    private void restoreRepositoryState() {
        if (lastBranch == null) {
            logWarning(() -> "No branch to restore to");
            return;
        }
        try {
            // reset and clean
            localGithub.reset().setMode(ResetCommand.ResetType.HARD).call();
            localGithub.clean().setCleanDirectories(true).call();

            // checkout back to saved branch
            localGithub.checkout().setName(lastBranch).call();

            logInfo(() -> "Repository restored to branch " + lastBranch + " at HEAD");
        } catch (GitAPIException e) {
            logError(() -> "reset the repo: " + e.getMessage());
        }
    }

    public void setTickets(List<Ticket> tickets) {
        this.tickets = tickets;
    }

    public void setCommits(List<Commit> commits) {
        this.commits = commits;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public List<Release> getReleases() {
        return releases;
    }

    public Repository getRepository() {
        return repository;
    }

    public List<Commit> getCommits() {
        return commits;
    }

    public Map<Release, List<JavaClass>> getJavaClassPerRelease() {
        return javaClassPerRelease;
    }

    public String getProject() {
        return project;
    }

    public Logger getLogger() {
        return logger;
    }

    public String getLogName() {
        return logName;
    }

    // --- logging helpers (lazy suppliers to avoid expensive concat) ---
    private void logInfo(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.INFO)) logger.info(msgSupplier.get());
    }
    private void logWarning(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.WARNING)) logger.warning(msgSupplier.get());
    }
    private void logError(Supplier<String> msgSupplier) {
        if (logger.isLoggable(Level.SEVERE)) logger.severe(msgSupplier.get());
    }

    private Release findReleaseForCommit(LocalDateTime commitDateTime, List<Release> releases) {
        int left = 0;
        int right = releases.size() - 1;
        Release result = null;

        while (left <= right) {
            int mid = (left + right) / 2;
            Release midRelease = releases.get(mid);
            LocalDateTime releaseDateTime = midRelease.getReleaseDate().atStartOfDay();

            if (!commitDateTime.isAfter(releaseDateTime)) {
                result = midRelease;
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        return result;
    }
}