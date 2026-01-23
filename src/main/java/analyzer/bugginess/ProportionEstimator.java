package analyzer.bugginess;

import analyzer.model.Release;
import analyzer.model.TicketInfo;
import util.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProportionEstimator {

    private static final int MIN_VALID_TICKETS = 5;
    private final Map<String, Release> releaseMap;
    private final ReleaseIndexMapper mapper;
    private final List<TicketInfo> validTicketsWithAV = new ArrayList<>();
    private final double coldStartP;

    public ProportionEstimator(List<Release> orderedReleases) {
        this.mapper = new ReleaseIndexMapper(orderedReleases); // mappa le release a un indice intero ordinato
        this.releaseMap = new HashMap<>(); // mappa nome --> oggetto Release
        for (Release r : orderedReleases) {
            releaseMap.put(r.getName(), r); // per stimare P se non ci sono ticket validi
        }
        this.coldStartP = ColdStartEstimator.computeColdStartP();
    }

    /*
    Viene chiamato su ogni ticket con almeno una AV per costruire il dataset di partenza da cui calcolare P
     */
    public void registerValidTicket(TicketInfo ticket) {

        // Salta ticket senza AV o FV
        if (ticket.getAffectedVersions().isEmpty()) {
            return;
        }
        if (ticket.getFixVersionName() == null) {
            return;
        }

        // Trova la prima AV valida e normalizzata
        String avName = ticket.getAffectedVersions().stream()
                .map(this::normalizeVersionName)
                .filter(name -> mapper.getIndex(name) != -1)
                .findFirst()
                .orElse(null);

        if (avName == null) {
            return;
        }

        // Ottiene gli indici di
        int fvIdx = mapper.getIndex(normalizeVersionName(ticket.getFixVersionName())); // fix version
        int ivIdx = mapper.getIndex(avName); // pone la prima AV come IV
        int ovIdx = findClosestReleaseBefore(ticket.getOpeningVersion());

        if (fvIdx == -1 || ivIdx == -1 || ovIdx == -1) {
            return;
        }

        // Se uno dei tre è invalido (-1), ignora il ticket
        ticket.setInjectedVersion(ticket.getOpeningVersion());
        validTicketsWithAV.add(ticket);
    }

    // Calcola P come media degli incrementi per ogni ticket con AV definita
    private double computeIncrementalP() {
        double sum = 0;
        int count = 0;

        // Per ogni ticket con AV definita
        for (TicketInfo t : validTicketsWithAV) {
            int iv = mapper.getIndex(t.getAffectedVersions().get(0)); // Prende la prima AV come IV
            int fv = mapper.getIndex(t.getFixVersionName());
            int ov = findClosestReleaseBefore(t.getOpeningVersion());

            if (fv == ov) {
                sum += fv - iv;
            } else {
                sum += (double) (fv - iv) / (fv - ov);
            }
            count++;
        }

        return count == 0 ? 1.0 : sum / count; // ritorna 1 se non ci sono ticket validi
    }

    // Usata per stimare la IV nei ticket che non hanno AV
    public String estimateIV(TicketInfo ticket) {
        if (Configuration.LABELING_DEBUG) Configuration.logger.info("Estimo IV per ticket " + ticket.getId());
        if (Configuration.LABELING_DEBUG) Configuration.logger.info("   → FV: " + ticket.getFixVersionName() + ", OV: " + ticket.getOpeningVersion());

        String normalizedFV = normalizeVersionName(ticket.getFixVersionName());
        int fvIndex = mapper.getIndex(normalizedFV); //  indice della fixVersion

        int ovIndex = findClosestReleaseBefore(ticket.getOpeningVersion()); //  indice della release più vicina alla openingDate
        if (ovIndex == -1) return null;

        // se hai almeno 5 ticket -->  computeIncrementalP() altrimenti cold start
        double p = (validTicketsWithAV.size() >= MIN_VALID_TICKETS) ? computeIncrementalP() : coldStartP;

        int ivIndex;
        if (fvIndex == ovIndex) {
            ivIndex = (int) Math.round(fvIndex - p);
        } else {
            ivIndex = (int) Math.round(fvIndex - ((fvIndex - ovIndex) * p));
        }

        ivIndex = Math.max(0, ivIndex);
        String estIV = mapper.getReleaseName(ivIndex); // nome della versione stimata

        ticket.setInjectedVersionName(mapper.getReleaseName(ivIndex));

        if (Configuration.LABELING_DEBUG) {
            if (fvIndex == -1) {
                Configuration.logger.info("Proportion SKIPPED: FixVersion non trovata → " + ticket.getFixVersionName());
            }
        }

        return estIV;
    }

    public String normalizeVersionName(String name) {
        if (name == null) return null;
        if (name.matches("\\d+\\.\\d+")) {
            return name + ".0";
        }
        return name;
    }

    private int findClosestReleaseBefore(LocalDate targetDate) {
        int bestIdx = -1;
        LocalDate bestDate = null;

        for (int i = 0; i < mapper.size(); i++) {
            String relName = mapper.getReleaseName(i);
            Release r = releaseMap.get(relName);
            if (r == null || r.getReleaseDate().isAfter(targetDate)) continue;

            if (bestDate == null || r.getReleaseDate().isAfter(bestDate)) {
                bestDate = r.getReleaseDate();
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    public ReleaseIndexMapper getMapper() {
        return mapper;
    }

}
