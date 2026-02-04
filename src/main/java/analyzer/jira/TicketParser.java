package analyzer.jira;

import analyzer.csv.CsvTicketDebugWriter;
import analyzer.exception.JiraParsingException;
import analyzer.exception.JiraReleaseException;
import analyzer.exception.JsonDownloadException;
import analyzer.model.Release;
import analyzer.model.TicketInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import util.Configuration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

public class TicketParser {

    private static final String RELEASE_DATE_STRING = "releaseDate";
    private static final String VERSIONS_STRING = "versions";
    private static final String CORRECT_NAME = "^\\d+\\.\\d+\\.\\d+$";

    //  Scarica tutti i ticket bug risolti e chiusi dal progetto
    public static Map<String, TicketInfo> parseTicketsFromJira() throws JiraParsingException {
        try {
            Map<String, TicketInfo> ticketMap = new HashMap<>();

            int startAt = 0;
            int maxResults = 1000;
            int total = 1;

            while (startAt < total) {

                // Chiamata http effettiva
                String jql = "project=" + Configuration.getProjectName() + " AND issuetype=Bug AND status in (Resolved, Closed) AND resolution=Fixed";
                String url = String.format("https://issues.apache.org/jira/rest/api/2/search?jql=%s&startAt=%d&maxResults=%d",
                        jql.replace(" ", "%20"), startAt, maxResults);
                JSONObject response = readJsonFromUrl(url);
                total = response.getInt("total");
                JSONArray issues = response.getJSONArray("issues");

                // Ogni oggetto issue viene convertito in un TicketInfo
                for (int i = 0; i < issues.length(); i++) {
                    JSONObject issue = issues.getJSONObject(i);

                    TicketInfo ticket = parseSingleTicket(issue);
                    if (ticket == null) {
                        continue;
                    }

                    ticketMap.put(ticket.getId(), ticket);
                }

                startAt += maxResults;
            }

            return ticketMap;

        } catch (Exception e) {
            throw new JiraParsingException("Errore durante il parsing dei ticket da JIRA", e);
        }
    }


    // Usa dati json per costruire ticket info
    private static TicketInfo parseSingleTicket(JSONObject issue) {

        // Estrazione campi ticket dall'oggetto json
        String key = issue.getString("key");
        JSONObject fields = issue.getJSONObject("fields");
        LocalDate createdDate = LocalDate.parse(fields.getString("created").substring(0, 10));
        JSONArray fixVersions = fields.getJSONArray("fixVersions");
        if (fixVersions.isEmpty()) return null; // Se manca fixVersion, viene ignorato

        // Costruzione TicketInfo
        TicketInfo ticket = new TicketInfo(key);
        ticket.setOpeningVersion(createdDate);

        // Parsing delle fix version
        String earliestFVName = parseFixVersions(ticket, fixVersions);
        if (earliestFVName == null) return null;

        //  Parsing delle affected version
        JSONArray affectedVersions = fields.optJSONArray(VERSIONS_STRING);
        parseAffectedVersions(ticket, affectedVersions);

        return ticket;
    }

    // Individuare fix version (FV) con data piu vecchia tra quelle associate al ticket, e salvarla nel TicketInfo.
    private static String parseFixVersions(TicketInfo ticket, JSONArray fixVersions) {
        LocalDate earliestFVDate = null;
        String earliestFVName = null;

        // Loop su ogni fixVersion
        for (int j = 0; j < fixVersions.length(); j++) {
            JSONObject fv = fixVersions.getJSONObject(j);
            // Solo le versioni con data di rilascio definita e nome versione nel formato X.Y.Z
            if (fv.has(RELEASE_DATE_STRING) && fv.has("name")) {
                String fvName = fv.getString("name");
                if (!fvName.matches(CORRECT_NAME)) {
                    continue;
                }

                //  Converte la data da stringa a LocalDate, e la registra nel ticket
                LocalDate fvDate = LocalDate.parse(fv.getString(RELEASE_DATE_STRING));
                ticket.addFixVersion(fvName, fvDate);

                if (earliestFVDate == null || fvDate.isBefore(earliestFVDate)) {
                    earliestFVDate = fvDate;
                    earliestFVName = fvName;
                }
            }
        }

        // Selezione della piu vecchia
        if (earliestFVDate != null) {
            ticket.setFixVersion(earliestFVDate);
            ticket.setFixVersionName(earliestFVName);
        }

        return earliestFVName;
    }

