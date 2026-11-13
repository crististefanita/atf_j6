package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HandEvaluation;
import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;
import ro.atf.holdem.core.PotAward;

import java.io.IOException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static ro.atf.holdem.android.GameplayAchievements.Metric.*;

public class GameplayAchievementsComponentTest {
    @Test public void achievementNoticePrioritizesOneEarnedAndOnePartialUpdate() {
        var catalog = java.util.Arrays.stream(GameplayAchievementCatalog.Tier.values())
                .flatMap(tier -> GameplayAchievementCatalog.forTier(tier).stream()).toList();
        var earnedMilestone = catalog.stream().filter(item -> item.target() == 1).findFirst().orElseThrow();
        var partialMilestones = catalog.stream().filter(item -> item.target() > 1).limit(2).toList();
        var partialFirst = new GameplayAchievements.Change(partialMilestones.get(0), 1);
        var earned = new GameplayAchievements.Change(earnedMilestone, earnedMilestone.target());
        var partialSecond = new GameplayAchievements.Change(partialMilestones.get(1), 1);

        var summary = GameplayAchievementNotice.summarize(List.of(partialFirst, earned, partialSecond));

        assertEquals(List.of(earned, partialFirst), summary.previews());
        assertEquals(1, summary.remaining());
        try {
            summary.previews().add(partialSecond);
            fail("Notice previews must be immutable");
        } catch (UnsupportedOperationException expected) { }
    }

    @Test public void achievementNoticeHandlesEmptyAndSingleUpdateWithoutInventingProgress() {
        assertEquals(new GameplayAchievementNotice.Summary(List.of(), 0),
                GameplayAchievementNotice.summarize(List.of()));
        var milestone = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.EASY).stream()
                .filter(item -> item.target() > 1).findFirst().orElseThrow();
        var partial = new GameplayAchievements.Change(milestone, 1);

        var summary = GameplayAchievementNotice.summarize(List.of(partial));

