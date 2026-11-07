package ro.atf.holdem.arena;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import ro.atf.holdem.core.*;

public final class TournamentProfiles {
    private TournamentProfiles() {}

    public static Map<Integer, Tournament.Rules> load(Path path) throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (var input = Files.newBufferedReader(path)) {
            var root = mapping(new Yaml(new SafeConstructor(options)).load(input),
                    Set.of("schemaVersion", "levelSeconds", "startingStack", "seatCounts", "levels"));
            if (integer(root.get("schemaVersion")) != 1) throw new IllegalArgumentException("Unknown schemaVersion");
            int seconds = integer(root.get("levelSeconds"));
            int stack = integer(root.get("startingStack"));
            var levels = sequence(root.get("levels")).stream().map(raw -> {
                var level = mapping(raw, Set.of("smallBlind", "bigBlind", "minimumOpen"));
                return new BlindLevel(integer(level.get("smallBlind")), integer(level.get("bigBlind")),
                        integer(level.get("minimumOpen")));
            }).toList();
            var profiles = new TreeMap<Integer, Tournament.Rules>();
            for (Object raw : sequence(root.get("seatCounts"))) {
                int seats = integer(raw);
                if (profiles.putIfAbsent(seats, new Tournament.Rules(seats, stack, seconds, levels)) != null)
                    throw new IllegalArgumentException("Duplicate seat count");
            }
            if (profiles.isEmpty()) throw new IllegalArgumentException("No tournament profiles");
            return Collections.unmodifiableMap(profiles);
        } catch (org.yaml.snakeyaml.error.YAMLException failure) {
            throw new IllegalArgumentException("Invalid tournament YAML", failure);
        }
    }

    private static Map<?, ?> mapping(Object raw, Set<String> keys) {
        if (raw instanceof Map<?, ?> map && map.keySet().equals(keys)) return map;
        throw new IllegalArgumentException("Expected keys " + keys);
    }

    private static List<?> sequence(Object raw) {
        if (raw instanceof List<?> list) return list;
        throw new IllegalArgumentException("Expected YAML sequence");
    }

    private static int integer(Object raw) {
        if (raw instanceof Integer n) return n;
        throw new IllegalArgumentException("Expected YAML integer");
    }
}
