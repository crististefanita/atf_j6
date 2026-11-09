package ro.atf.holdem.android;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.SplittableRandom;

import ro.atf.holdem.ai.CallingStationPolicy;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.ai.DeterministicBluffPolicy;
import ro.atf.holdem.ai.LooseAggressivePolicy;
import ro.atf.holdem.ai.PressurePolicy;
import ro.atf.holdem.ai.TightAggressiveBaseline;
import ro.atf.holdem.core.HoldemTableViewState;

/** Emits settled engine facts for calibration without building or launching Android. */
public final class AuditGameplayAchievements {
    private static final int HAND_LIMIT = 500;
    private static final List<DecisionPolicy> MIXED = List.of(
            new TightAggressiveBaseline(), new CallingStationPolicy(), new PressurePolicy(),
            new DeterministicBluffPolicy(25, 2), new LooseAggressivePolicy(56, 25, 40, 14));

    private AuditGameplayAchievements() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6 || !List.of("product", "mixed").contains(args[0]))
            throw new IllegalArgumentException("Expected <product|mixed> <count> <first-seed> <new.csv> <seconds-per-hand> <seat-counts>");
        String mode = args[0];
        int count = Integer.parseInt(args[1]);
        long firstSeed = Long.parseLong(args[2]);
        int seconds = Integer.parseInt(args[4]);
        var seatCounts = java.util.Arrays.stream(args[5].split(",")).map(Integer::parseInt).toList();
        if (count < 1 || seconds < 1 || !LocalHoldemGame.TOURNAMENT_SIZES.containsAll(seatCounts)
                || seatCounts.stream().distinct().count() != seatCounts.size())
            throw new IllegalArgumentException("Invalid tournament count, clock or seats");
        try (var output = new PrintWriter(Files.newBufferedWriter(Path.of(args[3]), StandardOpenOption.CREATE_NEW))) {
            output.println("mode,seed,seats,viewer,policy,hand,rank,potWon,refund,category,dealt,showdownPlayers,potRecipients,bigBlind,stack,contribution");
            for (int trial = 0; trial < count; trial++) {
                long seed = firstSeed + trial;
                int seats = seatCounts.get(trial % seatCounts.size());
                if (mode.equals("product")) product(seed, seats, seconds, output);
                else mixed(seed, seats, seconds, output);
            }
            if (output.checkError()) throw new IllegalStateException("Could not write calibration observations");
        }
    }

    private static void product(long seed, int seats, int seconds, PrintWriter output) {
        var game = LocalHoldemGame.tournament(seats, seed, 1, () -> 0L);
        for (int number = 1; number <= HAND_LIMIT; number++) {
            int actions = 0;
            while (game.view().actor() >= 0) {
                if (++actions > 256) throw new IllegalStateException("Action limit for " + seed);
                if (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
                else game.autoPlayAction(game.turn());
            }
            boolean complete = game.outcome() != null;
            var frame = game.view();
            int rank = complete ? game.finishingRanks().get(frame.viewer()) : 0;
            publish(output, "product", seed, "advisor", number, rank, frame);
            if (complete) return;
            game.nextHandAtElapsedSeconds((long) seconds * number);
        }
        System.err.println("Product tournament reached the observation limit: " + seed);
    }

    private static void mixed(long seed, int seats, int seconds, PrintWriter output) {
        var random = new SplittableRandom(seed);
        var rules = LocalHoldemGame.tournamentRules(seats, 1);
        var game = new ro.atf.holdem.core.Tournament(rules, random.nextInt(seats));
        for (int number = 1; number <= HAND_LIMIT; number++) {
            var hand = game.startHand((long) seconds * (number - 1), random.nextLong()).hand();
            int actions = 0;
            while (hand.actor() >= 0) {
                if (++actions > 256) throw new IllegalStateException("Action limit for " + seed);
                int actor = hand.actor();
                var decision = policy(seed, actor).decide(hand.observeActor());
                hand.act(actor, decision.action(), decision.amount());
            }
            boolean complete = game.stacks().stream().filter(stack -> stack > 0).count() == 1;
            if (complete) game.finish();
            for (int viewer = 0; viewer < seats; viewer++) {
                int rank = complete ? game.finishingRanks().get(viewer) : 0;
                publish(output, "mixed", seed, policy(seed, viewer).getClass().getSimpleName(),
                        number, rank, hand.viewFor(viewer));
            }
            if (complete) return;
        }
        System.err.println("Mixed tournament reached the observation limit: " + seed);
    }

    private static DecisionPolicy policy(long seed, int seat) {
        return MIXED.get(Math.floorMod(seed + seat, MIXED.size()));
    }

    private static void publish(PrintWriter output, String mode, long seed, String policy, int number,
                                int rank, HoldemTableViewState frame) {
        var seat = frame.seats().get(frame.viewer());
        var category = seat.handEvaluation() == null ? "-" : seat.handEvaluation().category().name();
        long dealt = frame.seats().stream().filter(HoldemTableViewState.Seat::dealtCards).count();
        long showdown = frame.seats().stream().filter(player -> player.handEvaluation() != null).count();
        long recipients = frame.seats().stream().filter(player -> player.potWon() > 0).count();
        output.printf("%s,%d,%d,%d,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%d%n", mode, seed, frame.seats().size(),
                frame.viewer(), policy, number, rank, seat.potWon(), seat.returned(), category, dealt, showdown,
                recipients, frame.bigBlind() == frame.viewer() ? 1 : 0, seat.stack(), seat.contributed());
    }
}
