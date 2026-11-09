package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HandEvaluation;
import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static ro.atf.holdem.android.GameplayAchievements.Metric.*;

public class GameplayAchievementsComponentTest {
    @Test public void onePokerHandAdvancesSeveralIndependentObjectivesInEitherOrder() {
        var achievements = memory();
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")), seat(0, 0, null), 4);
        achievements.recordSettled("p", "t:1", "t", 0, hand);

        var easy = GameplayAchievementCatalog.forTier(AchievementCatalog.Tier.EASY);
        assertEquals(POT, easy.get(0).metric());
        assertEquals(1, easy.get(0).progress(achievements.counts("p")));
        assertEquals(1, easy.get(1).progress(achievements.counts("p")));
        assertEquals(0, easy.get(2).progress(achievements.counts("p")));
        assertEquals(1, easy.get(3).progress(achievements.counts("p")));
        assertEquals(1, achievements.count("p", SOLE_HIGH_CARD_POT));
        assertEquals(0, achievements.count("p", TITLE));
        assertEquals(0, achievements.count("other", POT));

        var wonByFolds = settled(0, seat(80, 20, null), seat(0, 0, null), 4);
        achievements.recordSettled("p", "t:2", "t", 0, wonByFolds);
        assertEquals(1, easy.get(1).progress(achievements.counts("p")));
        assertEquals(2, easy.get(3).progress(achievements.counts("p")));
        assertEquals(0, achievements.count("p", MULTIWAY_SHOWDOWN_POT));
    }

    @Test public void noCreditForOpenHandRefundOrOpponentWin() {
        var achievements = memory();
        var refund = settled(1, seat(70, 0, null), seat(0, 30, null), 4);
        achievements.recordSettled("p", "t:1", "t", 0, refund);
        var open = new HoldemTableViewState(0, 0, 0, 1, 2, Street.FLOP, 70, List.of(), List.of(),
                List.of(seat(0, 0, null), seat(0, 0, null)), List.of());
        achievements.recordSettled("p", "t:2", "t", 1, open);
        assertEquals(0, achievements.count("p", POT));
        assertEquals(0, achievements.count("p", TITLE));
        assertEquals(0, achievements.count("p", BIG_BLIND_POT));
    }

    @Test public void highCardRequiresShowdownAndSolePotRecipient() {
        var highCard = evaluation("As Kd Jh 8c 5d");
        var pair = evaluation("As Ad Jh 8c 5d");
        var achievements = memory();
        achievements.recordSettled("p", "t:1", "t", 0, settled(0, seat(25, 0, highCard), seat(25, 0, highCard), 4));
        achievements.recordSettled("p", "t:2", "t", 0, settled(0, seat(50, 0, pair), seat(0, 0, null), 4));
        achievements.recordSettled("p", "t:3", "t", 0, settled(0, seat(50, 0, null), seat(0, 0, null), 4));
        assertEquals(3, achievements.count("p", POT));
        assertEquals(0, achievements.count("p", SOLE_HIGH_CARD_POT));

        achievements.recordSettled("p", "t:4", "t", 0, settled(0, seat(50, 0, highCard), seat(0, 30, null), 4));
        assertEquals(1, achievements.count("p", SOLE_HIGH_CARD_POT));
    }

    @Test public void rankingAndCombinationBoundariesFollowPokerFacts() {
        var achievements = memory();
        var noWin = settled(0, seat(0, 0, null), seat(90, 0, null), 4);
        achievements.recordSettled("p", "outside:1", "outside", 4, noWin);
        assertEquals(0, achievements.count("p", PODIUM));
        achievements.recordSettled("p", "podium:1", "podium", 3, noWin);
        assertEquals(1, achievements.count("p", PODIUM));
        assertEquals(0, achievements.count("p", TITLE));

        var straight = settled(0, seat(50, 0, evaluation("9s 8d 7h 6c 5d")), seat(0, 0, null), 2);
        achievements.recordSettled("p", "cash:1", null, 0, straight);
        assertEquals(1, achievements.count("p", STRAIGHT_OR_BETTER));
        assertEquals(0, achievements.count("p", FULL_HOUSE_OR_BETTER));
        assertEquals(0, achievements.count("p", TOURNAMENT_POT));

        var fullHouse = settled(0, seat(50, 0, evaluation("As Ad Ah 5c 5d")), seat(0, 0, null), 2);
        achievements.recordSettled("p", "cash:2", null, 0, fullHouse);
        assertEquals(1, achievements.count("p", FULL_HOUSE_OR_BETTER));
    }

