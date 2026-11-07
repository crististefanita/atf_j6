package ro.atf.holdem.arena;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Strict, versioned strategy rosters for seat-rotation tournament experiments. */
public record TournamentStrategyLineups(Path policyDefaults, Map<String, Path> equityPolicyFiles,
                                        Map<Integer, List<String>> lineups) {
    private static final Set<String> ALLOWED = Set.of("tag", "calling", "pressure", "loose", "bluff",
            "equity-baseline", "equity-stack-aware");

    public TournamentStrategyLineups {
        Objects.requireNonNull(policyDefaults, "policyDefaults");
        equityPolicyFiles = Map.copyOf(equityPolicyFiles);
        var copy = new TreeMap<Integer, List<String>>();
        lineups.forEach((seats, roster) -> copy.put(seats, List.copyOf(roster)));
        lineups = Collections.unmodifiableMap(copy);
    }

    public static TournamentStrategyLineups load(Path path) throws IOException {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(0);
        try (var input = Files.newBufferedReader(path)) {
            Object document = new Yaml(new SafeConstructor(options)).load(input);
            if (!(document instanceof Map<?, ?> root)
                    || !root.keySet().equals(Set.of("schemaVersion", "policyDefaults", "equityPolicyFiles", "lineups"))
                    || !Integer.valueOf(1).equals(root.get("schemaVersion"))
                    || !(root.get("policyDefaults") instanceof String defaults)
                    || !(root.get("equityPolicyFiles") instanceof Map<?, ?> rawFiles)
                    || !(root.get("lineups") instanceof Map<?, ?> rawLineups))
                throw new IllegalArgumentException("Expected versioned defaults, equity policy files, and lineups");

            var files = new TreeMap<String, Path>();
            for (var entry : rawFiles.entrySet()) {
                if (!(entry.getKey() instanceof String name) || !ALLOWED.contains(name)
                        || !(entry.getValue() instanceof String file))
                    throw new IllegalArgumentException("Invalid equity policy file mapping");
                files.put(name, Path.of(file));
            }
            if (!files.keySet().equals(Set.of("equity-baseline", "equity-stack-aware")))
                throw new IllegalArgumentException("Both equity variants must have explicit YAML files");

            var lineups = new TreeMap<Integer, List<String>>();
            for (var entry : rawLineups.entrySet()) {
                if (!(entry.getKey() instanceof Integer seats) || seats < 4 || seats > 6
                        || !(entry.getValue() instanceof List<?> rawRoster))
                    throw new IllegalArgumentException("Invalid seat-count lineup");
                var roster = new ArrayList<String>();
                for (Object raw : rawRoster) {
                    if (!(raw instanceof String strategy) || !ALLOWED.contains(strategy))
                        throw new IllegalArgumentException("Unknown strategy in lineup: " + raw);
                    roster.add(strategy);
                }
                if (roster.size() != seats || new HashSet<>(roster).size() != seats)
                    throw new IllegalArgumentException("Each seat needs one distinct strategy at " + seats + " seats");
                lineups.put(seats, List.copyOf(roster));
            }
            if (!lineups.keySet().equals(Set.of(4, 5, 6)))
                throw new IllegalArgumentException("Lineups must cover 4, 5, and 6 seats");
            return new TournamentStrategyLineups(Path.of(defaults), files, lineups);
        } catch (org.yaml.snakeyaml.error.YAMLException failure) {
            throw new IllegalArgumentException("Invalid strategy lineup YAML", failure);
        }
    }

    public List<String> forSeats(int seats) {
        List<String> roster = lineups.get(seats);
        if (roster == null) throw new IllegalArgumentException("No strategy lineup for " + seats + " seats");
        return roster;
    }
}
