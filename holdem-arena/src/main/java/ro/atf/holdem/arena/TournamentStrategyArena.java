package ro.atf.holdem.arena;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import ro.atf.holdem.ai.*;
import ro.atf.holdem.core.*;

/** Runs distinct per-seat strategy rosters and rotates every role through every physical seat. */
public final class TournamentStrategyArena {
    private static final long SECONDS_PER_HAND = 12;
    private static final int HAND_LIMIT = 500;

    private TournamentStrategyArena() {}

    public static List<String> rotate(List<String> roster, int offset) {
        Objects.requireNonNull(roster, "roster");
        if (roster.isEmpty() || new HashSet<>(roster).size() != roster.size())
            throw new IllegalArgumentException("A lineup needs unique strategy IDs");
        var rotated = new ArrayList<String>(roster.size());
        for (int seat = 0; seat < roster.size(); seat++)
            rotated.add(roster.get(Math.floorMod(seat - offset, roster.size())));
        return List.copyOf(rotated);
    }

    static TournamentArena.Result play(Tournament.Rules rules, long seed, long buttonOffset, int policyOffset,
                                       List<String> baseRoster, TournamentPolicyConfig defaults,
                                       Map<String, TournamentPolicyConfig> variants, int shortStackShoveMaxBigBlinds,
                                       PrintWriter trace) {
        if (baseRoster.size() != rules.seats()) throw new IllegalArgumentException("Lineup/seat-count mismatch");
        List<String> roster = rotate(baseRoster, policyOffset);
        var policies = new ArrayList<DecisionPolicy>(rules.seats());
        for (String strategy : roster) policies.add(policy(strategy, defaults, variants, shortStackShoveMaxBigBlinds));

        var buffered = new StringWriter();
        var result = TournamentArena.play(rules, seed, SECONDS_PER_HAND, HAND_LIMIT,
                policies, buttonOffset, new PrintWriter(buffered));
        List<String> lines = buffered.toString().lines().toList();
        if (lines.isEmpty() || !lines.getFirst().startsWith("TOURNAMENT "))
            throw new IllegalStateException("Tournament trace omitted its identity header");
        trace.println(lines.getFirst() + " policyOffset=" + Math.floorMod(policyOffset, rules.seats()));
        for (int seat = 0; seat < roster.size(); seat++)
            trace.printf("STRATEGY seat=%d id=%s implementation=%s%n", seat, roster.get(seat),
                    policies.get(seat).getClass().getSimpleName());
        for (int i = 1; i < lines.size(); i++) trace.println(lines.get(i));
        trace.flush();
        if (trace.checkError()) throw new IllegalStateException("Strategy tournament trace write failed");
        return result;
    }

    private static DecisionPolicy policy(String strategy, TournamentPolicyConfig defaults,
                                         Map<String, TournamentPolicyConfig> variants, int shoveThreshold) {
        return switch (strategy) {
            case "tag" -> new TightAggressiveBaseline();
            case "calling" -> new CallingStationPolicy();
            case "pressure" -> new PressurePolicy();
            case "loose" -> new LooseAggressivePolicy(defaults.looseOpenFrequencyPercent(),
                    defaults.looseRaiseFrequencyPercent(), defaults.looseMaxCallPotPercent(),
                    defaults.looseLatePositionAdjustmentPercent());
            case "bluff" -> new DeterministicBluffPolicy(defaults.bluffFrequencyPercent(),
                    defaults.latePositionWindow());
            case "short-stack-shove" -> new ShortStackShovePolicy(shoveThreshold);
            case "equity-baseline", "equity-stack-aware", "equity-history-control", "equity-history-aware" ->
                    equity(variants.get(strategy));
            default -> throw new IllegalArgumentException("Unknown strategy ID " + strategy);
        };
    }

    private static ContextualEquityPolicy equity(TournamentPolicyConfig config) {
        if (config == null) throw new IllegalArgumentException("Equity strategy has no YAML configuration");
        return new ContextualEquityPolicy(config.equitySamples(), config.cashCallMarginPercent(),
                config.tournamentCallMarginPercent(), config.equityRaiseEdgePercent(),
                config.valueBetThresholdPercent(), ContextualEquityPolicy.GameMode.TOURNAMENT,
                config.equityContextWeights());
    }

    /** CLI: profile.yaml lineup.yaml trace.txt count firstSeed policyOffset seatCount [buttonOffset]. */
    public static void main(String[] args) throws IOException {
        if (args.length < 7 || args.length > 8)
            throw new IllegalArgumentException("Expected profile.yaml lineup.yaml trace.txt count firstSeed policyOffset seatCount [buttonOffset]");
        Path profilePath = Path.of(args[0]), lineupPath = Path.of(args[1]), tracePath = Path.of(args[2]);
        int count = Integer.parseInt(args[3]);
        long firstSeed = Long.parseLong(args[4]);
        int policyOffset = Integer.parseInt(args[5]);
        int seatCount = Integer.parseInt(args[6]);
        long buttonOffset = args.length == 8 ? Long.parseLong(args[7]) : 0;
        if (count < 1 || seatCount < 4 || seatCount > 6) throw new IllegalArgumentException("Invalid run size");
        Math.addExact(firstSeed, count - 1L);

        Map<Integer, Tournament.Rules> allRules = TournamentProfiles.load(profilePath);
        Tournament.Rules rules = allRules.get(seatCount);
        if (rules == null) throw new IllegalArgumentException("Profile omits " + seatCount + " seats");
        var lineup = TournamentStrategyLineups.load(lineupPath);
        List<String> baseRoster = lineup.forSeats(seatCount);
        var defaults = TournamentPolicyConfig.load(lineup.policyDefaults());
        var variants = new HashMap<String, TournamentPolicyConfig>();
        lineup.equityPolicyFiles().forEach((name, path) -> {
            try { variants.put(name, TournamentPolicyConfig.load(path)); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        });

        try (var trace = new PrintWriter(Files.newBufferedWriter(tracePath, StandardOpenOption.CREATE_NEW))) {
            int totalHands = 0;
            for (int trial = 0; trial < count; trial++) {
                long seed = firstSeed + trial;
                totalHands += play(rules, seed, buttonOffset, policyOffset, baseRoster,
                        defaults, variants, lineup.shortStackShoveMaxBigBlinds(), trace).hands();
            }
            if (trace.checkError()) throw new IOException("Strategy tournament trace write failed");
            System.out.printf("%d seats: %d tournaments, policyOffset=%d, buttonOffset=%d, %d hands%n",
                    seatCount, count, policyOffset, buttonOffset, totalHands);
        } catch (UncheckedIOException failure) {
            throw failure.getCause();
        }
    }
}
