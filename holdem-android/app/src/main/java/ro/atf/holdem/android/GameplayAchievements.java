package ro.atf.holdem.android;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

/** Projects settled poker results into independent, bounded progress counters. */
final class GameplayAchievements {
    enum Metric {
        POT, TOURNAMENT_POT, BIG_BLIND_POT, SHOWDOWN, TOURNAMENT_FINISHED,
        PODIUM, TITLE, SIX_SEAT_TITLE, STRAIGHT_OR_BETTER,
        FULL_HOUSE_OR_BETTER, SOLE_HIGH_CARD_POT, MULTIWAY_SHOWDOWN_POT, SIDE_POT, SPLIT_POT,
        BUTTON_POT, SMALL_BLIND_POT, HEADS_UP_POT, BIG_BLIND_CHECK_TO_FLOP, SHOWDOWN_POT,
        PAIR_SHOWDOWN_POT, MULTIWAY_POT, POSTFLOP_POT, TWO_PAIR_SHOWDOWN_POT,
        HEADS_UP_SHOWDOWN_POT, SIDE_POT_ONLY, SWEPT_SIDE_POTS, PREFLOP_FOLD_WIN,
        SOLE_MAIN_WIN_ELIMINATION, HEADS_UP_PHASE_REACHED, FOUR_SEAT_TITLE, FIVE_SEAT_TITLE,
        BOARD_PLAYS_SPLIT, SHORT_STACK_TITLE, TITLE_AFTER_HEADS_UP_DEFICIT,
        TITLE_AFTER_LAST_STACK, TITLE_WITHOUT_HEADS_UP, HARD_SIX_SEAT_MANUAL_TITLE,
        ALL_TABLE_SIZES_TITLE, HARD_SIX_HIGH_CARD_POT
    }

    record HandContext(int startingSeats, int startingActivePlayers, boolean humanActive,
                       boolean shortStackSeen, boolean lastStackSeen, boolean headsUpDeficitSeen,
                       boolean tournamentTitleManual, boolean hardDifficulty, int knockouts) {
        HandContext {
            if (startingSeats < 2 || startingSeats > 6 || startingActivePlayers < 1
                    || startingActivePlayers > startingSeats || knockouts < 0)
                throw new IllegalArgumentException("Invalid achievement hand context");
        }

        static HandContext fromFrame(HoldemTableViewState frame) {
            int players = frame.seats().size();
            int active = (int) frame.seats().stream().filter(HoldemTableViewState.Seat::dealtCards).count();
            return new HandContext(players, active == 0 ? players : active,
                    frame.seats().get(frame.viewer()).dealtCards(), false, false, false, false, false, 0);
        }

        boolean fullyManualHardSixSeat() {
            return startingSeats == 6 && hardDifficulty && tournamentTitleManual;
        }
    }

    record Change(GameplayAchievementCatalog.Milestone milestone, int count) {
        boolean unlocked() { return count == milestone.target(); }
    }

    interface Storage {
        Map<Metric, Set<String>> read(String profile);
        void write(String profile, Map<Metric, Set<String>> updates);
    }

    private final Storage storage;
    GameplayAchievements(Storage storage) {
        this.storage = storage;
    }

    int count(String profile, Metric metric) {
        return counts(profile).getOrDefault(metric, 0);
    }

    Map<Metric, Integer> counts(String profile) {
        var result = new EnumMap<Metric, Integer>(Metric.class);
        storage.read(profile).forEach((metric, ids) -> result.put(metric, ids.size()));
        return result;
    }

    List<Change> recordSettled(LocalHoldemGame game) {
        if (game == null || !game.isTournament()) return List.of();
        var frame = game.view();
        if (frame.street() != Street.COMPLETE) return List.of();
        String tournament = game.tournamentId();
        String owner = game.playerId(frame.viewer());
        return recordSettled(owner, tournament + ":" + game.handNumber(), tournament, game.settledRank(), frame,
                AchievementPotEvidence.from(game.potAwards(), frame), game.achievementContext());
    }