    @Test public void completedTournamentIdsAreDistinctAcrossRestartsAndThresholdsRunInParallel() {
        var storage = new MemoryStorage();
        var achievements = new GameplayAchievements(storage);
        var sixSeats = settled(0, seat(80, 0, null), seat(0, 0, null), 6);
        var fourSeats = settled(0, seat(80, 0, null), seat(0, 0, null), 4);
        achievements.recordSettled("p", "a:4", "a", 1, sixSeats);
        var restarted = new GameplayAchievements(storage);
        restarted.recordSettled("p", "a:4", "a", 1, sixSeats);
        var medium = GameplayAchievementCatalog.forTier(AchievementCatalog.Tier.MEDIUM).get(0);
        var hard = GameplayAchievementCatalog.forTier(AchievementCatalog.Tier.HARD);
        assertEquals(1, medium.progress(restarted.counts("p")));
        assertEquals(1, hard.get(0).progress(restarted.counts("p")));
        assertEquals(1, restarted.count("p", TOURNAMENT_POT));

        restarted.recordSettled("p", "b:5", "b", 1, fourSeats);
        assertEquals(2, hard.get(0).progress(restarted.counts("p")));
        assertEquals(1, hard.get(4).progress(restarted.counts("p")));
        restarted.recordSettled("p", "c:9", "c", 1, sixSeats);
        restarted.recordSettled("p", "d:3", "d", 1, sixSeats);
        assertEquals(3, hard.get(0).progress(restarted.counts("p")));
        assertEquals(2, hard.get(4).progress(restarted.counts("p")));
        assertEquals(0, hard.get(1).progress(restarted.counts("p")));
    }

    @Test public void realTournamentResultKeepsItsOwnerAndIdentityAfterReload() {
        var game = LocalHoldemGame.tournament(4, 1, 1, "profile-a");
        var achievements = memory();
        achievements.recordSettled(game);
        assertEquals(0, achievements.count("profile-a", POT));

        playAndAdvance(game, ActionType.CALL, 20);
        for (int street = 0; street < 3; street++) playAndAdvance(game, ActionType.CHECK, 0);
        game.nextHand();
        playAndAdvance(game, ActionType.ALL_IN, 1960);
        game.nextHand();
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
        playAndAdvance(game, ActionType.ALL_IN, 7900);

        assertEquals(LocalHoldemGame.Outcome.WON, game.outcome());
        achievements.recordSettled(game);
        achievements.recordSettled(LocalHoldemGame.restore(game.save()));
        assertEquals(1, achievements.count("profile-a", POT));
        assertEquals(1, achievements.count("profile-a", TITLE));
        assertEquals(0, achievements.count("local-human", POT));
    }

    private static void playAndAdvance(LocalHoldemGame game, ActionType action, int amount) {
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
        game.play(game.turn(), new LocalHoldemGame.Move(action, amount));
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
    }

