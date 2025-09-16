package org.example.controller;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.example.logging.SeLogger;
import org.example.model.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Class controller responsible to link Jira-Ticket with GitHub commits and fill all the metadata
 */
public class GitInjection implements AutoCloseable {

    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd";
    private static final String TEMP = ".temp" + File.separator;
    private static final String GIT = File.separator + ".git";
    public static final String RELEASE = "release";
    public static final String TEST = "Test";

    private final String repoPath;
    private final String lastBranch;

    private List<Ticket> tickets = new ArrayList<>();
    private final List<Release> releases;
    protected final Git localGithub;
    private final Repository repository;

    private List<Commit> commits = new ArrayList<>();
    private List<Commit> commitsWithIssues = new ArrayList<>();

    // Mappa condivisa tra thread per evitare doppio clone
    private static final ConcurrentHashMap<String, Repository> repoCache = new ConcurrentHashMap<>();

    private final Map<Release, List<JavaClass>> javaClassPerRelease = new LinkedHashMap<>();

    private final String project;
    private final Logger logger = SeLogger.getInstance().getLogger();


    public GitInjection(@NotNull String targetName, String targetUrl, List<Release> releaseList)
            throws GitAPIException, IOException {

        this.project = targetName;
        this.repoPath = TEMP + targetName.toLowerCase(Locale.getDefault());
        File directory = new File(repoPath);

        // Thread-safe clone o riuso della repo esistente
        this.repository = repoCache.computeIfAbsent(targetName, key -> {
            try {
                if (!directory.exists()) {
                    Git git = Git.cloneRepository()
                            .setURI(targetUrl)
                            .setDirectory(directory)
                            .call();
                    return git.getRepository();
                } else {
                    return new FileRepository(repoPath + GIT);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize repository: " + targetName, e);
            }
        });

        this.localGithub = new Git(repository);
        this.releases = releaseList != null ? releaseList : new ArrayList<>();

        String branch = null;
        try {
            branch = repository.getBranch();
        } catch (Exception e) {
            logWarning(() -> "Unable to get current branch: " + e.getMessage());
        }
        this.lastBranch = branch;
    }

    /**
     * Inject commits from all branches into commits list and associate them to releases
     */
    public void injectCommits() throws GitAPIException, IOException {
        List<RevCommit> revCommits = new ArrayList<>();
        List<Ref> allBranches = localGithub.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();

        // Raccolta commit senza duplicati
        for (Ref branch : allBranches) {
            Iterable<RevCommit> branchCommits = localGithub.log().add(repository.resolve(branch.getName())).call();
            for (RevCommit branchCommit : branchCommits) {
                if (!revCommits.contains(branchCommit)) {
                    revCommits.add(branchCommit);
                }
            }
        }

        revCommits.sort(Comparator.comparing(rc -> Date.from(rc.getCommitterIdent().getWhenAsInstant())));

        // Conversione commit → Commit associati alle release
        this.commits = new ArrayList<>();
        SimpleDateFormat formatter = new SimpleDateFormat(LOCAL_DATE_FORMAT);
        for (RevCommit revCommit : revCommits) {
            LocalDate commitDate = LocalDate.parse(formatter.format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant())));
            LocalDate lowerBoundDate = LocalDate.parse(formatter.format(new Date(0))); // 1970-01-01

            for (Release release : this.releases) {
                LocalDate releaseDate = release.getReleaseDate();
                if (commitDate.isAfter(lowerBoundDate) && !commitDate.isAfter(releaseDate)) {
                    Commit newCommit = new Commit(revCommit, release);
                    this.commits.add(newCommit);
                    release.addCommit(newCommit);
                }
                lowerBoundDate = releaseDate;
            }
        }

        // Rimuove release senza commit
        releases.removeIf(release -> release.getCommitList() == null || release.getCommitList().isEmpty());

        // Riassegna ID progressivi
        int id = 0;
        for (Release release : releases) {
            release.setId(++id);
        }

        this.commits.sort(Comparator.comparing(c -> Date.from(c.getRevCommit().getCommitterIdent().getWhenAsInstant())));

