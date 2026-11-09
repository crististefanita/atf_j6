package ro.atf.holdem.arena;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import ro.atf.holdem.ai.*;
import ro.atf.holdem.core.*;

public final class TournamentArena {
    public record Result(int winner, int hands, List<Integer> stacks) {}

    private TournamentArena() {}

    public static Result play(Tournament.Rules rules, long seed, long secondsPerHand, int handLimit,
                              List<DecisionPolicy> policies, PrintWriter trace) {
        return play(rules, seed, secondsPerHand, handLimit, policies, 0, trace);
    }

    public static Result play(Tournament.Rules rules, long seed, long secondsPerHand, int handLimit,
                              List<DecisionPolicy> policies, long buttonOffset, PrintWriter trace) {
        if (secondsPerHand < 1 || handLimit < 1 || policies.size() != rules.seats())
            throw new IllegalArgumentException("Invalid arena parameters");
        policies = List.copyOf(policies);
        int firstButton = Math.floorMod(Math.floorMod(seed, rules.seats())
                + Math.floorMod(buttonOffset, rules.seats()), rules.seats());
        var tournament = new Tournament(rules, firstButton);
        var random = new SplittableRandom(seed);
        var shoveHistory = new PublicShoveHistory(rules.seats());
        trace.printf("TOURNAMENT seed=%d seats=%d buttonOffset=%d firstButton=%d secondsPerHand=%d rules=%s%n",
                seed, rules.seats(), buttonOffset, firstButton, secondsPerHand, rules);
        for (int i = 0; i < handLimit; i++) {
            long deal = random.nextLong();
            var round = tournament.startHand(Math.multiplyExact(i, secondsPerHand), deal);
            var hand = round.hand();
            trace.printf("HAND %d elapsed=%d eliminated=%d level=%d blinds=%s button=%d dealSeed=%d%n",
                    round.number(), round.elapsedSeconds(), round.eliminated(), round.levelIndex(),
                    hand.level(), hand.button(), deal);
            int published = publishEvents(hand, trace, 0);
            while (hand.actor() >= 0) {
                int seat = hand.actor();
                HoldemObservation observation = hand.observeActor(shoveHistory.snapshot());
                Decision decision = policies.get(seat).decide(observation);
                hand.act(seat, decision.action(), decision.amount());
                shoveHistory.recordAccepted(observation, decision.action(), decision.amount());
                trace.printf("DECISION seat=%d policy=%s action=%s amount=%d reason=%s positionFromButton=%d street=%s pot=%d toCall=%d heroStack=%d activeOpponents=%d bigBlind=%d visibleSeatIds=%s visibleStacks=%s visibleContributions=%s foldedSeatIds=%s priorAllInActors=%s priorAllInEvents=%s publicOpponentTendencies=%s legalActions=%s%n",
                        seat, decision.policyVersion(), decision.action(), decision.amount(), decision.explanation(),
                        observation.positionFromButton(), observation.street(), observation.pot(), observation.toCall(),
                        observation.heroStack(), observation.activeOpponentCount(), observation.bigBlind(),
                        compactSeatIds(observation), compact(observation.visibleStacks()),
                        compact(observation.visibleContributions()), foldedSeatIds(observation),
                        priorAllInActors(observation),
                        priorAllInEvents(observation),
                        publicTendencies(observation),
                        observation.legalActions().stream().map(action -> action.type() + ":" + action.amount()
                                + ":" + action.minAmount() + ":" + action.maxAmount())
                                .collect(java.util.stream.Collectors.joining(",")));
                published = publishEvents(hand, trace, published);
            }
            shoveHistory.settleHand();
            var stacks = tournament.stacks();
            trace.printf("STACKS %s%n", stacks);
            if (stacks.stream().filter(n -> n > 0).count() == 1) {
                int winner = stacks.indexOf(rules.startingStack() * rules.seats());
                if (winner < 0) throw new IllegalStateException("Chip conservation violated");
                trace.printf("COMPLETE winner=%d hands=%d%n", winner, round.number());
                return new Result(winner, round.number(), stacks);
            }
        }
        throw new IllegalStateException("Tournament did not finish within the hand limit");
    }

