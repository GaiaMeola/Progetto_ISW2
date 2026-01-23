package analyzer.jira;

import analyzer.csv.CsvDebugWriter;
import analyzer.model.Release;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Level;

import util.Configuration;

public class GetReleaseInfo {

    /*
    Questa classe di occupa di gestire il recupero delle release del progetto da JIRA
     */

    private static final ArrayList<LocalDateTime> releases = new ArrayList<>(); // lista date
    private static final HashMap<LocalDateTime, String> releaseNames = new HashMap<>(); // mappa date --> nome release
    private static final HashMap<LocalDateTime, String> releaseIDs = new HashMap<>(); // mappa date --> id release
    private static final String RELEASE_DATE_STRING = "releaseDate";
    private static final String RELEASE_STRING = "released" ;

    private GetReleaseInfo(){
        // Prevent instantiation
    }

    // Ottieni la lista di release (primo 33%) del progetto
    public static List<Release> getDatasetReleases() throws IOException, JSONException {
        releases.clear();
        releaseNames.clear();
        releaseIDs.clear();

        if (Configuration.BASIC_DEBUG) Configuration.logger.info("Recupero release da JIRA per il progetto " + Configuration.getProjectName());

        // Richiesta HTTP per ottenere il json delle release del progetto
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + Configuration.getProjectName();
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        /*
        Filtra versioni valide:
        - versioni rilascaiate (released == true)
        - con data definita (releaseDate)
        - release name con formato X.Y.Z (es. 1.2.3)
         */
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String name = version.optString("name", "unknown");
                if (!name.matches("^\\d+\\.\\d+\\.\\d+$")) continue;
                String dateStr = version.getString(RELEASE_DATE_STRING);
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

        // Ordina le release in base alle date
        releases.sort(Comparator.naturalOrder());

        // Prendi il primo 33%
        int cutoff = (int) Math.ceil(releases.size() * 0.33);
        List<LocalDateTime> selected = releases.subList(0, cutoff);

        if (Configuration.BASIC_DEBUG && Configuration.logger.isLoggable(Level.INFO)) {
            Configuration.logger.info("Numero totale release: " + releases.size());
            Configuration.logger.info("Release selezionate (33%): " + selected.size());
        }

        // Costruisci oggetti Release
        List<Release> allReleases = new ArrayList<>();
        List<Release> selectedReleases = new ArrayList<>();

        for (LocalDateTime dt : releases) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            allReleases.add(r);

            if (selected.contains(dt)) {
                selectedReleases.add(r);
            }
        }

        // Scrivi su CSV tramite CsvDebugWriter per stampare tutte le release e quelle selezionate
        CsvDebugWriter.writeReleaseCsv(
                Configuration.getDebugVersionInfoPath(),
                allReleases,
                selectedReleases
        );

        // Genera output List<Release>
        List<Release> output = new ArrayList<>();
        for (LocalDateTime dt : selected) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            output.add(r);
        }

        return output;
    }

    // Ottieni la lista di release (tutte) del progetto
    public static List<Release> getAllReleases() throws IOException, JSONException {
        releases.clear();
        releaseNames.clear();
        releaseIDs.clear();

        if (Configuration.LABELING_DEBUG)
            Configuration.logger.info("Recupero TUTTE le release da JIRA per il progetto " + Configuration.getProjectName());

        // Richiesta HTTP
        String url = "https://issues.apache.org/jira/rest/api/2/project/" + Configuration.getProjectName();
        JSONObject json = readJsonFromUrl(url);
        JSONArray versions = json.getJSONArray("versions");

        // Filtraggio valide
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.getJSONObject(i);
            if (version.has(RELEASE_DATE_STRING) && version.has(RELEASE_STRING) && version.getBoolean(RELEASE_STRING)) {
                String name = version.optString("name", "unknown");

                if (!name.matches("^\\d+\\.\\d+\\.\\d+$")) continue;

                String dateStr = version.getString(RELEASE_DATE_STRING);
                String id = version.optString("id", "0");
                addRelease(dateStr, name, id);
            }
        }

        // Ordina
        releases.sort(Comparator.naturalOrder());

        // Crea lista
        List<Release> all = new ArrayList<>();
        for (LocalDateTime dt : releases) {
            Release r = new Release();
            r.setName(releaseNames.get(dt));
            r.setId(releaseIDs.get(dt));
            r.setReleaseDate(dt.toLocalDate());
            r.setReleased(true);
            all.add(r);
        }

        return all;
    }


    // Aggiunge una release e i dati associati alle strutture definite all'inizio della classe
    private static void addRelease(String strDate, String name, String id) {
        LocalDate date = LocalDate.parse(strDate);
        LocalDateTime dateTime = date.atStartOfDay();
        if (!releases.contains(dateTime)) {
            releases.add(dateTime);
        }
        releaseNames.put(dateTime, name);
        releaseIDs.put(dateTime, id);
    }

    // Effettua richiesta HTTP e converte in JSONObject il risultato ottenuto

// ... resto della classe ...

    private static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {

        InputStream is = URI.create(url).toURL().openStream();
        try (BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            return new JSONObject(sb.toString());
        }
    }

}

