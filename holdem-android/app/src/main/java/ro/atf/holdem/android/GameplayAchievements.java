package ro.atf.holdem.android;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

/** Projects settled poker results into independent, bounded progress counters. */
final class GameplayAchievements {
    enum Metric {
        POT(3), SHOWDOWN_POT(3), UNCONTESTED_POT(1), TOURNAMENT_POT(5),
        PODIUM(1), TITLE(3), SIX_SEAT_TITLE(2), STRAIGHT_OR_BETTER(1),
        FULL_HOUSE_OR_BETTER(1), SOLE_HIGH_CARD_POT(1), MULTIWAY_TOURNAMENT_UNCONTESTED(5);

        final int cap;
        Metric(int cap) { this.cap = cap; }
    }

    interface Storage {
        Map<Metric, Set<String>> read(String profile);
        void write(String profile, Map<Metric, Set<String>> updates);
    }

    private final Storage storage;
    private final String currentProfile;

    GameplayAchievements(Storage storage) {
        this(storage, null);
    }

    GameplayAchievements(Storage storage, String currentProfile) {
        this.currentProfile = currentProfile;
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

    void recordSettled(LocalHoldemGame game) {
        if (game == null) return;
        var frame = game.view();
        if (frame.street() != Street.COMPLETE) return;
        String tournament = game.isTournament() ? game.tournamentId() : null;
        String hand = tournament == null
                ? "table:" + game.displayNameSeed() + ":" + frame.dealer()
                : tournament + ":" + game.handNumber();
        int rank = game.outcome() == null ? 0 : game.finishingRanks().get(frame.viewer());
        String owner = game.playerId(frame.viewer());
        if (tournament == null && currentProfile != null
                && ("local-human".equals(owner) || "legacy-human".equals(owner))) {
            // Old standalone hands predate profile IDs. They belong to this installation's profile.
            owner = currentProfile;
        }
        recordSettled(owner, hand, tournament, rank, frame);
    }

    synchronized void recordSettled(String profile, String hand, String tournament, int rank, HoldemTableViewState frame) {
        if (frame.street() != Street.COMPLETE) return;
        var recorded = storage.read(profile);
        var updates = new EnumMap<Metric, Set<String>>(Metric.class);
        var seat = frame.seats().get(frame.viewer());
        if (seat.potWon() > 0) {
            credit(recorded, Metric.POT, hand, updates);
            if (tournament != null) credit(recorded, Metric.TOURNAMENT_POT, hand, updates);
            var evaluation = seat.handEvaluation();
            if (evaluation == null) {
                credit(recorded, Metric.UNCONTESTED_POT, hand, updates);
                if (tournament != null && frame.seats().stream().filter(HoldemTableViewState.Seat::dealtCards).count() >= 3)
                    credit(recorded, Metric.MULTIWAY_TOURNAMENT_UNCONTESTED, hand, updates);
            } else {
                credit(recorded, Metric.SHOWDOWN_POT, hand, updates);
                var category = evaluation.category();
                if (category.compareTo(HandRank.Category.STRAIGHT) >= 0)
                    credit(recorded, Metric.STRAIGHT_OR_BETTER, hand, updates);
                if (category.compareTo(HandRank.Category.FULL_HOUSE) >= 0)
                    credit(recorded, Metric.FULL_HOUSE_OR_BETTER, hand, updates);
                if (category == HandRank.Category.HIGH_CARD
                        && frame.seats().stream().filter(other -> other.potWon() > 0).count() == 1)
                    credit(recorded, Metric.SOLE_HIGH_CARD_POT, hand, updates);
            }
        }
        if (tournament != null && rank >= 1 && rank <= 3) {
            credit(recorded, Metric.PODIUM, tournament, updates);
            if (rank == 1) {
                credit(recorded, Metric.TITLE, tournament, updates);
                if (frame.seats().size() == 6) credit(recorded, Metric.SIX_SEAT_TITLE, tournament, updates);
            }
        }
        if (!updates.isEmpty()) storage.write(profile, updates);
    }

    private static void credit(Map<Metric, Set<String>> recorded, Metric metric, String result, Map<Metric, Set<String>> updates) {
        var ids = recorded.getOrDefault(metric, Set.of());
        if (ids.size() >= metric.cap || ids.contains(result)) return;
        var next = new HashSet<>(ids);
        next.add(result);
        updates.put(metric, Set.copyOf(next));
    }
}
