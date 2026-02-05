package analyzer.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TicketInfo {
    private final String id;
    private LocalDate openingVersion; // OV
    private LocalDate fixVersion;     // FV
    private LocalDate injectedVersion; // IV
    private final List<String> affectedVersions = new ArrayList<>();
    private final List<String> fixedFiles = new ArrayList<>();
    private final List<String> affectedMethods = new ArrayList<>();
    private String fixVersionName;
    private final List<String> fixVersionNames = new ArrayList<>();
    private final List<LocalDate> fixVersionDates = new ArrayList<>();
    private final List<String> commitIds = new ArrayList<>();

    public TicketInfo(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public List<LocalDate> getFixVersionDates() { return fixVersionDates; }

    public List<String> getCommitIds() { return commitIds; }
    public void addCommitId(String commitId) { this.commitIds.add(commitId); }

    public void addFixVersion(String name, LocalDate date) {
        this.fixVersionNames.add(name);
        this.fixVersionDates.add(date);
    }

    public LocalDate getOpeningVersion() { return openingVersion; }
    public void setOpeningVersion(LocalDate openingVersion) { this.openingVersion = openingVersion; }

    public LocalDate getFixVersion() { return fixVersion; }
    public void setFixVersion(LocalDate fixVersion) { this.fixVersion = fixVersion; }

    public LocalDate getInjectedVersion() { return injectedVersion; }
    public void setInjectedVersion(LocalDate injectedVersion) { this.injectedVersion = injectedVersion; }

    public List<String> getAffectedVersions() { return affectedVersions; }
    public List<String> getFixedFiles() { return fixedFiles; }
    public List<String> getAffectedMethods() { return affectedMethods; }

    public void addAffectedVersion(String version) { affectedVersions.add(version); }
    public void addFixedFile(String file) { fixedFiles.add(file); }
    public void addAffectedMethod(String method) { affectedMethods.add(method); }

    public String getFixVersionName() { return fixVersionName; }
    public void setFixVersionName(String fixVersionName) { this.fixVersionName = fixVersionName; }

    private String injectedVersionName;

    public String getInjectedVersionName() {
        return injectedVersionName;
    }

    public void setInjectedVersionName(String injectedVersionName) {
        this.injectedVersionName = injectedVersionName;
    }

}
