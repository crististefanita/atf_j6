package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Street;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static ro.atf.holdem.core.ActionType.*;

public class LocalHoldemTournamentTest {
    @Test public void successiveAllInsEliminateSeatsAndEndWithTheHumanChampion() {
        var game = LocalHoldemGame.tournament(4, 1);
        play(game, CALL);
        assertEquals(Street.FLOP, game.view().street());
        play(game, CHECK);
        play(game, CHECK);
        play(game, CHECK);
        assertEquals(List.of(1980, 1980, 2060, 1980), stacks(game));
        assertEquals(80, totalPayout(game));
        assertNull(game.outcome());

        game.nextHand();
        assertEquals(1, game.view().dealer());
        playAllIn(game, 1960);
        assertEquals(7920, totalPayout(game));
        assertEquals(List.of(0, 0, 80, 7920), stacks(game));
        assertTrue(game.eliminated(0));
        assertTrue(game.eliminated(1));
        assertFalse(game.eliminated(2));
        assertNull(game.outcome());

        game.nextHand();
        advancePassiveTurns(game);
        var headsUp = game.view();
        assertEquals(3, game.handNumber());
        assertEquals(3, headsUp.viewer());
        assertEquals(3, headsUp.physicalSeat(0));
        assertEquals(3, headsUp.dealer());
        assertEquals(3, headsUp.smallBlind());
        assertEquals(2, headsUp.bigBlind());
        assertEquals(3, headsUp.actor());
        assertEquals("Two eliminations advance the tournament by two blind levels", 3, game.blindLevelNumber());
        assertEquals(20, game.blindLevel().smallBlind());
        assertEquals(40, game.blindLevel().bigBlind());
        assertEquals(List.of(0, 0, 40, 20), headsUp.seats().stream().map(seat -> seat.contributed()).collect(Collectors.toList()));
        playAllIn(game, 7900);
        assertEquals(List.of(0, 0, 0, 8000), stacks(game));
        assertEquals(8000, totalPayout(game));
        assertEquals(5, game.view().board().size());
        assertEquals(2, game.view().ownCards().size());
        assertEquals(LocalHoldemGame.Outcome.WON, game.outcome());
        assertThrows(IllegalArgumentException.class, game::nextHand);
    }

    @Test public void losingAllInStopsTheSessionWithoutDiscardingTheFinalBoard() {
        var game = LocalHoldemGame.tournament(4, 1);
        play(game, ALL_IN);
        assertEquals(LocalHoldemGame.Outcome.LOST, game.outcome());
        assertEquals(List.of(0, 0, 8000, 0), stacks(game));
        assertTrue(game.eliminated(game.view().viewer()));
        assertEquals(5, game.view().board().size());
        assertTrue(game.view().legalActions().isEmpty());
        assertThrows(IllegalArgumentException.class, game::nextHand);
        var restored = LocalHoldemGame.restore(game.save());
        assertEquals(game.view(), restored.view());
        assertEquals(LocalHoldemGame.Outcome.LOST, restored.outcome());
    }

    @Test public void restorationReconstructsPastRoundsAndRejectsAStaleTurn() {
        var game = LocalHoldemGame.tournament(4, 1);
        var beforeFirstAction = game.save();
        play(game, CALL);
        play(game, CHECK);
        play(game, CHECK);
        play(game, CHECK);
        var previousHand = game.turn();
        game.nextHand();
        play(game, ALL_IN);
        game.nextHand();
        var restored = LocalHoldemGame.restore(game.save());
        assertEquals(3, restored.handNumber());
        assertEquals(game.view(), restored.view());
        assertTrue(restored.eliminated(0));
        assertTrue(restored.eliminated(1));
        assertTrue(beforeFirstAction.rounds().get(0).isEmpty());
        var stale = new LocalHoldemGame.Move(FOLD, 0);
        assertThrows(IllegalArgumentException.class, () -> game.play(previousHand, stale));
        assertThrows(IllegalArgumentException.class, () -> restored.play(game.turn(), stale));
        play(game, ALL_IN);
        play(restored, ALL_IN);
        assertEquals(game.view(), restored.view());
        assertEquals(LocalHoldemGame.Outcome.WON, LocalHoldemGame.restore(restored.save()).outcome());
    }

    @Test public void fundedSeatsAndChipsArePreservedAcrossOrdinaryHandsForEveryTournamentSize() {
        for (int seats : LocalHoldemGame.TOURNAMENT_SIZES) {
            var game = LocalHoldemGame.tournament(seats, 33);
            int human = game.view().viewer();
            assertThrows(IllegalArgumentException.class, game::nextHand);
            for (int round = 1; round <= 4; round++) {
                assertEquals(round, game.handNumber());
                assertEquals(human, game.view().physicalSeat(0));
                assertEquals((round - 1) % seats, game.view().dealer());
                while (game.view().street() != Street.COMPLETE) {
                if (game.hasPassiveTurn()) {
                    game.advancePassiveAction(game.turn());
                    continue;
                }
                    boolean canCheck = game.view().legalActions().stream().anyMatch(option -> option.type() == CHECK);
                    play(game, canCheck ? CHECK : CALL);
                }
                assertEquals(seats * 2000, stacks(game).stream().mapToInt(Integer::intValue).sum());
                assertTrue(game.view().legalActions().isEmpty());
                for (int seat = 0; seat < seats; seat++) assertFalse(game.eliminated(seat));
                assertNull(game.outcome());
                game.nextHand();
            }
        }
    }

