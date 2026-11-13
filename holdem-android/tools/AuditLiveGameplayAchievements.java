package ro.atf.holdem.android;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

/** Simulates the live table without Android and records actual settlement facts. */
public final class AuditLiveGameplayAchievements {
    private static final int HAND_LIMIT = 500;

    private AuditLiveGameplayAchievements() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7)
            throw new IllegalArgumentException("Expected <difficulty> <count> <first-seed> <new.csv> <seconds> <samples> <seats>");
        var difficulty = LocalHoldemGame.Difficulty.valueOf(args[0]);
        int count = Integer.parseInt(args[1]);
        long firstSeed = Long.parseLong(args[2]);
        int seconds = Integer.parseInt(args[4]);
        int samples = Integer.parseInt(args[5]);
        var sizes = Arrays.stream(args[6].split(",")).map(Integer::parseInt).toList();
        if (count < 1 || seconds < 1 || samples < 1 || sizes.isEmpty() || !LocalHoldemGame.TOURNAMENT_SIZES.containsAll(sizes))
            throw new IllegalArgumentException("Invalid simulation inputs");
        try (var output = new PrintWriter(Files.newBufferedWriter(Path.of(args[3]), StandardOpenOption.CREATE_NEW))) {
            output.println("difficulty,seed,seats,hand,rank,seat,terminal,dealt,bigBlind,potWon,returned,category,showdownPlayers,potRecipients,sideEligible,sideWon,splitEligible,splitWon,dealer,smallBlind,activeAtStart,boardCards,bigBlindCheckedToFlop,sideOnlyWin,sweptSidePots,boardPlaysSplit,mainPotSoleWinner,knockouts,shortStackSeen,lastStackSeen,headsUpDeficit,hardMode,manualTitle");
            for (int trial = 0; trial < count; trial++) {
                play(difficulty, firstSeed + trial, sizes.get(trial % sizes.size()), seconds, samples, output);
            }
            if (output.checkError()) throw new IllegalStateException("Could not write calibration observations");
        }
    }

    private static void play(LocalHoldemGame.Difficulty difficulty, long seed, int seats, int seconds, int samples,
                             PrintWriter output) {
        var game = LocalHoldemGame.tournament(seats, seed, difficulty, () -> 0L, samples);
        for (int number = 1; number <= HAND_LIMIT; number++) {
            int steps = 0;
            var turn = game.turn();
            while (game.view().actor() >= 0) {
                if (++steps > 256) throw new IllegalStateException("Action limit for " + seed);
                if (!game.hasPassiveTurn()) game.autoPlayAction(turn);
                else if (difficulty == LocalHoldemGame.Difficulty.HARD) game.playOpponent(turn, game.suggestOpponent(turn));
                else game.advancePassiveAction(turn);
                turn = game.turn();
            }
            var frame = game.view();
            int viewer = frame.viewer();
            var player = frame.seats().get(viewer);
            var evaluation = player.handEvaluation();
            var pots = AchievementPotEvidence.from(game.potAwards(), frame);
            var context = game.achievementContext();
            int rank = game.settledRank();
            // Tournament achievements belong to the player's completed participation;
            // once their final rank is fixed, the remaining AI-vs-AI table is irrelevant.
            boolean terminal = rank > 0 || game.outcome() != null;
            long showdown = frame.seats().stream().filter(seat -> seat.handEvaluation() != null).count();
            long recipients = frame.seats().stream().filter(seat -> seat.potWon() > 0).count();
            boolean bigBlindChecked = frame.bettingActions().stream().anyMatch(action -> action.seat() == viewer
                    && action.street() == ro.atf.holdem.core.Street.PREFLOP
                    && action.kind() == ro.atf.holdem.core.BettingAction.Kind.CHECK);
            output.printf("%s,%d,%d,%d,%d,%d,%s,%s,%s,%d,%d,%s,%d,%d,%s,%s,%s,%s,%d,%d,%d,%d,%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%s%n",
                    difficulty, seed, seats, number, rank, viewer,
                    terminal, player.dealtCards(), viewer == frame.bigBlind(), player.potWon(), player.returned(),
                    evaluation == null ? "-" : evaluation.category().name(), showdown, recipients,
                    pots.sideEligible(), pots.sideWon(), pots.splitEligible(), pots.splitWon(),
                    frame.dealer(), frame.smallBlind(),
                    context.startingActivePlayers(), frame.board().size(), bigBlindChecked, pots.sideOnlyWon(),
                    pots.sweptSidePots(), pots.boardPlaysSplit(), pots.mainPotSoleWinner(), context.knockouts(),
                    context.shortStackSeen(), context.lastStackSeen(), context.headsUpDeficitSeen(),
                    context.hardDifficulty(), context.tournamentTitleManual());
            if (rank > 0 || game.outcome() != null) return;
            if (number < HAND_LIMIT) game.nextHandAtElapsedSeconds((long) seconds * number);
        }
    }
}
