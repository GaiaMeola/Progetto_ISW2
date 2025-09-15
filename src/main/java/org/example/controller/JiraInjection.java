package org.example.controller;

import org.example.logging.SeLogger;
import org.example.model.Release;
import org.example.model.Ticket;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.example.utilities.Sink;
import org.example.utilities.WebJsonReader;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//Responsabile solo del recupero e del pre-processamento dei dati
public class JiraInjection {
    private static final String  RELEASE_DATE = "releaseDate";
    private final String projName;

    private List<Release> releases = null;
    private List<Ticket> ticketsWithIssues = null;
    private List<Ticket> fixedTickets = null;

    public JiraInjection(String projectName) {
        this.projName = projectName;
    }

    public void injectReleases() throws IOException, URISyntaxException {
        this.releases = new ArrayList<>();

        JSONArray versions = fetchProjectVersions(this.projName);

        for (int i = 0; i < versions.length(); i++) {
            parseRelease(versions.getJSONObject(i)).ifPresent(releases::add);
        }

        assignIdsAndSort(releases);
    }

    //metodo per recuperare i ticket da Jira
    public void injectTickets() throws IOException, URISyntaxException {
        this.pullIssues();
        this.filterFixedApplyingProportion();
    }

    //soltanto ticket corretti
    public void filterFixedNormally() {
        this.fixedTickets = new ArrayList<>();
        for (Ticket ticket : this.ticketsWithIssues) {
            if (ticket.isCorrectTicket()) {
                this.fixedTickets.add(ticket);
            }
        }
        this.fixedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));
    }

    /**
     * Carica i ticket da Jira per il progetto, filtra quelli malformati e aggiorna ticketsWithIssues.
     */
    public void pullIssues() throws IOException, URISyntaxException {
        Logger logger = SeLogger.getInstance().getLogger();

        this.ticketsWithIssues = new ArrayList<>();
        int totalTickets = 0;
        int validTickets = 0;
        int startAt = 0;

        int total;

        do {
            JSONObject json = fetchTicketsPage(startAt);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");

            for (int i = 0; i < issues.length(); i++, startAt++) {
                totalTickets++;
                JSONObject issue = issues.getJSONObject(i);
                Ticket ticket = buildTicket(issue);
                if (ticket != null) {
                    this.ticketsWithIssues.add(ticket);
                    validTickets++;
                }
            }
        } while (startAt < total);

        this.ticketsWithIssues.sort(Comparator.comparing(Ticket::getResolutionDate));

        if (logger.isLoggable(Level.INFO)) {
            int finalTotalTickets = totalTickets;
            int finalValidTickets = validTickets;
            logger.info(() -> String.format(
                    "project=%s, total tickets=%d, valid tickets=%d",
                    projName, finalTotalTickets, finalValidTickets
            ));
        }
    }

    /** Recupera un blocco di ticket da Jira a partire da startAt */
    private JSONObject fetchTicketsPage(int startAt) throws IOException, URISyntaxException {
        // Costruisco la JQL
        String jql = String.format(
                "project=\"%s\" AND \"issueType\"=\"Bug\" AND (\"status\"=\"Closed\" OR \"status\"=\"Resolved\") AND \"resolution\"=\"Fixed\"",
                this.projName
        );
        // URL-encode della JQL
        String encodedJql = URLEncoder.encode(jql, StandardCharsets.UTF_8);

        // Costruzione finale della URL
        String url = String.format(
                "https://issues.apache.org/jira/rest/api/2/search?jql=%s&fields=key,versions,created,resolutiondate&startAt=%d&maxResults=1000",
                encodedJql, startAt
        );

        return WebJsonReader.readJsonFromUrl(url);
    }

    /**
     * Costruisce un oggetto Ticket valido a partire dal JSON di Jira.
     * Ritorna null se il ticket è malformato o incoerente.
     */
    @Nullable
    private Ticket buildTicket(JSONObject issue) {
        Logger logger = SeLogger.getInstance().getLogger();
        try {
            JSONObject fields = issue.getJSONObject("fields");

            LocalDate creationDate = LocalDate.parse(fields.getString("created").substring(0, 10));
            LocalDate resolutionDate = LocalDate.parse(fields.getString("resolutiondate").substring(0, 10));
            JSONArray affectedVersionsArray = fields.getJSONArray("versions");

            Release openingVersion = getReleaseAfterOrEqualDate(creationDate, this.releases);
            Release fixedVersion = getReleaseAfterOrEqualDate(resolutionDate, this.releases);
            List<Release> affectedReleases = extractValidAffectedReleases(affectedVersionsArray);

            if (!isTicketValid(openingVersion, fixedVersion, affectedReleases)) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(() -> String.format(
                            "Ticket %s scartato: incoerente o malformato",
                            issue.optString("key")
                    ));
                }
                return null;
            }

            Ticket ticket = new Ticket(
                    issue.getString("key"),
                    creationDate,
                    resolutionDate,
                    openingVersion,
                    fixedVersion,
                    affectedReleases
            );
            ticket.setInjectedVersion(ticket.getAffectedVersions().getFirst());

            return ticket;
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.severe(() -> String.format(
                        "Errore nella creazione del ticket %s: %s",
                        issue.optString("key"), e.getMessage()
                ));
            }
            return null;
        }
    }

    /** Estrae e filtra le affected releases coerenti con le release del progetto */
    @NotNull
    private List<Release> extractValidAffectedReleases(JSONArray affectedVersionsArray) {
        List<Release> validReleases = new ArrayList<>();
        for (int i = 0; i < affectedVersionsArray.length(); i++) {
            String affectedVersionName = affectedVersionsArray.getJSONObject(i).getString("name");
            for (Release release : this.releases) {
                if (release.getReleaseName().equals(affectedVersionName)) {
                    validReleases.add(new Release(release.getId(), release.getReleaseName(), release.getReleaseDate()));
                    break;
                }
            }
        }
        validReleases.sort(Comparator.comparing(Release::getReleaseDate));
        return validReleases;
    }

    /** Controlla se il ticket è coerente rispetto alle release di apertura, fix e affected releases */
    private boolean isTicketValid(@Nullable Release openingVersion, @Nullable Release fixedVersion, List<Release> affectedReleases) {
        if (openingVersion == null || fixedVersion == null) return false;

        // OV <= FV
        if (fixedVersion.getReleaseDate().isBefore(openingVersion.getReleaseDate())) return false;

        // Tutte le affected releases devono essere tra OV e FV
        for (Release r : affectedReleases) {
            if (r.getReleaseDate().isBefore(openingVersion.getReleaseDate()) ||
                    r.getReleaseDate().isAfter(fixedVersion.getReleaseDate())) {
                return false;
            }
        }

        // Esclude ticket antecedenti alla prima release
        return openingVersion.getId() != this.releases.getFirst().getId();
    }

    private void filterFixedApplyingProportion() {

        List<Ticket> proportionTickets = new ArrayList<>();
        this.fixedTickets = new ArrayList<>();
        double proportion;
        PreProcessProportion preProcessProportion = new PreProcessProportion();

        JSONObject proportionResults = new JSONObject();
        for(Ticket ticket : this.getTicketsWithIssues()){
            if(!ticket.isCorrectTicket()){ //ticket non corretti --> per stimare IV mancante
                proportion = preProcessProportion.computeProportion(proportionTickets,
                        ticket, true, proportionResults);
                this.preProcessTicketWithProportion(ticket, proportion);
                this.adjustAffectedVersions(ticket);
            } else{ //ticket corretti --> per usarlo come base statistica
                preProcessProportion.computeProportion(proportionTickets, ticket,
                        false, proportionResults);
                this.adjustAffectedVersions(ticket);
                proportionTickets.add(ticket);
            }
            this.fixedTickets.add(ticket);
        }
        this.fixedTickets.sort(Comparator.comparing(Ticket::getResolutionDate));

        Sink.serializeToJson(this.projName, "Proportion", proportionResults,
                Sink.FileExtension.JSON);
    }

    //metodo usato per Tickets non corretti; utilizziamo la formula fornita dal professore
    private void preProcessTicketWithProportion(@NotNull Ticket ticket, double proportion) {

        List<Release> affectedVersionsList = new ArrayList<>();
        int injectedVersionId;
        //IV = max(1; FV-(FV-OV)*P)
        if(ticket.getFixedVersion().getId() == ticket.getOpeningVersion().getId()){
            injectedVersionId = Math.max(1, (int) (ticket.getFixedVersion().getId()-proportion));
        }else{
            injectedVersionId = Math.max(1, (int) (ticket.getFixedVersion().getId()
                    -((ticket.getFixedVersion().getId()-ticket.getOpeningVersion().getId())*proportion)));
        }
        for (Release release : this.releases){
            if(release.getId() == injectedVersionId){
                affectedVersionsList.add(new Release(release.getId(), release.getReleaseName(), release.getReleaseDate()));
                break;
            }
        }
        affectedVersionsList.sort(Comparator.comparing(Release::getReleaseDate));
        ticket.setAffectedVersions(affectedVersionsList);
        ticket.setInjectedVersion(affectedVersionsList.getFirst());
    }

    //aggiunge tutte le affected versions di un ticket
    private void adjustAffectedVersions(@NotNull Ticket ticket) {
        List<Release> completeAffectedVersionsList = new ArrayList<>();
        for(int i = ticket.getInjectedVersion().getId(); i < ticket.getFixedVersion().getId(); i++){
            for(Release release : this.releases){
                if(release.getId() == i){
                    completeAffectedVersionsList.add(new Release(release.getId(),
                            release.getReleaseName(), release.getReleaseDate()));
                    break;
                }
            }
        }
        completeAffectedVersionsList.sort(Comparator.comparing(Release::getReleaseDate));
        ticket.setAffectedVersions(completeAffectedVersionsList);
    }

    private @Nullable Release getReleaseAfterOrEqualDate(LocalDate specificDate, @NotNull List<Release> releasesList) {
        releasesList.sort(Comparator.comparing(Release::getReleaseDate));
        for (Release release : releasesList) {
            if (!release.getReleaseDate().isBefore(specificDate)) {
                return release;
            }
        }
        return null;
    }

    public List<Ticket> getTicketsWithAffectedVersion (){
        List<Ticket> affectedVersions = new ArrayList<>();
        for (Ticket ticket : this.ticketsWithIssues) {
            if (ticket.isCorrectTicket()) {
                affectedVersions.add(ticket);
            }
        }
        affectedVersions.sort(Comparator.comparing(Ticket::getResolutionDate));
        return affectedVersions;
    }

    public Map<String, String> getMapReleases() {
        Map<String, String> retMap = new HashMap<>();
        this.releases.sort(Comparator.comparing(Release::getReleaseDate));
        final String name = "name";
        final String commits = "commits";
        for (Release release : this.releases) {
            Map<String, String> inner = new LinkedHashMap<>();
            inner.put(name, release.getReleaseName());
            inner.put(RELEASE_DATE, release.getReleaseDate().toString());
            inner.put(commits, String.valueOf(release.getCommitList().size()));
            retMap.put(String.valueOf(release.getId()), inner.toString());
        }
        return retMap;
    }


    public List<Ticket> getTicketsWithIssues() {
        return ticketsWithIssues;
    }

    public List<Ticket> getFixedTickets() {
        return fixedTickets;
    }

    //Restituisce la lista delle release di ogni progetto
    public List<Release> getReleases() {
        return releases;
    }

    /** Recupera l’array JSON delle versioni del progetto da Jira */
    private JSONArray fetchProjectVersions(String projectName) throws IOException, URISyntaxException {
        String url = "https://issues.apache.org/jira/rest/api/latest/project/" + projectName;
        JSONObject object = WebJsonReader.readJsonFromUrl(url);
        return object.getJSONArray("versions");
    }

    /** Converte un JSONObject in un Optional<Release> se valido */
    private Optional<Release> parseRelease(JSONObject releaseJson) {
        if (releaseJson.has(RELEASE_DATE) && releaseJson.has("name")) {
            String name = releaseJson.getString("name");
            LocalDate date = LocalDate.parse(releaseJson.getString(RELEASE_DATE));
            return Optional.of(new Release(name, date));
        }
        return Optional.empty();
    }

    /** Ordina le release per data e assegna ID progressivi */
    private void assignIdsAndSort(List<Release> releases) {
        releases.sort(Comparator.comparing(Release::getReleaseDate));
        int id = 1;
        for (Release release : releases) {
            release.setId(id++);
        }
    }
}