    private static int publishEvents(HoldemHand hand, PrintWriter trace, int from) {
        var events = hand.history();
        events.subList(from, events.size()).forEach(trace::println);
        return events.size();
    }

    private static String compactSeatIds(HoldemObservation observation) {
        return observation.publicActionHistory().stream().filter(event -> event.startsWith("SEATS "))
                .findFirst().map(event -> event.substring("SEATS ".length()).replace(" ", ""))
                .orElseThrow(() -> new IllegalStateException("Public seat map is missing from hand history"));
    }

    private static String compact(List<Integer> values) {
        return values.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }


    private static String publicTendencies(HoldemObservation observation) {
        return observation.publicOpponentTendencies().stream()
                .map(value -> value.physicalSeat() + ":" + value.shoveOpportunities() + ":" + value.allInRaises())
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String foldedSeatIds(HoldemObservation observation) {
        var seats = observation.publicActionHistory().stream()
                .filter(event -> event.matches("(?:PREFLOP|FLOP|TURN|RIVER) \\d+ FOLD \\d+"))
                .map(event -> event.split(" ")[1]).distinct().toList();
        return "[" + String.join(",", seats) + "]";
    }

    private static String priorAllInActors(HoldemObservation observation) {
        var actors = observation.publicActionHistory().stream()
                .filter(event -> event.matches("(?:PREFLOP|FLOP|TURN|RIVER) \\d+ ALL_IN \\d+"))
                .map(event -> event.split(" ")[1]).distinct().toList();
        return "[" + String.join(",", actors) + "]";
    }

    private static String priorAllInEvents(HoldemObservation observation) {
        var streetPaid = new HashMap<Integer, Integer>();
        var events = new ArrayList<String>();
        int price = observation.bigBlind();
        for (String event : observation.publicActionHistory()) {
            if (event.startsWith("SB ") || event.startsWith("BB ")) {
                String[] parts = event.split(" ");
                streetPaid.put(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                continue;
            }
            if (event.matches("(?:FLOP|TURN|RIVER) \\[.*\\]")) {
                streetPaid.clear();
                price = 0;
                continue;
            }
            String[] parts = event.split(" ");
            if (parts.length != 4 || !parts[0].matches("PREFLOP|FLOP|TURN|RIVER")) continue;
            int seat = Integer.parseInt(parts[1]);
            int amount = Integer.parseInt(parts[3]);
            int after = streetPaid.getOrDefault(seat, 0) + amount;
            if (parts[2].equals("ALL_IN"))
                events.add(seat + ":" + (after > price ? "raise" : "call") + ":" + amount);
            streetPaid.put(seat, after);
            price = Math.max(price, after);
        }
        return "[" + String.join(",", events) + "]";
    }

    static DecisionPolicy policyForSeat(int seat, long seed, String cohort, TournamentPolicyConfig config) {
        if (!Set.of("legacy", "legacy-loose", "legacy-pot", "legacy-equity", "equity", "mixed", "bluff", "calling", "loose",
                "sized", "pressure", "control").contains(cohort))
            throw new IllegalArgumentException("Unknown policy cohort: " + cohort);
        boolean candidateSlot = Math.floorMod(seat + Math.floorMod(seed, 4), 4) == 3;
        if (cohort.equals("legacy") || cohort.startsWith("legacy-") && !candidateSlot)
            return Math.floorMod(seat + Math.floorMod(seed, 2), 2) == 0
                    ? new TightAggressiveBaseline() : new CallingStationPolicy();
        if (cohort.equals("legacy-loose"))
            return new LooseAggressivePolicy(config.looseOpenFrequencyPercent(), config.looseRaiseFrequencyPercent(),
                    config.looseMaxCallPotPercent(), config.looseLatePositionAdjustmentPercent());
        if (cohort.equals("legacy-pot"))
            return new PotSizedPolicy(new LooseAggressivePolicy(config.looseOpenFrequencyPercent(),
                    config.looseRaiseFrequencyPercent(), config.looseMaxCallPotPercent(),
                    config.looseLatePositionAdjustmentPercent()), config.looseBetPotPercent());
        return switch ((seat + Math.floorMod(seed, 4)) % 4) {
            case 0 -> new TightAggressiveBaseline();
            case 1 -> new CallingStationPolicy();
            case 2 -> new PressurePolicy();
            default -> switch (cohort) {
                case "calling" -> new CallingStationPolicy();
                case "loose" -> new LooseAggressivePolicy(config.looseOpenFrequencyPercent(),
                        config.looseRaiseFrequencyPercent(), config.looseMaxCallPotPercent(),
                        config.looseLatePositionAdjustmentPercent());
                case "sized" -> new PotSizedPolicy(new LooseAggressivePolicy(config.looseOpenFrequencyPercent(),
                        config.looseRaiseFrequencyPercent(), config.looseMaxCallPotPercent(),
                        config.looseLatePositionAdjustmentPercent()), config.looseBetPotPercent());
                case "equity", "legacy-equity" -> new ContextualEquityPolicy(config.equitySamples(), config.cashCallMarginPercent(),
                        config.tournamentCallMarginPercent(), config.equityRaiseEdgePercent(),
                        config.valueBetThresholdPercent(), ContextualEquityPolicy.GameMode.TOURNAMENT,
                        config.equityContextWeights());
                case "pressure" -> new PressurePolicy();
                case "mixed", "bluff" -> new DeterministicBluffPolicy(config.bluffFrequencyPercent(),
                        config.latePositionWindow());
                default -> new TightAggressiveBaseline();
            };
        };
    }

    static String cohortFromArgs(String[] args) {
        return args.length >= 5 ? args[4] : "mixed";
    }

    static long buttonOffsetFromArgs(String[] args) {
        return args.length == 7 ? Long.parseLong(args[6]) : 0;
    }

    static Path policyPathFromArgs(String[] args) {
        return policyPathFromArgs(args, Path.of("config", "j6-policy.yaml"));
    }

    static Path policyPathFromArgs(String[] args, Path defaultPolicy) {
        return args.length >= 6 ? Path.of(args[5]) : Objects.requireNonNull(defaultPolicy, "defaultPolicy");
    }

    /** CLI arguments: profile YAML, new trace path, count, optional seed, style, policy YAML, button offset. */
    public static void main(String[] args) throws IOException {
        if (args.length < 3 || args.length > 7)
            throw new IllegalArgumentException("Expected profile.yaml trace.txt count [firstSeed] [style|control] [policy.yaml] [buttonOffset]");
        var profiles = TournamentProfiles.load(Path.of(args[0]));
        Path policyPath = policyPathFromArgs(args);
        var policyConfig = TournamentPolicyConfig.load(policyPath);
        int count = Integer.parseInt(args[2]);
        long firstSeed = args.length >= 4 ? Long.parseLong(args[3]) : 0;
        String cohort = cohortFromArgs(args);
        long buttonOffset = buttonOffsetFromArgs(args);
        if (!Set.of("legacy", "legacy-loose", "legacy-pot", "legacy-equity", "equity", "mixed", "bluff", "calling", "loose",
                "sized", "pressure", "control").contains(cohort))
            throw new IllegalArgumentException("Unknown policy cohort: " + cohort);
        if (count < 1) throw new IllegalArgumentException("Expected a positive tournament count");
        Math.addExact(firstSeed, count - 1L);
        try (var trace = new PrintWriter(Files.newBufferedWriter(Path.of(args[1]), StandardOpenOption.CREATE_NEW))) {
            for (var rules : profiles.values()) {
                int hands = 0;
                for (int trial = 0; trial < count; trial++) {
                    long seed = firstSeed + trial;
                    var policies = new ArrayList<DecisionPolicy>();
                    for (int seat = 0; seat < rules.seats(); seat++)
                        policies.add(policyForSeat(seat, seed, cohort, policyConfig));
                    hands += play(rules, seed, 12, 500, policies, buttonOffset, trace).hands();
                }
                System.out.printf("%d seats: %d %s tournaments, %d hands%n", rules.seats(), count, cohort, hands);
            }
            if (trace.checkError()) throw new IOException("Tournament trace write failed");
        }
    }

}
