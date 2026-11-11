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
        FULL_HOUSE_OR_BETTER, SOLE_HIGH_CARD_POT, MULTIWAY_SHOWDOWN_POT, SIDE_POT, SPLIT_POT
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
                AchievementPotEvidence.from(game.potAwards(), frame.viewer()));
    }

    List<Change> recordSettled(String profile, String hand, String tournament, int rank, HoldemTableViewState frame) {
        return recordSettled(profile, hand, tournament, rank, frame, AchievementPotEvidence.NONE);
    }

    synchronized List<Change> recordSettled(String profile, String hand, String tournament, int rank,
                                            HoldemTableViewState frame, AchievementPotEvidence pots) {
        if (tournament == null || frame.street() != Street.COMPLETE) return List.of();
        var recorded = storage.read(profile);
        var updates = new EnumMap<Metric, Set<String>>(Metric.class);
        var seat = frame.seats().get(frame.viewer());
        if (seat.handEvaluation() != null) credit(recorded, Metric.SHOWDOWN, hand, updates);
        if (seat.potWon() > 0) {
            credit(recorded, Metric.POT, hand, updates);
            if (pots.sideWon()) credit(recorded, Metric.SIDE_POT, hand, updates);
            if (pots.splitWon()) credit(recorded, Metric.SPLIT_POT, hand, updates);
            if (frame.viewer() == frame.bigBlind()) credit(recorded, Metric.BIG_BLIND_POT, hand, updates);
            var evaluation = seat.handEvaluation();
            if (evaluation != null) {
                long showdownPlayers = frame.seats().stream().filter(player -> player.handEvaluation() != null).count();
                if (showdownPlayers >= 3) credit(recorded, Metric.MULTIWAY_SHOWDOWN_POT, hand, updates);
                var category = evaluation.category();
                if (category.compareTo(HandRank.Category.STRAIGHT) >= 0)
                    credit(recorded, Metric.STRAIGHT_OR_BETTER, hand, updates);
                if (category.compareTo(HandRank.Category.FULL_HOUSE) >= 0)
                    credit(recorded, Metric.FULL_HOUSE_OR_BETTER, hand, updates);
            }
        }
        if (rank >= 1 && rank <= frame.seats().size()) {
            credit(recorded, Metric.TOURNAMENT_FINISHED, tournament, updates);
            if (rank <= 3) credit(recorded, Metric.PODIUM, tournament, updates);
            if (rank == 1) {
                credit(recorded, Metric.TITLE, tournament, updates);
                if (frame.seats().size() == 6) credit(recorded, Metric.SIX_SEAT_TITLE, tournament, updates);
            }
        }
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
}