    List<Change> recordSettled(String profile, String hand, String tournament, int rank, HoldemTableViewState frame) {
        return recordSettled(profile, hand, tournament, rank, frame, AchievementPotEvidence.NONE);
    }

    synchronized List<Change> recordSettled(String profile, String hand, String tournament, int rank,
                                            HoldemTableViewState frame, AchievementPotEvidence pots) {
        return recordSettled(profile, hand, tournament, rank, frame, pots, HandContext.fromFrame(frame));
    }

    synchronized List<Change> recordSettled(String profile, String hand, String tournament, int rank,
                                            HoldemTableViewState frame, AchievementPotEvidence pots,
                                            HandContext context) {
        if (tournament == null || frame.street() != Street.COMPLETE) return List.of();
        var recorded = storage.read(profile);
        var updates = new EnumMap<Metric, Set<String>>(Metric.class);
        var seat = frame.seats().get(frame.viewer());
        if (seat.handEvaluation() != null) credit(recorded, Metric.SHOWDOWN, hand, updates);
        boolean reachedFlop = frame.board().size() >= 3;
        if (reachedFlop && frame.viewer() == frame.bigBlind() && frame.bettingActions().stream()
                .anyMatch(action -> action.seat() == frame.viewer() && action.street() == Street.PREFLOP
                        && action.kind() == ro.atf.holdem.core.BettingAction.Kind.CHECK))
            credit(recorded, Metric.BIG_BLIND_CHECK_TO_FLOP, hand, updates);
        if (seat.potWon() > 0) {
            credit(recorded, Metric.POT, hand, updates);
            credit(recorded, Metric.BUTTON_POT, frame.viewer() == frame.dealer(), hand, updates);
            credit(recorded, Metric.SMALL_BLIND_POT, frame.viewer() == frame.smallBlind(), hand, updates);
            credit(recorded, Metric.HEADS_UP_POT,
                    context.humanActive() && context.startingActivePlayers() == 2, hand, updates);
            credit(recorded, Metric.MULTIWAY_POT, context.startingActivePlayers() >= 3, hand, updates);
            credit(recorded, Metric.POSTFLOP_POT, reachedFlop, hand, updates);
            credit(recorded, Metric.PREFLOP_FOLD_WIN,
                    frame.board().isEmpty() && seat.handEvaluation() == null, hand, updates);
            if (pots.sideWon()) credit(recorded, Metric.SIDE_POT, hand, updates);
            if (pots.splitWon()) credit(recorded, Metric.SPLIT_POT, hand, updates);
            if (pots.sideOnlyWon()) credit(recorded, Metric.SIDE_POT_ONLY, hand, updates);
            if (pots.sweptSidePots()) credit(recorded, Metric.SWEPT_SIDE_POTS, hand, updates);
            if (pots.boardPlaysSplit()) credit(recorded, Metric.BOARD_PLAYS_SPLIT, hand, updates);
            if (seat.handEvaluation() != null && pots.mainPotSoleWinner() && context.knockouts() >= 1)
                credit(recorded, Metric.SOLE_MAIN_WIN_ELIMINATION, hand, updates);
            if (frame.viewer() == frame.bigBlind()) credit(recorded, Metric.BIG_BLIND_POT, hand, updates);
            var evaluation = seat.handEvaluation();
            if (evaluation != null) {
                credit(recorded, Metric.SHOWDOWN_POT, hand, updates);
                long showdownPlayers = frame.seats().stream().filter(player -> player.handEvaluation() != null).count();
                if (showdownPlayers >= 3) credit(recorded, Metric.MULTIWAY_SHOWDOWN_POT, hand, updates);
                var category = evaluation.category();
                if (category == HandRank.Category.ONE_PAIR) credit(recorded, Metric.PAIR_SHOWDOWN_POT, hand, updates);
                if (category == HandRank.Category.TWO_PAIR) credit(recorded, Metric.TWO_PAIR_SHOWDOWN_POT, hand, updates);
                if (context.humanActive() && context.startingActivePlayers() == 2)
                    credit(recorded, Metric.HEADS_UP_SHOWDOWN_POT, hand, updates);
                if (category.compareTo(HandRank.Category.STRAIGHT) >= 0)
                    credit(recorded, Metric.STRAIGHT_OR_BETTER, hand, updates);
                if (category.compareTo(HandRank.Category.FULL_HOUSE) >= 0)
                    credit(recorded, Metric.FULL_HOUSE_OR_BETTER, hand, updates);
                long potRecipients = frame.seats().stream().filter(player -> player.potWon() > 0).count();
                if (context.hardDifficulty() && context.startingSeats() == 6
                        && category == HandRank.Category.HIGH_CARD && potRecipients == 1)
                    credit(recorded, Metric.HARD_SIX_HIGH_CARD_POT, hand, updates);
            }
        }
        if (rank >= 1 && rank <= frame.seats().size()) {
            credit(recorded, Metric.TOURNAMENT_FINISHED, tournament, updates);
            if (rank <= 3) credit(recorded, Metric.PODIUM, tournament, updates);
            if (rank == 1) {
                credit(recorded, Metric.TITLE, tournament, updates);
                if (frame.seats().size() == 6) credit(recorded, Metric.SIX_SEAT_TITLE, tournament, updates);
                if (frame.seats().size() == 4) credit(recorded, Metric.FOUR_SEAT_TITLE, tournament, updates);
                if (frame.seats().size() == 5) credit(recorded, Metric.FIVE_SEAT_TITLE, tournament, updates);
                if (context.shortStackSeen()) credit(recorded, Metric.SHORT_STACK_TITLE, tournament, updates);
                if (context.lastStackSeen()) credit(recorded, Metric.TITLE_AFTER_LAST_STACK, tournament, updates);
                if (context.headsUpDeficitSeen())
                    credit(recorded, Metric.TITLE_AFTER_HEADS_UP_DEFICIT, tournament, updates);
                if (context.startingActivePlayers() >= 3)
                    credit(recorded, Metric.TITLE_WITHOUT_HEADS_UP, tournament, updates);
                if (context.fullyManualHardSixSeat())
                    credit(recorded, Metric.HARD_SIX_SEAT_MANUAL_TITLE, tournament, updates);
                if (context.startingSeats() >= 4 && context.startingSeats() <= 6 && context.tournamentTitleManual())
                    credit(recorded, Metric.ALL_TABLE_SIZES_TITLE, "seat-size:" + context.startingSeats(), updates);
            }
        }
        if (context.humanActive() && context.startingActivePlayers() == 2)
            credit(recorded, Metric.HEADS_UP_PHASE_REACHED, tournament, updates);
        if (updates.isEmpty()) return List.of();
        storage.write(profile, updates);
        return changes(updates);
    }

    private static List<Change> changes(Map<Metric, Set<String>> updates) {
        var changes = new ArrayList<Change>();
        for (var tier : GameplayAchievementCatalog.Tier.values()) {
            for (var milestone : GameplayAchievementCatalog.forTier(tier)) {
                var ids = updates.get(milestone.metric());
                if (ids != null && ids.size() <= milestone.target()) changes.add(new Change(milestone, ids.size()));
            }
        }
        return List.copyOf(changes);
    }

    private static void credit(Map<Metric, Set<String>> recorded, Metric metric, String result, Map<Metric, Set<String>> updates) {
        var ids = recorded.getOrDefault(metric, Set.of());
        if (ids.size() >= GameplayAchievementCatalog.maxTarget(metric) || ids.contains(result)) return;
        var next = new HashSet<>(ids);
        next.add(result);
        updates.put(metric, Set.copyOf(next));
    }

    private static void credit(Map<Metric, Set<String>> recorded, Metric metric, boolean condition, String result,
                               Map<Metric, Set<String>> updates) {
        if (condition) credit(recorded, metric, result, updates);
    }
}