    // Registrare affected versions (AV) in cui il bug era presente
    private static void parseAffectedVersions(TicketInfo ticket, JSONArray affectedVersions) {
        if (affectedVersions == null) return; // se assenti, salta tutto

        // Loop su ogni AV
        for (int j = 0; j < affectedVersions.length(); j++) {
            JSONObject av = affectedVersions.getJSONObject(j);
            if (av.has("name")) {
                String avName = av.getString("name").trim();
                if (!avName.matches(CORRECT_NAME)) { // verifica che abbia name valido (X.Y.Z)
                    continue;
                }
                ticket.addAffectedVersion(avName);

            }
        }
    }

    //  Ritorna il corpo della risposta della chiamata http a JIRA convertito in un oggetto JSONObject
    private static JSONObject readJsonFromUrl(String url) throws JsonDownloadException {
        try (InputStream is = new URL(url).openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }

            return new JSONObject(sb.toString());

        } catch (RuntimeException | IOException e) {
            throw new JsonDownloadException("Errore nel download o parsing del JSON da URL: " + url, e);
        }
    }

    // Variante parametrizzata di parseTicketsFromJira in base al progetto
    public static Map<String, TicketInfo> parseTicketsFromProject(String projectKey) throws JiraParsingException {
        Map<String, TicketInfo> ticketMap = new HashMap<>();
        int startAt = 0;
        int maxResults = 1000;
        int total = 1;

        try {
            while (startAt < total) {
                String jql = "project=" + projectKey + " AND issuetype=Bug AND status in (Resolved, Closed) AND resolution=Fixed";
                String url = String.format("https://issues.apache.org/jira/rest/api/2/search?jql=%s&startAt=%d&maxResults=%d",
                        jql.replace(" ", "%20"), startAt, maxResults);
                JSONObject response = readJsonFromUrl(url);
                total = response.getInt("total");
                JSONArray issues = response.getJSONArray("issues");

                for (int i = 0; i < issues.length(); i++) {
                    TicketInfo ticket = parseSingleTicket(issues.getJSONObject(i));
                    if (ticket != null) {
                        ticketMap.put(ticket.getId(), ticket);
                    }
                }

                startAt += maxResults;
            }
            return ticketMap;

        } catch (Exception e) {
            throw new JiraParsingException("Errore durante il parsing dei ticket da JIRA", e);
        }
    }

    // Recupera l’elenco di release di un progetto JIRA,
    public static List<Release> getReleasesFromProject(String projectKey) throws JiraReleaseException, JsonDownloadException {

        try{List<Release> releases = new ArrayList<>();

            String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectKey;
            JSONObject json = readJsonFromUrl(url);
            JSONArray versions = json.getJSONArray(VERSIONS_STRING);

            for (int i = 0; i < versions.length(); i++) {
                JSONObject version = versions.getJSONObject(i);
                if (version.has(RELEASE_DATE_STRING) && version.has("released") && version.getBoolean("released")) {
                    Release r = new Release();
                    String name = version.optString("name", "unknown");
                    if (!name.matches(CORRECT_NAME)) {
                        continue;
                    }
                    r.setName(name);
                    r.setId(version.optString("id", "0"));
                    r.setReleaseDate(LocalDate.parse(version.getString(RELEASE_DATE_STRING)));
                    r.setReleased(true);
                    releases.add(r);
                }
            }

            // Ordina le release per data
            releases.sort(Comparator.comparing(Release::getReleaseDate));
            return releases;} catch (RuntimeException e) {
            throw new JiraReleaseException("Errore recupero release da JIRA", e);
        }
    }


    public static void main(String[] args) throws Exception {
        Map<String, TicketInfo> tickets = parseTicketsFromJira();
        CsvTicketDebugWriter.writeTicketCsv(Configuration.getDebugTicketPath(), tickets);
    }
}