        logInfo(() -> "injectCommits finished: commits=" + commits.size() + ", releases=" + releases.size());
    }

    /**
     * Associate commits to tickets by searching ticket keys in commit messages
     */
    public void preprocessCommitsWithIssue() {
        this.commitsWithIssues = new ArrayList<>();

        for (Commit commit : this.commits) {
            String fullMessage = safeString(commit.getRevCommit() != null ? commit.getRevCommit().getFullMessage() : "");
            if (fullMessage.isEmpty()) {
                logWarning(() -> {
                    assert commit.getRevCommit() != null;
                    return "Found null or empty commit message for commit: " + commit.getRevCommit().getName();
                });
            }

            for (Ticket ticket : this.tickets) {
                String ticketKey = safeString(ticket.getTicketKey()).trim();
                if (ticketKey.isEmpty()) continue;

                if (Pattern.compile("\\b" + Pattern.quote(ticketKey) + "\\b").matcher(fullMessage).find()) {
                    this.commitsWithIssues.add(commit);
                    ticket.addCommit(commit);
                    commit.setTicket(ticket);
                }
            }
        }

        // Rimuove ticket senza commit
        this.tickets.removeIf(t -> t.getCommitList() == null || t.getCommitList().isEmpty());

        logInfo(() -> "preprocessCommitsWithIssue finished: commitsWithIssues=" + commitsWithIssues.size());
    }

    /**
     * Delegate Java class preprocessing to PreProcessJavaClass
     */
    public void preprocessJavaClasses() throws IOException {
        List<JavaClass> javaClasses;
        PreProcessJavaClass pre = new PreProcessJavaClass(repository, localGithub, releases, commits, tickets, project, repoPath);
        pre.setLastBranch(lastBranch);
        pre.preprocessJavaClasses();

        javaClasses = Optional.ofNullable(pre.getJavaClasses()).orElse(new ArrayList<>());
        commitsWithIssues = Optional.ofNullable(pre.getCommitsWithIssues()).orElse(new ArrayList<>());

        // rebuild javaClassPerRelease map
        javaClassPerRelease.clear();
        for (JavaClass jc : javaClasses) {
            javaClassPerRelease.computeIfAbsent(jc.getRelease(), _ -> new ArrayList<>()).add(jc);
        }

        List<JavaClass> finalJavaClasses = javaClasses;
        logInfo(() -> "preprocessJavaClasses finished: javaClasses=" + finalJavaClasses.size());
    }

    // --- Getters ---
    public List<Commit> getCommitsWithIssues() { return Collections.unmodifiableList(commitsWithIssues); }
    public Repository getRepository() { return repository; }
    public Map<Release, List<JavaClass>> getJavaClassPerRelease() { return Collections.unmodifiableMap(javaClassPerRelease); }
    public String getProject() { return project; }
    public Logger getLogger() { return logger; }


    // --- Helpers ---
    private String safeString(String s) { return s != null ? s : ""; }

    private void logInfo(Supplier<String> msgSupplier) { if (logger.isLoggable(Level.INFO)) logger.info(msgSupplier.get()); }
    private void logWarning(Supplier<String> msgSupplier) { if (logger.isLoggable(Level.WARNING)) logger.warning(msgSupplier.get()); }

    @Override
    public void close() { closeRepo(); }

    public void closeRepo() {
        try {
            if (localGithub != null) localGithub.close();
            if (repository != null) repository.close();
        } catch (Exception e) {
            logWarning(() -> "Error closing repository: " + e.getMessage());
        }
    }

    // --- Setters for testing / external injection ---
    public void setTickets(List<Ticket> tickets) { this.tickets = tickets != null ? tickets : new ArrayList<>(); }
    public void setCommits(List<Commit> commits) { this.commits = commits != null ? commits : new ArrayList<>(); }

    public Collection<Object> getMapTickets() {
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
        return new ArrayList<>(mapTickets.values());
    }

    public Collection<Object> getMapCommits() {
        Map<String, String> mapCommits = new HashMap<>();
        for (Commit commit : Optional.ofNullable(this.commits).orElse(Collections.emptyList())) {
            Map<String, String> inner = new LinkedHashMap<>();
            RevCommit revCommit = commit.getRevCommit();
            Ticket ticket = commit.getTicket();
            Release release = commit.getRelease();
            if (ticket != null) inner.put("ticketKey", ticket.getTicketKey());
            inner.put(RELEASE, release != null ? release.getReleaseName() : "null");
            inner.put("creationDate", String.valueOf(
                    LocalDate.parse(new SimpleDateFormat(LOCAL_DATE_FORMAT)
                            .format(Date.from(revCommit.getCommitterIdent().getWhenAsInstant())))
            ));
            mapCommits.put(revCommit.getName(), inner.toString());
        }
        return new ArrayList<>(mapCommits.values());
    }

    public Collection<Object> getMapSummary() {
        Map<String, String> summaryMap = new HashMap<>();
        summaryMap.put("Releases", String.valueOf(Optional.ofNullable(this.releases).map(List::size).orElse(0)));
        summaryMap.put("Tickets", String.valueOf(Optional.ofNullable(this.tickets).map(List::size).orElse(0)));
        summaryMap.put("Commits", String.valueOf(Optional.ofNullable(this.commits).map(List::size).orElse(0)));
        summaryMap.put("Commits with bugs", String.valueOf(Optional.ofNullable(this.commitsWithIssues).map(List::size).orElse(0)));
        return new ArrayList<>(summaryMap.values());
    }
}