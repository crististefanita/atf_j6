package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Street;

import java.util.List;
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
        assertNull(game.outcome());

        game.nextHand();
        assertEquals(1, game.view().dealer());
        play(game, ALL_IN);
        assertEquals(List.of(0, 0, 80, 7920), stacks(game));
        assertTrue(game.eliminated(0));
        assertTrue(game.eliminated(1));
        assertFalse(game.eliminated(2));
        assertNull(game.outcome());

        game.nextHand();
        var headsUp = game.view();
        assertEquals(3, game.handNumber());
        assertEquals(3, headsUp.viewer());
        assertEquals(3, headsUp.physicalSeat(0));
        assertEquals(3, headsUp.dealer());
        assertEquals(3, headsUp.smallBlind());
        assertEquals(2, headsUp.bigBlind());
        assertEquals(3, headsUp.actor());
        assertEquals(List.of(0, 0, 20, 10), headsUp.seats().stream().map(seat -> seat.contributed()).collect(Collectors.toList()));
        play(game, ALL_IN);
        assertEquals(List.of(0, 0, 0, 8000), stacks(game));
        assertEquals(8000, game.view().seats().stream().mapToInt(seat -> seat.received()).sum());
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
        var beforeFold = game.save();
        play(game, FOLD);
        var previousHand = game.turn();
        game.nextHand();
        play(game, ALL_IN);
        game.nextHand();
        var restored = LocalHoldemGame.restore(game.save());
        assertEquals(3, restored.handNumber());
        assertEquals(game.view(), restored.view());
        assertTrue(restored.eliminated(0));
        assertTrue(restored.eliminated(1));
        assertTrue(beforeFold.rounds().get(0).isEmpty());
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

    private static List<Integer> stacks(LocalHoldemGame game) {
        return game.view().seats().stream().map(seat -> seat.stack()).collect(Collectors.toList());
    }

    private static void play(LocalHoldemGame game, ActionType action) {
        var legal = game.view().legalActions().stream().filter(option -> option.type() == action).findFirst().orElseThrow();
        game.play(game.turn(), new LocalHoldemGame.Move(action, legal.amount()));
    }
}
