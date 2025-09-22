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
import java.time.format.DateTimeParseException;
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

    /**
     * Scarica i ticket da JIRA, salva raw/filtered/proportion come JSON e lascia i risultati in memoria.
     */
    public void injectTickets() throws IOException, URISyntaxException {
        // 1) scarica da JIRA
        this.pullIssues();

        // 2) salva i raw (tutti i ticket scaricati)
        Sink.serializeToJson(this.projName, "TicketsRaw",
                new JSONObject(Map.of("tickets", ticketsToJsonArray(this.ticketsWithIssues))),
                Sink.FileExtension.JSON);

        // 3) filtro "normale" e salvo
        this.filterFixedNormally();
        Sink.serializeToJson(this.projName, "TicketsFilteredNormally",
                new JSONObject(Map.of("tickets", ticketsToJsonArray(this.fixedTickets))),
                Sink.FileExtension.JSON);

        // 4) applico proportion e salvo il risultato finale
        this.filterFixedApplyingProportion();
        Sink.serializeToJson(this.projName, "TicketsWithProportion",
                new JSONObject(Map.of("tickets", ticketsToJsonArray(this.fixedTickets))),
                Sink.FileExtension.JSON);
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
                }
            }
        } while (startAt < total);

        // ordino per data di risoluzione
        this.ticketsWithIssues.sort(Comparator.comparing(Ticket::getResolutionDate));

        // calcolo i valid tickets solo ora (quelli già "corretti" senza proportion)
        int validTickets = (int) this.ticketsWithIssues.stream()
                .filter(Ticket::isCorrectTicket)
                .count();

        if (logger.isLoggable(Level.INFO)) {
            int finalTotalTickets = totalTickets;
            logger.info(() -> String.format(
                    "project=%s, total tickets=%d, valid tickets=%d",
                    projName, finalTotalTickets, validTickets
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

            if (openingVersion == null || fixedVersion == null) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.warning(() -> String.format("Ticket %s scartato: OV o FV null", issue.optString("key")));
                }
                return null; // scarta ticket senza OV o FV
            }

            // Estrae AV coerenti con le release del progetto
            List<Release> affectedReleases = extractValidAffectedReleases(affectedVersionsArray);

            // Controllo di consistenza AV vs OV
            if (!affectedReleases.isEmpty()) {
                Release oldestAV = affectedReleases.getFirst();
                if (!oldestAV.getReleaseDate().isAfter(openingVersion.getReleaseDate())) {
                    // AV coerente
                    affectedReleases.sort(Comparator.comparing(Release::getReleaseDate));
                } else {
                    // AV incoerente, fallback a OV
                    if (logger.isLoggable(Level.WARNING)) {
                        logger.warning(() -> String.format(
                                "Ticket %s AV incoerente: oldest AV %s dopo OV %s, uso fallback OV",
                                issue.optString("key"),
                                oldestAV.getReleaseName(),
                                openingVersion.getReleaseName()
                        ));
                    }
                    affectedReleases = new ArrayList<>();
                    affectedReleases.add(openingVersion);
                }
            }

            Ticket ticket = new Ticket(
                    issue.getString("key"),
                    creationDate,
                    resolutionDate,
                    openingVersion,
                    fixedVersion,
                    affectedReleases
            );

            // Imposta IV se AV presente, altrimenti rimane da calcolare tramite proportion
            if (!affectedReleases.isEmpty()) {
                ticket.setInjectedVersion(affectedReleases.getFirst());
            }

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
        // IV = max(1; FV-(FV-OV)*P)
        if (ticket.getFixedVersion().getId() == ticket.getOpeningVersion().getId()) {
            injectedVersionId = Math.max(1, (int) (ticket.getFixedVersion().getId() - proportion));
        } else {
            injectedVersionId = Math.max(1, (int) (ticket.getFixedVersion().getId()
                    - ((ticket.getFixedVersion().getId() - ticket.getOpeningVersion().getId()) * proportion)));
        }
        for (Release release : this.releases) {
            if (release.getId() == injectedVersionId) {
                affectedVersionsList.add(new Release(release.getId(), release.getReleaseName(), release.getReleaseDate()));
                break;
            }
        }
        affectedVersionsList.sort(Comparator.comparing(Release::getReleaseDate));
        ticket.setAffectedVersions(affectedVersionsList);

        // protezione: se non ho trovato la release calcolata, scelgo fallback
        if (!affectedVersionsList.isEmpty()) {
            ticket.setInjectedVersion(affectedVersionsList.getFirst());
        } else {
            // fallback: usa openingVersion se presente, altrimenti fixedVersion
            if (ticket.getOpeningVersion() != null) {
                ticket.setInjectedVersion(ticket.getOpeningVersion());
                ticket.setAffectedVersions(List.of(ticket.getOpeningVersion()));
            } else {
                ticket.setInjectedVersion(ticket.getFixedVersion());
                ticket.setAffectedVersions(List.of(ticket.getFixedVersion()));
            }
            SeLogger.getInstance().getLogger().warning(() ->
                    String.format("[%s] fall-back IV for ticket %s (no matching release for injected id=%d)",
                            projName, ticket.getTicketKey(), injectedVersionId));
        }
    }

    // aggiunge tutte le affected versions di un ticket
    private void adjustAffectedVersions(@NotNull Ticket ticket) {
        List<Release> completeAffectedVersionsList = new ArrayList<>();

        // Creo una mappa id -> release per lookup rapido
        Map<Integer, Release> releaseMap = new HashMap<>();
        for (Release r : this.releases) {
            releaseMap.put(r.getId(), r);
        }

        int ivId = ticket.getInjectedVersion().getId();
        int fvId = ticket.getFixedVersion().getId();

        for (int i = ivId; i < fvId; i++) {
            Release r = releaseMap.get(i);
            if (r != null) {
                completeAffectedVersionsList.add(new Release(r.getId(), r.getReleaseName(), r.getReleaseDate()));
            }
        }

        completeAffectedVersionsList.sort(Comparator.comparing(Release::getReleaseDate));
        ticket.setAffectedVersions(completeAffectedVersionsList);
    }


    private @Nullable Release getReleaseAfterOrEqualDate(LocalDate specificDate, @NotNull List<Release> releasesList) {
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
        try {
            if (releaseJson.has(RELEASE_DATE) && releaseJson.has("name")) {
                String name = releaseJson.getString("name");
                String dateStr = releaseJson.getString(RELEASE_DATE);
                LocalDate date = LocalDate.parse(dateStr);
                return Optional.of(new Release(name, date));
            }
        } catch (DateTimeParseException e) {
            SeLogger.getInstance().getLogger().warning(() ->
                    String.format("Ignoring release with malformed date for project %s: %s", projName, e.getMessage()));
        } catch (Exception e) {
            SeLogger.getInstance().getLogger().warning(() ->
                    String.format("Ignoring invalid release entry for project %s: %s", projName, e.getMessage()));
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

    // --- helper per serializzazione ticket in JSON ---
    private JSONArray ticketsToJsonArray(List<Ticket> tickets) {
        JSONArray arr = new JSONArray();
        if (tickets == null) return arr;
        for (Ticket t : tickets) {
            arr.put(ticketToJson(t));
        }
        return arr;
    }

    private JSONObject ticketToJson(Ticket t) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("key", t.getTicketKey());
            obj.put("creationDate", t.getCreationDate() == null ? JSONObject.NULL : t.getCreationDate().toString());
            obj.put("resolutionDate", t.getResolutionDate() == null ? JSONObject.NULL : t.getResolutionDate().toString());

            Release ov = t.getOpeningVersion();
            Release fv = t.getFixedVersion();
            Release iv = t.getInjectedVersion();

            if (ov != null) {
                obj.put("openingVersion", Map.of("id", ov.getId(), "name", ov.getReleaseName(), "date", ov.getReleaseDate().toString()));
            } else {
                obj.put("openingVersion", JSONObject.NULL);
            }
            if (fv != null) {
                obj.put("fixedVersion", Map.of("id", fv.getId(), "name", fv.getReleaseName(), "date", fv.getReleaseDate().toString()));
            } else {
                obj.put("fixedVersion", JSONObject.NULL);
            }
            if (iv != null) {
                obj.put("injectedVersion", Map.of("id", iv.getId(), "name", iv.getReleaseName(), "date", iv.getReleaseDate().toString()));
            } else {
                obj.put("injectedVersion", JSONObject.NULL);
            }

            JSONArray affected = new JSONArray();
            List<Release> avs = t.getAffectedVersions();
            if (avs != null) {
                for (Release r : avs) {
                    affected.put(Map.of("id", r.getId(), "name", r.getReleaseName(), "date", r.getReleaseDate().toString()));
                }
            }
            obj.put("affectedVersions", affected);

        } catch (Exception e) {
            SeLogger.getInstance().getLogger().warning(() ->
                    String.format("Unable to convert ticket %s to JSON: %s", t.getTicketKey(), e.getMessage()));
        }
        return obj;
    }
}
