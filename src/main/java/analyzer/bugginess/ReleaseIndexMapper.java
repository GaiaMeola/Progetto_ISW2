package analyzer.bugginess;

import analyzer.model.Release;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReleaseIndexMapper {

    private final Map<String, Integer> releaseNameToIndex = new HashMap<>();
    private final Map<Integer, String> indexToReleaseName = new HashMap<>();

    // Associa a ogni release (in ordine temporale) un indice crescente, a partire da 0
    public ReleaseIndexMapper(List<Release> orderedReleases) {
        for (int i = 0; i < orderedReleases.size(); i++) {
            String rawName = orderedReleases.get(i).getName();
            String name = normalizeVersionName(rawName);
            releaseNameToIndex.put(name, i);
            indexToReleaseName.put(i, name);

        }
    }

    // Restituisce l’indice corrispondente a un nome release
    public int getIndex(String releaseName) {
        return releaseNameToIndex.getOrDefault(releaseName, -1);
    }

     // Restituisce il nome della release corrispondente a un indice
    public String getReleaseName(int index) {
        return indexToReleaseName.getOrDefault(index, null);
    }

    public int size() {
        return releaseNameToIndex.size();
    }

    private String normalizeVersionName(String name) {
        if (name == null) return null;
        if (name.matches("\\d+\\.\\d+")) {
            return name + ".0";
        }
        return name;
    }

}