    @Test public void trainingSettlementDoesNotAcquireTournamentOutcome() {
        var training = new LocalHoldemGame(2, 1);
        play(training, ALL_IN);
        assertNull(training.outcome());
        assertFalse(training.eliminated(0));
        assertFalse(training.eliminated(1));
        assertThrows(IllegalArgumentException.class, training::nextHand);
        assertThrows(IllegalArgumentException.class, () -> LocalHoldemGame.tournament(2, 1));
    }

    @Test public void tournamentBlindIntervalIsConfigurableAndRestoredWithTheHandHistory() {
        var oneMinute = LocalHoldemGame.tournament(4, 87, 1);
        assertEquals(1, oneMinute.blindLevelMinutes());
        assertEquals(1, oneMinute.blindLevelNumber());
        assertEquals(10, oneMinute.blindLevel().smallBlind());
        assertEquals(20, oneMinute.blindLevel().bigBlind());
        assertEquals(20, oneMinute.blindLevel().minimumOpen());
        settle(oneMinute);
        oneMinute.nextHandAtElapsedSeconds(60);
        assertEquals(2, oneMinute.blindLevelNumber());
        assertEquals(15, oneMinute.blindLevel().smallBlind());
        assertEquals(30, oneMinute.blindLevel().bigBlind());
        assertEquals(30, oneMinute.blindLevel().minimumOpen());
        settle(oneMinute);
        oneMinute.nextHandAtElapsedSeconds(120);
        assertEquals(3, oneMinute.blindLevelNumber());
        assertEquals(50, oneMinute.blindLevel().minimumOpen());

        var restored = LocalHoldemGame.restore(oneMinute.save());
        assertEquals(1, restored.blindLevelMinutes());
        assertEquals(3, restored.blindLevelNumber());
        assertEquals(oneMinute.view(), restored.view());

        var twoMinutes = LocalHoldemGame.tournament(4, 87, 2);
        assertEquals(2, twoMinutes.blindLevelMinutes());
        settle(twoMinutes);
        twoMinutes.nextHandAtElapsedSeconds(119);
        assertEquals(1, twoMinutes.blindLevelNumber());
        settle(twoMinutes);
        twoMinutes.nextHandAtElapsedSeconds(120);
        assertEquals(2, twoMinutes.blindLevelNumber());
        assertThrows(IllegalArgumentException.class, () -> LocalHoldemGame.tournament(4, 87, 4));
    }

    @Test public void tournamentClockPausesInBackgroundAndResumesFromTheSavedElapsedTime() {
        var monotonicNanos = new AtomicLong();
        var game = LocalHoldemGame.tournament(4, 9901, 1, monotonicNanos::get);
        monotonicNanos.set(65_000_000_000L);
        assertEquals(65, game.elapsedTournamentSeconds());

        game.pauseClock();
        monotonicNanos.addAndGet(600_000_000_000L);
        assertEquals("Background time must not consume a blind level", 65, game.elapsedTournamentSeconds());
        var saved = game.save();
        assertEquals(65, saved.elapsedSeconds());

        var restored = LocalHoldemGame.restore(saved, monotonicNanos::get);
        assertEquals("Restoration anchors the clock at its persisted elapsed duration",
                65, restored.elapsedTournamentSeconds());
        monotonicNanos.addAndGet(30_000_000_000L);
        assertEquals(95, restored.elapsedTournamentSeconds());

        restored.pauseClock();
        monotonicNanos.addAndGet(300_000_000_000L);
        assertEquals(95, restored.elapsedTournamentSeconds());
        restored.resumeClock();
        monotonicNanos.addAndGet(25_000_000_000L);
        assertEquals(120, restored.elapsedTournamentSeconds());
        settle(restored);
        restored.nextHand();
        assertEquals("The first hand after resume uses the elapsed tournament level", 3, restored.blindLevelNumber());
    }

    private static List<Integer> stacks(LocalHoldemGame game) {
        return game.view().seats().stream().map(seat -> seat.stack()).collect(Collectors.toList());
    }

    private static int totalPayout(LocalHoldemGame game) {
        return game.view().seats().stream().mapToInt(seat -> seat.received()).sum();
    }

    private static void playAllIn(LocalHoldemGame game, int expectedAmount) {
        advancePassiveTurns(game);
        var option = game.view().legalActions().stream().filter(action -> action.type() == ALL_IN).findFirst().orElseThrow();
        assertEquals(expectedAmount, option.amount());
        play(game, ALL_IN);
    }

    private static void play(LocalHoldemGame game, ActionType action) {
        advancePassiveTurns(game);
        if (game.view().street() == Street.COMPLETE) return;
        var legal = game.view().legalActions().stream().filter(option -> option.type() == action).findFirst().orElseThrow();
        game.play(game.turn(), new LocalHoldemGame.Move(action, legal.amount()));
        advancePassiveTurns(game);
    }

    private static void advancePassiveTurns(LocalHoldemGame game) {
        int actions = 0;
        while (game.hasPassiveTurn()) {
            if (++actions > 64) throw new AssertionError("Passive action bound exceeded");
            game.advancePassiveAction(game.turn());
        }
    }

    private static void settle(LocalHoldemGame game) {
        int steps = 0;
        while (game.view().street() != Street.COMPLETE) {
            if (++steps > 64) throw new AssertionError("Hand did not settle within the action bound");
            if (game.hasPassiveTurn()) {
                game.advancePassiveAction(game.turn());
                continue;
            }
            var legal = game.view().legalActions();
            var action = legal.stream().filter(option -> option.type() == CHECK).findFirst()
                    .orElseGet(() -> legal.stream().filter(option -> option.type() == CALL).findFirst()
                            .orElseThrow(AssertionError::new));
            game.play(game.turn(), new LocalHoldemGame.Move(action.type(), action.amount()));
        }
    }
}
