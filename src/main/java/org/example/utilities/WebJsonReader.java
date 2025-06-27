package org.example.utilities;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public class WebJsonReader {

    private WebJsonReader() {
        // Prevent instantiation
    }

    /**
     * Reads all characters from the given Reader into a String.
     *
     * @param rd the Reader to read from
     * @return the resulting String
     * @throws IOException if an I/O error occurs
     */
    private static @NotNull String readAll(@NotNull Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    /**
     * Reads and parses a JSON object from the specified URL.
     *
     * @param url the URL to read from
     * @return a JSONObject representing the data from the URL
     * @throws IOException if an I/O error occurs
     * @throws JSONException if the JSON is malformed
     * @throws URISyntaxException if the URL syntax is invalid
     */
    public static @NotNull JSONObject readJsonFromUrl(String url)
            throws IOException, JSONException, URISyntaxException {
        try (InputStream is = new URI(url).toURL().openStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String jsonText = readAll(rd);
            return new JSONObject(jsonText);
        }
    }
}
