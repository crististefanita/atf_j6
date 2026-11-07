package ro.atf.holdem.arena;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import ro.atf.holdem.ai.ContextualEquityPolicy;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.HoldemObservation;

/** Offline cash-table simulator: fixed blinds, persistent stacks and no tournament clock. */
public final class CashTableArena {
    public record Rules(int seats, int startingStack, BlindLevel fixedBlinds) {
        public Rules {
            Objects.requireNonNull(fixedBlinds, "fixedBlinds");
            if (seats < 2 || seats > 6 || startingStack < 1
                    || (long) seats * startingStack > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Cash rules require 2..6 seats and a positive conserved stack");
        }
    }

    public record Result(int handsPlayed, List<Integer> stacks, List<Integer> buttonSequence) {
        public Result {
            if (handsPlayed < 0) throw new IllegalArgumentException("handsPlayed cannot be negative");
            stacks = List.copyOf(stacks);
            buttonSequence = List.copyOf(buttonSequence);
            if (buttonSequence.size() != handsPlayed)
                throw new IllegalArgumentException("One button entry is required per played hand");
        }
    }

    private CashTableArena() {}

    /**
     * Runs at most {@code handLimit} hands. A busted seat stays out; play stops when fewer than two seats
     * retain chips. The trace contains only actor-visible observations plus public hand events.
     */
    public static Result play(Rules rules, long seed, int handLimit, List<DecisionPolicy> policies,
                              long buttonOffset, PrintWriter trace) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(policies, "policies");
        Objects.requireNonNull(trace, "trace");
        if (handLimit < 1 || policies.size() != rules.seats() || policies.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Expected positive hand limit and one policy per physical seat");

        var stacks = new ArrayList<Integer>(Collections.nCopies(rules.seats(), rules.startingStack()));
        var buttons = new ArrayList<Integer>(handLimit);
        var random = new SplittableRandom(seed);
        int button = Math.floorMod(Math.floorMod(seed, rules.seats())
                + Math.floorMod(buttonOffset, rules.seats()), rules.seats());
        trace.printf("CASH_SESSION seed=%d seats=%d startingStack=%d fixedBlinds=%s buttonOffset=%d firstButton=%d handLimit=%d%n",
                seed, rules.seats(), rules.startingStack(), rules.fixedBlinds(), buttonOffset, button, handLimit);

        for (int index = 0; index < handLimit && fundedCount(stacks) >= 2; index++) {
            if (index > 0) button = nextFunded(stacks, button);
            long dealSeed = random.nextLong();
            var hand = new HoldemHand(index + 1, stacks, button, rules.fixedBlinds(), HoldemHand.shuffledDeck(dealSeed));
            buttons.add(button);
            trace.printf("CASH_HAND %d button=%d fixedBlinds=%s dealSeed=%d%n",
                    index + 1, button, rules.fixedBlinds(), dealSeed);
            int published = publishEvents(hand, trace, 0);
            while (hand.actor() >= 0) {
                int seat = hand.actor();
                HoldemObservation observation = hand.observeActor();
                Decision decision = policies.get(seat).decide(observation);
                hand.act(seat, decision.action(), decision.amount());
                trace.printf("DECISION seat=%d policy=%s action=%s amount=%d reason=%s positionFromButton=%d street=%s pot=%d toCall=%d heroStack=%d activeOpponents=%d%n",
                        seat, decision.policyVersion(), decision.action(), decision.amount(), decision.explanation(),
                        observation.positionFromButton(), observation.street(), observation.pot(), observation.toCall(),
                        observation.heroStack(), observation.activeOpponentCount());
                published = publishEvents(hand, trace, published);
            }
            stacks = new ArrayList<>(hand.stacks());
            trace.printf("CASH_STACKS hand=%d stacks=%s funded=%d%n", index + 1, stacks, fundedCount(stacks));
        }

        int handsPlayed = buttons.size();
        trace.printf("CASH_SESSION_COMPLETE hands=%d stacks=%s buttons=%s%n", handsPlayed, stacks, buttons);
        if (trace.checkError()) throw new IllegalStateException("Cash session trace write failed");
        return new Result(handsPlayed, stacks, buttons);
    }

    private static int publishEvents(HoldemHand hand, PrintWriter trace, int from) {
        var events = hand.history();
        events.subList(from, events.size()).forEach(trace::println);
        return events.size();
    }

    private static int fundedCount(List<Integer> stacks) {
        return (int) stacks.stream().filter(stack -> stack > 0).count();
    }

    private static int nextFunded(List<Integer> stacks, int after) {
        for (int distance = 1; distance <= stacks.size(); distance++) {
            int seat = (after + distance) % stacks.size();
            if (stacks.get(seat) > 0) return seat;
        }
        throw new IllegalStateException("Cash session ended without a funded seat");
    }

    static DecisionPolicy policyForSeat(int seat, long seed, String cohort, TournamentPolicyConfig config) {
        Objects.requireNonNull(config, "config");
        if (seat < 0 || seat >= 6 || !List.of("cash-equity", "cash-control").contains(cohort))
            throw new IllegalArgumentException("Invalid seat or cash cohort");
        boolean candidate = Math.floorMod(seat + Math.floorMod(seed, 4), 4) == 3;
        if (cohort.equals("cash-equity") && candidate)
            return new ContextualEquityPolicy(config.equitySamples(), config.cashCallMarginPercent(),
                    config.tournamentCallMarginPercent(), config.equityRaiseEdgePercent(),
                    config.valueBetThresholdPercent(), ContextualEquityPolicy.GameMode.CASH,
                    config.equityContextWeights());
        return TournamentArena.policyForSeat(seat, seed, "mixed", config);
    }

    /** CLI: seats stack smallBlind bigBlind minOpen hands seed cash-equity|cash-control policy.yaml trace [buttonOffset]. */
    public static void main(String[] args) throws IOException {
        if (args.length < 10 || args.length > 11)
            throw new IllegalArgumentException("Expected seats stack smallBlind bigBlind minOpen hands seed cash-equity|cash-control policy.yaml trace [buttonOffset]");
        int seats = Integer.parseInt(args[0]);
        int stack = Integer.parseInt(args[1]);
        var rules = new Rules(seats, stack, new BlindLevel(Integer.parseInt(args[2]),
                Integer.parseInt(args[3]), Integer.parseInt(args[4])));
        int hands = Integer.parseInt(args[5]);
        long seed = Long.parseLong(args[6]);
        String cohort = args[7];
        if (!List.of("cash-equity", "cash-control").contains(cohort))
            throw new IllegalArgumentException("Unknown cash cohort: " + cohort);
        Path configPath = Path.of(args[8]);
        var config = TournamentPolicyConfig.load(configPath);
        Path tracePath = Path.of(args[9]);
        long buttonOffset = args.length == 11 ? Long.parseLong(args[10]) : 0;
        var policies = new ArrayList<DecisionPolicy>(seats);
        for (int seat = 0; seat < seats; seat++)
            policies.add(policyForSeat(seat, seed, cohort, config));
        try (var output = new PrintWriter(Files.newBufferedWriter(tracePath, StandardOpenOption.CREATE_NEW))) {
            var result = play(rules, seed, hands, policies, buttonOffset, output);
            System.out.printf("%d hands cash session, stacks=%s%n", result.handsPlayed(), result.stacks());
        }
    }
}