    @Test public void existingStandaloneTableCreditsTheInstallationProfileAfterUpgrade() {
        LocalHoldemGame winner = null;
        for (int seed = 1; seed <= 12 && winner == null; seed++) {
            var game = new LocalHoldemGame(2, seed);
            var call = game.view().legalActions().stream()
                    .filter(action -> action.type() == ActionType.CALL).findFirst().orElseThrow();
            playAndAdvance(game, ActionType.CALL, call.amount());
            while (game.view().street() != Street.COMPLETE) playAndAdvance(game, ActionType.CHECK, 0);
            if (game.view().seats().get(game.view().viewer()).potWon() > 0) winner = game;
        }
        assertNotNull("cohort must contain a cash hand won by the viewer", winner);
        var achievements = new GameplayAchievements(new MemoryStorage(), "installed-profile");
        achievements.recordSettled(winner);
        achievements.recordSettled(LocalHoldemGame.restore(winner.save()));
        assertEquals(1, achievements.count("installed-profile", POT));
        assertEquals(0, achievements.count("local-human", POT));
    }

    @Test public void failedBatchCanBeRetriedWithoutPartialCredit() {
        var storage = new MemoryStorage();
        var achievements = new GameplayAchievements(storage);
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")), seat(0, 0, null), 4);
        storage.failWrite = true;
        assertThrows(IllegalStateException.class, () -> achievements.recordSettled("p", "t:1", "t", 1, hand));
        assertTrue(achievements.counts("p").isEmpty());

        storage.failWrite = false;
        achievements.recordSettled("p", "t:1", "t", 1, hand);
        achievements.recordSettled("p", "t:1", "t", 1, hand);
        assertEquals(1, achievements.count("p", POT));
        assertEquals(1, achievements.count("p", TITLE));
        assertEquals(1, achievements.count("p", TOURNAMENT_FINISHED));
    }

    @Test public void allCatalogEntriesUnlockAtTheirOwnThresholdInPublishedOrder() {
        assertMilestones(AchievementCatalog.Tier.EASY, List.of(
                "first-pot:POT:1", "first-tournament-pot:TOURNAMENT_POT:1", "big-blind-pot:BIG_BLIND_POT:1",
                "three-pots:POT:3", "finish-tournament:TOURNAMENT_FINISHED:1"));
        assertMilestones(AchievementCatalog.Tier.MEDIUM, List.of(
                "first-title:TITLE:1", "tournament-pots:TOURNAMENT_POT:5", "multiway-showdowns:MULTIWAY_SHOWDOWN_POT:3",
                "straight-or-better:STRAIGHT_OR_BETTER:2", "tournament-podiums:PODIUM:2"));
        assertMilestones(AchievementCatalog.Tier.HARD, List.of(
                "three-titles:TITLE:3", "high-card:SOLE_HIGH_CARD_POT:1", "full-house-or-better:FULL_HOUSE_OR_BETTER:2",
                "multiway-showdown-series:MULTIWAY_SHOWDOWN_POT:12", "six-seat-titles:SIX_SEAT_TITLE:2"));
    }

    private static void assertMilestones(AchievementCatalog.Tier tier, List<String> expected) {
        var milestones = GameplayAchievementCatalog.forTier(tier);
        var keys = milestones.stream().map(rule -> rule.id() + ":" + rule.metric() + ":" + rule.target())
                .collect(java.util.stream.Collectors.toList());
        assertEquals(expected, keys);
        for (var rule : milestones) {
            assertEquals(rule.id(), 0, rule.progress(Map.of()));
            assertEquals(rule.id(), rule.target() - 1, rule.progress(Map.of(rule.metric(), rule.target() - 1)));
            assertEquals(rule.id(), rule.target(), rule.progress(Map.of(rule.metric(), rule.target())));
            assertEquals(rule.id(), rule.target(), rule.progress(Map.of(rule.metric(), rule.target() + 1)));
        }
    }