        assertEquals(List.of(partial), summary.previews());
        assertEquals(0, summary.remaining());
    }

    @Test public void onePokerHandAdvancesSeveralIndependentObjectivesInEitherOrder() {
        var achievements = memory();
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")),
                seat(0, 0, evaluation("Qs Jd 9h 8c 5d")), 4);
        var changes = achievements.recordSettled("p", "t:1", "t", 0, hand);
        assertTrue(changes.stream().anyMatch(change -> change.milestone().id().equals("first-pot") && change.unlocked()));
        assertTrue(changes.stream().anyMatch(change -> change.milestone().id().equals("three-pots")
                && change.count() == 1 && !change.unlocked()));
        assertTrue(achievements.recordSettled("p", "t:1", "t", 0, hand).isEmpty());

        var easy = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.EASY);
        assertEquals(POT, easy.get(0).metric());
        assertEquals(SHOWDOWN, easy.get(1).metric());
        assertEquals(1, easy.get(0).progress(achievements.counts("p")));
        assertEquals(1, easy.get(1).progress(achievements.counts("p")));
        assertEquals(0, easy.get(2).progress(achievements.counts("p")));
        assertEquals(1, easy.get(3).progress(achievements.counts("p")));
        assertEquals(0, achievements.count("p", SOLE_HIGH_CARD_POT));
        assertEquals(0, achievements.count("p", TITLE));
        assertEquals(0, achievements.count("other", POT));

        var wonByFolds = settled(0, seat(80, 20, null), seat(0, 0, null), 4);
        achievements.recordSettled("p", "t:2", "t", 0, wonByFolds);
        assertEquals(1, easy.get(1).progress(achievements.counts("p")));
        assertEquals(1, achievements.count("p", SHOWDOWN));
        assertEquals(2, easy.get(3).progress(achievements.counts("p")));
        assertEquals(0, achievements.count("p", MULTIWAY_SHOWDOWN_POT));
    }

    @Test public void rowIdsFollowCatalogKeysAcrossThresholdAndLabelChanges() {
        var first = new GameplayAchievementCatalog.Milestone("sample-pot", POT, 1, 10, false, "Rule", "Rule");
        var adjusted = new GameplayAchievementCatalog.Milestone("sample-pot", POT, 4, 20, true, "Rule {target}", "Rule {target}");
        assertEquals(first.rowId(), adjusted.rowId());

        var rowIds = new java.util.HashSet<Long>();
        for (var tier : GameplayAchievementCatalog.Tier.values()) {
            for (var milestone : GameplayAchievementCatalog.forTier(tier)) {
                assertTrue("Milestones use nonnegative row IDs", milestone.rowId() >= 0);
                assertTrue("Unique row for " + milestone.id(), rowIds.add(milestone.rowId()));
            }
        }
    }

    @Test public void titleEvidenceStopsAtTheLargestTargetAcrossTiers() {
        var medium = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.MEDIUM).get(0);
        var hard = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.HARD).get(0);
        assertEquals(TITLE, medium.metric());
        assertEquals(TITLE, hard.metric());
        assertTrue(hard.target() > medium.target());

        var storage = new MemoryStorage();
        var achievements = new GameplayAchievements(storage);
        var finalHand = settled(0, seat(80, 0, null), seat(0, 0, null), 4);
        for (int tournament = 0; tournament < hard.target() + 2; tournament++) {
            String id = "title-" + tournament;
            achievements.recordSettled("profile", id + ":1", id, 1, finalHand);
        }

        assertEquals(hard.target(), storage.read("profile").get(TITLE).size());
        assertEquals(medium.target(), medium.progress(achievements.counts("profile")));
        assertEquals(hard.target(), hard.progress(achievements.counts("profile")));
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
        assertEquals(0, achievements.count("p", SHOWDOWN));
    }

    @Test public void firstShowdownCountsParticipationEvenWhenTheViewerLosesThePot() {
        var achievements = memory();
        var loser = seat(0, 0, evaluation("As Kd Jh 8c 5d"));
        var winner = seat(60, 0, evaluation("As Ad Jh 8c 5d"));
        var showdown = settled(0, loser, winner, 4);

        achievements.recordSettled("p", "t:1", "t", 0, showdown);
        achievements.recordSettled("p", "t:1", "t", 0, showdown);

        assertEquals(1, achievements.count("p", SHOWDOWN));
        assertEquals(0, achievements.count("p", POT));
        assertEquals(1, GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.EASY).get(1)
                .progress(achievements.counts("p")));
    }

    @Test public void retiredCountersRemainReadableAndDoNotEarnNewCredit() {
        var storage = new MemoryStorage();
        storage.write("p", Map.of(SOLE_HIGH_CARD_POT, Set.of("old"), TOURNAMENT_POT, Set.of("one", "two")));
        var achievements = new GameplayAchievements(storage);
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")), seat(0, 0, null), 4);
        var changes = achievements.recordSettled("p", "t:1", "t", 0, hand);
        assertTrue(changes.stream().noneMatch(change -> change.milestone().id().equals("high-card")
                || change.milestone().id().equals("tournament-pots")));
        assertEquals(Set.of("old"), storage.read("p").get(SOLE_HIGH_CARD_POT));
        assertEquals(Set.of("one", "two"), storage.read("p").get(TOURNAMENT_POT));
    }

    @Test public void splitAndSideCreditUseAwardedLayersAndAdvanceOncePerHand() {
        var achievements = memory();
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")),
                seat(40, 0, evaluation("As Kd Jh 8c 5d")), 4);
        var unopposed = AchievementPotEvidence.from(List.of(new PotAward(30, List.of(0, 1), List.of(1)),
                new PotAward(20, List.of(0), List.of(0))), 0);
        achievements.recordSettled("p", "t:1", "t", 0, hand, unopposed);
        assertEquals(0, achievements.count("p", SIDE_POT));
        assertEquals(0, achievements.count("p", SPLIT_POT));

        var contested = AchievementPotEvidence.from(List.of(new PotAward(60, List.of(0, 1), List.of(0, 1)),
                new PotAward(30, List.of(0, 1), List.of(0, 1))), 0);
        var changes = achievements.recordSettled("p", "t:2", "t", 0, hand, contested);
        assertEquals(1, achievements.count("p", SIDE_POT));
        assertEquals(1, achievements.count("p", SPLIT_POT));
        assertEquals(List.of("split-showdown", "contested-side-pot"), changes.stream()
                .filter(change -> change.milestone().metric() == SIDE_POT || change.milestone().metric() == SPLIT_POT)
                .map(change -> change.milestone().id()).collect(java.util.stream.Collectors.toList()));
        assertTrue(achievements.recordSettled("p", "t:2", "t", 0, hand, contested).isEmpty());
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
        achievements.recordSettled("p", "t:1", "t", 0, straight);
        assertEquals(1, achievements.count("p", STRAIGHT_OR_BETTER));
        assertEquals(0, achievements.count("p", FULL_HOUSE_OR_BETTER));
        assertEquals(0, achievements.count("p", TOURNAMENT_POT));

        var fullHouse = settled(0, seat(50, 0, evaluation("As Ad Ah 5c 5d")), seat(0, 0, null), 2);
        achievements.recordSettled("p", "t:2", "t", 0, fullHouse);
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
        var medium = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.MEDIUM).get(0);
        var hard = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.HARD);
        assertEquals(1, medium.progress(restarted.counts("p")));
        assertEquals(1, hard.get(0).progress(restarted.counts("p")));
        assertEquals(1, restarted.count("p", POT));

        restarted.recordSettled("p", "b:5", "b", 1, fourSeats);
        assertEquals(2, hard.get(0).progress(restarted.counts("p")));
        assertEquals(1, hard.get(2).progress(restarted.counts("p")));
        restarted.recordSettled("p", "c:9", "c", 1, sixSeats);
        restarted.recordSettled("p", "d:3", "d", 1, sixSeats);
        assertEquals(3, hard.get(0).progress(restarted.counts("p")));
        assertEquals(2, hard.get(2).progress(restarted.counts("p")));
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

    @Test public void trainingAndItsRestoredSessionLeaveExistingProgressUntouched() {
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
        var storage = new MemoryStorage();
        storage.write("local-human", Map.of(POT, Set.of("legacy-training-evidence")));
        var achievements = new GameplayAchievements(storage);
        assertTrue(achievements.recordSettled(winner).isEmpty());
        assertTrue(achievements.recordSettled(LocalHoldemGame.restore(winner.save())).isEmpty());
        assertEquals(Set.of("legacy-training-evidence"), storage.read("local-human").get(POT));
    }

    @Test public void failedBatchCanBeRetriedWithoutPartialCredit() {
        var storage = new MemoryStorage();
        var achievements = new GameplayAchievements(storage);
        var hand = settled(0, seat(80, 0, evaluation("As Kd Jh 8c 5d")), seat(0, 0, null), 4);
        storage.failWrite = true;
        assertThrows(IllegalStateException.class, () -> achievements.recordSettled("p", "t:1", "t", 1, hand));
        assertTrue(achievements.counts("p").isEmpty());

        storage.failWrite = false;
        assertFalse(achievements.recordSettled("p", "t:1", "t", 1, hand).isEmpty());
        assertTrue(achievements.recordSettled("p", "t:1", "t", 1, hand).isEmpty());
        assertEquals(1, achievements.count("p", POT));
        assertEquals(1, achievements.count("p", TITLE));
        assertEquals(1, achievements.count("p", TOURNAMENT_FINISHED));
    }

    @Test public void notificationFollowsBothDurableWritesAndDoesNotRepeatOnResume() throws IOException {
        var game = LocalHoldemGame.tournament(4, 1, 1, "profile-a");
        playAndAdvance(game, ActionType.CALL, 20);
        for (int street = 0; street < 3; street++) playAndAdvance(game, ActionType.CHECK, 0);
        var checkpoint = new HoldemSessionStorage() {
            String saved;
            boolean fail = true;
            @Override public String read() { return saved; }
            @Override public void write(String state) throws IOException {
                if (fail) throw new IOException("checkpoint unavailable");
                saved = state;
            }
        };
        var ledger = new TournamentLeaderboardStorage() {
            @Override public String read() { return null; }
            @Override public void write(String encoded) { throw new AssertionError("Hand is not the final result"); }
        };
        var storage = new MemoryStorage();
        var sessions = new HoldemSessionRepository(checkpoint);
        var progress = new HoldemProgressStore(sessions, new TournamentLeaderboardRepository(ledger), new GameplayAchievements(storage));
        var notices = new java.util.ArrayList<List<GameplayAchievements.Change>>();
        assertThrows(IOException.class, () -> progress.checkpoint(game, false, "Profile", notices::add));
        assertTrue(storage.read("profile-a").isEmpty());
        assertTrue(notices.isEmpty());

        checkpoint.fail = false;
        storage.failWrite = true;
        assertThrows(IllegalStateException.class, () -> progress.checkpoint(game, false, "Profile", notices::add));
        assertTrue(notices.isEmpty());
        assertTrue(sessions.load().hasCheckpoint());

        storage.failWrite = false;
        progress.checkpoint(game, false, "Profile", changes -> {
            assertNotNull(checkpoint.saved);
            assertEquals(1, storage.read("profile-a").get(SHOWDOWN).size());
            notices.add(changes);
        });
        var restarted = new HoldemProgressStore(sessions, new TournamentLeaderboardRepository(ledger), new GameplayAchievements(storage));
        restarted.checkpoint(sessions.load().resumed().game(), false, "Profile", notices::add);
        assertEquals(1, notices.size());
        assertTrue(notices.get(0).stream().anyMatch(change -> change.milestone().id().equals("first-showdown")));
    }

    @Test public void allCatalogEntriesUnlockAtTheirOwnThresholdInPublishedOrder() {
        assertMilestones(GameplayAchievementCatalog.Tier.EASY, List.of(
                "first-pot:POT:1", "first-showdown:SHOWDOWN:1", "big-blind-pot:BIG_BLIND_POT:1",
                "three-pots:POT:3", "finish-tournament:TOURNAMENT_FINISHED:1",
                "button-win:BUTTON_POT:1", "small-blind-win:SMALL_BLIND_POT:1",
                "free-flop-from-big-blind:BIG_BLIND_CHECK_TO_FLOP:1", "showdown-win:SHOWDOWN_POT:1",
                "pair-showdown-win:PAIR_SHOWDOWN_POT:1", "two-pair-showdown-win:TWO_PAIR_SHOWDOWN_POT:1",
                "multiway-pot-win:MULTIWAY_POT:1", "postflop-pot-win:POSTFLOP_POT:1",
                "heads-up-pot-win:HEADS_UP_POT:1"));
        assertMilestones(GameplayAchievementCatalog.Tier.MEDIUM, List.of(
                "first-title:TITLE:1", "split-showdown:SPLIT_POT:1", "contested-side-pot:SIDE_POT:1",
                "multiway-showdowns:MULTIWAY_SHOWDOWN_POT:1",
                "straight-or-better:STRAIGHT_OR_BETTER:2", "tournament-podiums:PODIUM:2",
                "heads-up-showdown-wins:HEADS_UP_SHOWDOWN_POT:3", "side-pot-without-main-pot:SIDE_POT_ONLY:1",
                "sweep-side-pots:SWEPT_SIDE_POTS:1", "preflop-pot-win:PREFLOP_FOLD_WIN:1",
                "main-pot-knockouts:SOLE_MAIN_WIN_ELIMINATION:2", "reach-heads-up-twice:HEADS_UP_PHASE_REACHED:2",
                "four-seat-titles:FOUR_SEAT_TITLE:2", "five-seat-titles:FIVE_SEAT_TITLE:2"));
        assertMilestones(GameplayAchievementCatalog.Tier.HARD, List.of(
                "three-titles:TITLE:3", "full-house-or-better:FULL_HOUSE_OR_BETTER:2", "six-seat-titles:SIX_SEAT_TITLE:2",
                "board-play-split:BOARD_PLAYS_SPLIT:1", "short-stack-titles:SHORT_STACK_TITLE:2",
                "heads-up-comeback-title:TITLE_AFTER_HEADS_UP_DEFICIT:1",
                "title-after-last-stack:TITLE_AFTER_LAST_STACK:1", "title-before-heads-up:TITLE_WITHOUT_HEADS_UP:1",
                "hard-six-high-card-pots:HARD_SIX_HIGH_CARD_POT:4",
                "manual-hard-six-seat-titles:HARD_SIX_SEAT_MANUAL_TITLE:2",
                "titles-at-all-table-sizes:ALL_TABLE_SIZES_TITLE:3"));
    }

    @Test public void highCardAchievementNeedsASoleShowdownPotOnSixSeatHardTournament() {
        var achievements = memory();
        var highCard = evaluation("As Kd Jh 8c 5d");
        var eligibleContext = new GameplayAchievements.HandContext(6, 3, true, false, false, false,
                false, true, 0);
        var wonAlone = settled(0, seat(80, 0, highCard), seat(0, 0, null), 6);

        for (int hand = 1; hand <= 4; hand++) {
            achievements.recordSettled("p", "hard-six:" + hand, "hard-six", 0, wonAlone,
                    AchievementPotEvidence.NONE, eligibleContext);
            achievements.recordSettled("p", "hard-six:" + hand, "hard-six", 0, wonAlone,
                    AchievementPotEvidence.NONE, eligibleContext);
        }
        assertEquals("Four distinct, sole high-card awards unlock the Hard goal", 4,
                achievements.count("p", HARD_SIX_HIGH_CARD_POT));
        var goal = GameplayAchievementCatalog.forTier(GameplayAchievementCatalog.Tier.HARD).stream()
                .filter(item -> item.id().equals("hard-six-high-card-pots")).findFirst().orElseThrow();
        assertEquals(4, goal.progress(achievements.counts("p")));

        var normal = new GameplayAchievements.HandContext(6, 3, true, false, false, false,
                false, false, 0);
        achievements.recordSettled("p", "normal-six:1", "normal-six", 0, wonAlone,
                AchievementPotEvidence.NONE, normal);
        achievements.recordSettled("p", "hard-five:1", "hard-five", 0,
                settled(0, seat(80, 0, highCard), seat(0, 0, null), 5),
                AchievementPotEvidence.NONE,
                new GameplayAchievements.HandContext(5, 3, true, false, false, false, false, true, 0));
        var pair = evaluation("As Ad Ah 5c 5d");
        achievements.recordSettled("p", "hard-six-pair:1", "hard-six", 0,
                settled(0, seat(80, 0, pair), seat(0, 0, null), 6),
                AchievementPotEvidence.NONE, eligibleContext);
        var split = settled(0, seat(40, 0, highCard), seat(40, 0, highCard), 6);
        achievements.recordSettled("p", "hard-six-split:1", "hard-six", 0, split,
                AchievementPotEvidence.NONE, eligibleContext);
        assertEquals("Normal, other table sizes, pairs and split pots do not qualify", 4,
                achievements.count("p", HARD_SIX_HIGH_CARD_POT));
    }

    @Test public void preflopWinUsesTheSettledBoardNotTheTerminalStreetMarker() {
        var achievements = memory();
        var walk = settled(0, seat(80, 0, null), seat(0, 0, null), 4);
        achievements.recordSettled("p", "walk:1", "walk", 0, walk);
        assertEquals(1, achievements.count("p", PREFLOP_FOLD_WIN));

        var flop = new HoldemTableViewState(0, -1, 0, 1, 2, Street.COMPLETE, 80, List.of(),
                List.of(Card.fromLabel("2c"), Card.fromLabel("3d"), Card.fromLabel("4h")),
                List.of(seat(80, 0, evaluation("As Ad 4h 3d 2c")), seat(0, 0, null)), List.of());
        achievements.recordSettled("p", "flop:1", "flop", 0, flop);
        assertEquals("A postflop win cannot earn the preflop objective", 1,
                achievements.count("p", PREFLOP_FOLD_WIN));
    }

    @Test public void positionShowdownAndPostflopObjectivesUseTheSettledHandEvidence() {
        var achievements = memory();
        var context = new GameplayAchievements.HandContext(4, 3, true, false, false, false,
                false, false, 0);
        var board = List.of(Card.fromLabel("2c"), Card.fromLabel("3d"), Card.fromLabel("4h"));
        var pair = evaluation("As Ad 4h 3d 2c");
        var frame = new HoldemTableViewState(0, -1, 0, 1, 2, Street.COMPLETE, 80, List.of(), board,
                List.of(seat(80, 0, pair), seat(0, 0, null), seat(0, 0, null), seat(0, 0, null)), List.of());

        achievements.recordSettled("p", "position:1", "position", 0, frame,
                AchievementPotEvidence.NONE, context);
        assertEquals(1, achievements.count("p", BUTTON_POT));
        assertEquals(1, achievements.count("p", MULTIWAY_POT));
        assertEquals(1, achievements.count("p", POSTFLOP_POT));
        assertEquals(1, achievements.count("p", SHOWDOWN_POT));
        assertEquals(1, achievements.count("p", PAIR_SHOWDOWN_POT));
        assertEquals(0, achievements.count("p", TWO_PAIR_SHOWDOWN_POT));
        assertEquals(0, achievements.count("p", HEADS_UP_POT));
        assertTrue(achievements.recordSettled("p", "position:1", "position", 0, frame,
                AchievementPotEvidence.NONE, context).isEmpty());

        var smallBlind = new HoldemTableViewState(0, -1, 1, 0, 2, Street.COMPLETE, 80, List.of(), board,
                List.of(seat(80, 0, pair), seat(0, 0, null), seat(0, 0, null), seat(0, 0, null)), List.of());
        achievements.recordSettled("p", "position:2", "position", 0, smallBlind,
                AchievementPotEvidence.NONE, context);
        assertEquals(1, achievements.count("p", SMALL_BLIND_POT));

        var check = List.of(new ro.atf.holdem.core.BettingAction(1, Street.PREFLOP, 2,
                ro.atf.holdem.core.BettingAction.Kind.CHECK, 0, 0));
        var bigBlind = new HoldemTableViewState(2, -1, 0, 1, 2, Street.COMPLETE, 80, List.of(), board,
                List.of(seat(0, 0, null), seat(0, 0, null), seat(80, 0, pair), seat(0, 0, null)), List.of(), check);
        achievements.recordSettled("p", "position:3", "position", 0, bigBlind,
                AchievementPotEvidence.NONE, context);
        assertEquals(1, achievements.count("p", BIG_BLIND_CHECK_TO_FLOP));
    }

    @Test public void sidePotSplitAndKnockoutObjectivesRequireTheCorrectAwardLayer() {
        var achievements = memory();
        var board = List.of("As", "Ks", "Qs", "Js", "Ts").stream().map(Card::fromLabel).toList();
        var boardEvaluation = HandRank.evaluateBestOf(board);
        var splitFrame = new HoldemTableViewState(0, -1, 0, 1, 2, Street.COMPLETE, 90, List.of(), board,
                List.of(seat(45, 0, boardEvaluation), seat(45, 0, boardEvaluation), seat(0, 0, null)), List.of());
        var splitLayers = AchievementPotEvidence.from(List.of(new PotAward(90, List.of(0, 1), List.of(0, 1))), splitFrame);
        achievements.recordSettled("p", "split:1", "split", 0, splitFrame, splitLayers,
                new GameplayAchievements.HandContext(4, 3, true, false, false, false, false, false, 0));
        assertEquals(1, achievements.count("p", BOARD_PLAYS_SPLIT));
        assertEquals(1, achievements.count("p", SPLIT_POT));

        var sideFrame = settled(0, seat(25, 0, evaluation("As Ad Ah 5c 5d")), seat(50, 0, evaluation("Ks Kd Kh Qc Qd")), 4);
        var layers = AchievementPotEvidence.from(List.of(new PotAward(50, List.of(0, 1), List.of(1)),
                new PotAward(25, List.of(0, 1), List.of(0))), sideFrame);
        achievements.recordSettled("p", "side:1", "side", 0, sideFrame, layers,
                new GameplayAchievements.HandContext(4, 3, true, false, false, false, false, false, 0));
        assertEquals(1, achievements.count("p", SIDE_POT_ONLY));
        assertEquals(1, achievements.count("p", SIDE_POT));
        assertEquals(0, achievements.count("p", SOLE_MAIN_WIN_ELIMINATION));

        var unopposedSide = AchievementPotEvidence.from(List.of(new PotAward(50, List.of(0, 1), List.of(0)),
                new PotAward(25, List.of(0), List.of(0))), sideFrame);
        assertFalse("A single-eligible layer is not a contested side-pot achievement", unopposedSide.sideOnlyWon());
        assertFalse("Sweeping only uncontested layers does not count as a side-pot sweep", unopposedSide.sweptSidePots());

        var swept = AchievementPotEvidence.from(List.of(new PotAward(50, List.of(0, 1), List.of(0)),
                new PotAward(25, List.of(0, 1), List.of(0))), sideFrame);
        achievements.recordSettled("p", "sweep:1", "sweep", 0, sideFrame, swept,
                new GameplayAchievements.HandContext(4, 3, true, false, false, false, false, false, 1));
        assertEquals(1, achievements.count("p", SWEPT_SIDE_POTS));
        assertEquals(1, achievements.count("p", SOLE_MAIN_WIN_ELIMINATION));
    }

    @Test public void tournamentObjectivesProgressInParallelAndDeduplicateAcrossReloads() {
        var storage = new MemoryStorage();
        var achievements = new GameplayAchievements(storage);
        for (int seats : List.of(4, 5, 6, 6)) {
            var tableContext = new GameplayAchievements.HandContext(seats, 3, true, true, true, true,
                    true, true, 1);
            String id = "champion-" + seats + "-" + (achievements.count("p", TITLE) + 1);
            var finalHand = settled(0, seat(80, 0, null), seat(0, 0, null), seats);
            achievements.recordSettled("p", id + ":1", id, 1, finalHand, AchievementPotEvidence.NONE, tableContext);
            achievements = new GameplayAchievements(storage);
        }
        assertEquals(3, achievements.count("p", TITLE));
        assertEquals(1, achievements.count("p", FOUR_SEAT_TITLE));
        assertEquals(1, achievements.count("p", FIVE_SEAT_TITLE));
        assertEquals(2, achievements.count("p", SIX_SEAT_TITLE));
        assertEquals(2, achievements.count("p", HARD_SIX_SEAT_MANUAL_TITLE));
        assertEquals(3, achievements.count("p", ALL_TABLE_SIZES_TITLE));
        assertEquals(2, achievements.count("p", SHORT_STACK_TITLE));
        assertEquals(1, achievements.count("p", TITLE_AFTER_LAST_STACK));
        assertEquals(1, achievements.count("p", TITLE_AFTER_HEADS_UP_DEFICIT));
        assertEquals(1, achievements.count("p", TITLE_WITHOUT_HEADS_UP));
        assertEquals(0, achievements.count("p", HEADS_UP_PHASE_REACHED));

        var autoPlayed = new GameplayAchievements.HandContext(6, 3, true, false, false, false,
                false, true, 0);
        achievements.recordSettled("p", "auto:1", "auto", 1,
                settled(0, seat(80, 0, null), seat(0, 0, null), 6), AchievementPotEvidence.NONE, autoPlayed);
        assertEquals("Auto-play cannot qualify as a manual Hard title", 2,
                achievements.count("p", HARD_SIX_SEAT_MANUAL_TITLE));
    }

    private static void assertMilestones(GameplayAchievementCatalog.Tier tier, List<String> expected) {
        var milestones = GameplayAchievementCatalog.forTier(tier);
        var keys = milestones.stream().map(rule -> rule.id() + ":" + rule.metric() + ":" + rule.target())
                .collect(java.util.stream.Collectors.toList());
        assertEquals(expected, keys);
        for (var rule : milestones) {
            assertEquals(rule.id(), 0, rule.progress(Map.of()));
            assertEquals(rule.id(), rule.target() - 1, rule.progress(Map.of(rule.metric(), rule.target() - 1)));
            assertEquals(rule.id(), rule.target(), rule.progress(Map.of(rule.metric(), rule.target())));
            assertEquals(rule.id(), rule.target(), rule.progress(Map.of(rule.metric(), rule.target() + 1)));
            assertFalse(rule.rule(true).contains("{target}"));
            assertFalse(rule.rule(false).contains("{target}"));
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
        assertEquals(1, achievements.count("p", MULTIWAY_SHOWDOWN_POT));
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
