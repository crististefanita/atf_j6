package ro.atf.holdem.android;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class AchievementCatalog {
    enum Tier { EASY, MEDIUM, HARD }

    // Rules and hand reading precede betting decisions and specialist formats.
    private static final List<String> TRACK = List.of("pozitie", "showdown", "marime", "blinduri", "allin",
            "poturi", "egalitate", "potodds", "outs", "preflop", "textura", "range", "valoare", "disciplina",
            "bluff", "shortstack", "heads-up", "cash", "turneu", "review");

    record Entry(String id, Tier tier, String family, String context, int required, int size, int lots,
                 String prerequisite, int contextIndex, int stage) {
        boolean usesPotOdds() { return family.equals("potodds"); }
        String trackKey() { return tier == Tier.EASY ? family + "-" + contextIndex + "-easy" : id; }
    }

    private final List<Entry> ordered;
    private final Map<String, Entry> byId = new HashMap<>();

    AchievementCatalog(Reader source) throws IOException {
        var entries = new ArrayList<Entry>();
        var input = new BufferedReader(source);
        if (!"id,nivel,familie,context,corecte_per_lot,unice_per_lot,loturi,prerechizit".equals(input.readLine()))
            throw new IOException("Invalid achievement catalog header");
        for (String line; (line = input.readLine()) != null; ) {
            try {
                String[] fields = line.split(",", -1);
                if (fields.length != 8) throw new IllegalArgumentException();
                Tier tier = switch (fields[1]) {
                    case "usor" -> Tier.EASY;
                    case "mediu" -> Tier.MEDIUM;
                    case "greu" -> Tier.HARD;
                    default -> throw new IllegalArgumentException();
                };
                String family = fields[2];
                String[] key = fields[0].substring(family.length() + 1).split("-");
                var entry = new Entry(fields[0], tier, family, fields[3], Integer.parseInt(fields[4]),
                        Integer.parseInt(fields[5]), Integer.parseInt(fields[6]), fields[7],
                        Integer.parseInt(key[0]), Integer.parseInt(key[1].substring(1)));
                if (!TRACK.contains(family) || entry.required < 1 || entry.size < entry.required || entry.lots < 1
                        || byId.putIfAbsent(entry.id, entry) != null) throw new IllegalArgumentException();
                entries.add(entry);
            } catch (RuntimeException invalid) {
                throw new IOException("Invalid achievement catalog entry", invalid);
            }
        }
        var sequence = java.util.Comparator.comparingInt((Entry e) -> TRACK.indexOf(e.family))
                .thenComparingInt(Entry::contextIndex).thenComparing(Entry::tier).thenComparingInt(Entry::stage);
        entries.sort(sequence);
        for (Entry entry : entries) {
            if (entry.prerequisite.isEmpty()) continue;
            Entry previous = byId.get(entry.prerequisite);
            if (previous == null || !previous.family.equals(entry.family) || previous.contextIndex != entry.contextIndex
                    || sequence.compare(previous, entry) >= 0) throw new IOException("Invalid prerequisite for " + entry.id);
        }
        ordered = java.util.Collections.unmodifiableList(entries);
    }

    List<Entry> entries(Tier tier) {
        return ordered.stream().filter(entry -> entry.tier == tier).collect(java.util.stream.Collectors.toList());
    }

    int familyIndex(Entry entry) { return TRACK.indexOf(entry.family); }

    Entry find(String id) { return byId.get(id); }
}