    @Test public void blindAndMultiwayCreditUseActualSeatAndShowdownParticipation() {
        var achievements = memory();
        var winner = seat(80, 0, evaluation("As Ad Ah 5c 5d"));
        var runnerUp = seat(0, 0, evaluation("Ks Kd Qh Jc 8d"));
        var third = seat(0, 0, evaluation("Qs Qd Jh 8c 5d"));
        var folded = new HoldemTableViewState.Seat(0, 0, true, 0, 0, 0, true, List.of(), null);
        var headsUpShowdown = new HoldemTableViewState(2, -1, 0, 1, 0, Street.COMPLETE, 80, List.of(),
                List.of(), List.of(runnerUp, folded, winner, folded), List.of());
        achievements.recordSettled("p", "a:1", "a", 0, headsUpShowdown);
        assertEquals(0, achievements.count("p", BIG_BLIND_POT));
        assertEquals(0, achievements.count("p", MULTIWAY_SHOWDOWN_POT));

        var multiway = new HoldemTableViewState(2, -1, 0, 1, 2, Street.COMPLETE, 80, List.of(),
                List.of(), List.of(runnerUp, third, winner, folded), List.of());
        for (int hand = 2; hand <= 14; hand++) achievements.recordSettled("p", "a:" + hand, "a", 0, multiway);
        assertEquals(1, achievements.count("p", BIG_BLIND_POT));
        assertEquals(12, achievements.count("p", MULTIWAY_SHOWDOWN_POT));
        assertEquals(2, achievements.count("p", STRAIGHT_OR_BETTER));
        assertEquals(2, achievements.count("p", FULL_HOUSE_OR_BETTER));
    }

    @Test public void finishingAndPodiumAreDistinctEvenWithoutWinningTheFinalHand() {
        var achievements = memory();
        var lost = settled(0, seat(0, 0, null), seat(80, 0, null), 4);
        achievements.recordSettled("p", "a:1", null, 1, lost);
        achievements.recordSettled("p", "b:1", "b", 5, lost);
        assertEquals(0, achievements.count("p", TOURNAMENT_FINISHED));

        achievements.recordSettled("p", "b:2", "b", 4, lost);
        assertEquals(1, achievements.count("p", TOURNAMENT_FINISHED));
        assertEquals(0, achievements.count("p", PODIUM));
        achievements.recordSettled("p", "c:2", "c", 3, lost);
        achievements.recordSettled("p", "c:2", "c", 3, lost);
        assertEquals(1, achievements.count("p", PODIUM));
        achievements.recordSettled("p", "d:3", "d", 2, lost);
        assertEquals(2, achievements.count("p", PODIUM));
        assertEquals(0, achievements.count("p", TITLE));
        assertEquals(0, achievements.count("p", POT));
    }

    static GameplayAchievements memory() { return new GameplayAchievements(new MemoryStorage()); }

    private static HandEvaluation evaluation(String cards) {
        return HandRank.evaluateBestOf(List.of(cards.split(" ")).stream().map(Card::fromLabel).toList());
    }

    private static HoldemTableViewState.Seat seat(int won, int refund, HandEvaluation evaluation) {
        return new HoldemTableViewState.Seat(0, 0, false, won + refund, won, refund, true, List.of(), evaluation);
    }

    private static HoldemTableViewState settled(int viewer, HoldemTableViewState.Seat first,
                                                HoldemTableViewState.Seat second, int seats) {
        var players = new java.util.ArrayList<>(List.of(first, second));
        players.addAll(Collections.nCopies(seats - 2, seat(0, 0, null)));
        return new HoldemTableViewState(viewer, -1, 0, 1, 2, Street.COMPLETE, 0, List.of(), List.of(), players, List.of());
    }

    private static final class MemoryStorage implements GameplayAchievements.Storage {
        private boolean failWrite;
        private final Map<String, Map<GameplayAchievements.Metric, Set<String>>> profiles = new HashMap<>();

        @Override public Map<GameplayAchievements.Metric, Set<String>> read(String profile) {
            return Map.copyOf(profiles.getOrDefault(profile, Map.of()));
        }

        @Override public void write(String profile, Map<GameplayAchievements.Metric, Set<String>> updates) {
            if (failWrite) throw new IllegalStateException("test write failure");
            profiles.computeIfAbsent(profile, ignored -> new HashMap<>()).putAll(updates);
        }
    }
}
