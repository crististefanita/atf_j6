package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.Street;
import ro.cardtable.names.PlayerNames;
import ro.cardtable.ui.cards.CardFace;
import ro.cardtable.ui.table.TableGeometry;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

public class LocalHoldemGameTest {
    @Test public void raiseAndChecksReachSettlementWithoutChangingTheViewersCards() {
        for (int seats : LocalHoldemGame.TABLE_SIZES) {
            var game = new LocalHoldemGame(seats, 62);
            var preflop = game.view();
            assertEquals(30, preflop.pot());
            assertEquals(0, preflop.board().size());
            assertEquals(preflop.viewer(), preflop.physicalSeat(0));
            assertEquals(2, preflop.ownCards().size());
            int expectedPot = seats == 2 ? 140 : seats * 60;
            play(game, ActionType.RAISE, 60);
            assertEquals(Street.FLOP, game.view().street());
            assertEquals(expectedPot, game.view().pot());
            assertEquals(3, game.view().board().size());
            play(game, ActionType.CHECK, 0);
            assertEquals(4, game.view().board().size());
            play(game, ActionType.CHECK, 0);
            var river = game.view();
            assertEquals(5, river.board().size());
            play(game, ActionType.CHECK, 0);
            var result = game.view();
            assertEquals(Street.COMPLETE, result.street());
            assertEquals(river.board(), result.board());
            assertEquals(preflop.ownCards(), result.ownCards());
            assertEquals(0, preflop.board().size());
            assertTrue(result.legalActions().isEmpty());
            assertEquals(-1, result.actor());
            assertEquals(expectedPot, result.seats().stream().mapToInt(seat -> seat.received()).sum());
            assertEquals(seats * 2000, result.seats().stream().mapToInt(seat -> seat.stack()).sum());
        }
    }

    @Test public void headsUpKeepsTheHumanSeatWhenTheDealerChanges() {
        var first = new LocalHoldemGame(2, 0, 14);
        assertEquals(0, first.view().viewer());
        assertEquals(0, first.view().dealer());
        assertEquals(0, first.view().smallBlind());
        assertEquals(1, first.view().bigBlind());
        assertEquals(30, first.view().pot());
        play(first, ActionType.FOLD, 0);
        var next = new LocalHoldemGame(2, first.view().bigBlind(), 21);
        var opening = next.view();
        assertEquals(0, opening.physicalSeat(0));
        assertEquals(1, opening.dealer());
        assertEquals(1, opening.smallBlind());
        assertEquals(0, opening.bigBlind());
        assertEquals(0, opening.actor());
        assertEquals(40, opening.pot());
        assertEquals(List.of(ActionType.CHECK, ActionType.RAISE, ActionType.ALL_IN),
                opening.legalActions().stream().map(action -> action.type()).collect(java.util.stream.Collectors.toList()));
        assertEquals(opening, LocalHoldemGame.restore(next.save()).view());
        play(next, ActionType.CHECK, 0);
        assertEquals(Street.FLOP, next.view().street());
        assertEquals(0, next.view().actor());
    }

    @Test public void compactRowsKeepTheHumanBelowTheBoardAndAllSixSeatsDistinct() {
        var headsUp = CompactSeatRows.forSeats(2);
        assertArrayEquals(new int[]{1}, headsUp.top());
        assertArrayEquals(new int[]{0}, headsUp.bottom());
        var four = CompactSeatRows.forSeats(4);
        assertArrayEquals(new int[]{1, 2, 3}, four.top());
        assertArrayEquals(new int[]{-1, 0, -1}, four.bottom());
        var five = CompactSeatRows.forSeats(5);
        assertArrayEquals(new int[]{2, -1, 3}, five.top());
        assertArrayEquals(new int[]{1, 0, 4}, five.bottom());
        var six = CompactSeatRows.forSeats(6);
        assertArrayEquals(new int[]{2, 3, 4}, six.top());
        assertArrayEquals(new int[]{1, 0, 5}, six.bottom());
    }

    @Test public void engineRejectsOutOfRangeAndSessionRejectsRepeatedOrForeignTurns() {
        var game = new LocalHoldemGame(4, 62);
        var turn = game.turn();
        var initial = game.view();
        var raise = initial.legalActions().stream().filter(action -> action.type() == ActionType.RAISE)
                .findFirst().orElseThrow(AssertionError::new);
        assertEquals(40, raise.minAmount());
        assertEquals(2000, raise.maxAmount());
        assertThrows(IllegalArgumentException.class, () -> play(game, ActionType.RAISE, 39));
        assertThrows(IllegalArgumentException.class, () -> play(game, ActionType.RAISE, 2001));
        assertThrows(IllegalArgumentException.class, () -> play(game, ActionType.CHECK, 0));
        assertThrows(IllegalArgumentException.class,
                () -> game.play(new LocalHoldemGame(4, 62).turn(), new LocalHoldemGame.Move(ActionType.CALL, 20)));
        assertEquals(initial, game.view());
        assertEquals(turn, game.turn());
        play(game, ActionType.CALL, 20);
        var afterCall = game.view();
        assertThrows(IllegalArgumentException.class, () -> game.play(turn, new LocalHoldemGame.Move(ActionType.CHECK, 0)));
        assertEquals(afterCall, game.view());
    }

    @Test public void foldAndAllInSettleWithoutAnotherHumanCommand() {
        var fold = new LocalHoldemGame(4, 11);
        play(fold, ActionType.FOLD, 0);
        assertEquals(Street.COMPLETE, fold.view().street());
        assertTrue(fold.view().seats().get(fold.view().viewer()).folded());
        assertEquals(2000, fold.view().seats().get(fold.view().viewer()).stack());
        var allIn = new LocalHoldemGame(6, 11);
        play(allIn, ActionType.ALL_IN, 2000);
        assertEquals(Street.COMPLETE, allIn.view().street());
        assertEquals(12000, allIn.view().seats().stream().mapToInt(seat -> seat.received()).sum());
    }

    @Test public void recreationReplaysOnlyAcceptedHumanMovesAndRejectsTheOldToken() {
        var original = new LocalHoldemGame(5, 9);
        play(original, ActionType.CALL, 20);
        play(original, ActionType.BET, 40);
        var restored = LocalHoldemGame.restore(original.save());
        assertEquals(original.view(), restored.view());
        assertThrows(IllegalArgumentException.class,
                () -> restored.play(original.turn(), new LocalHoldemGame.Move(ActionType.CHECK, 0)));
        play(restored, ActionType.CHECK, 0);
        assertEquals(Street.RIVER, restored.view().street());
    }

    @Test public void cardFacesAndCommonGeometryStayCompatible() {
        assertEquals(new CardFace("10", "♥", true), HoldemTableView.face(new Card(Card.Rank.TEN, Card.Suit.HEARTS)));
        assertEquals(6, new HashSet<>(PlayerNames.forMatch(6, "Ana", 6)).size());
        for (int seats : new int[]{4, 5, 6}) {
            for (int width : new int[]{240, 360, 540}) {
                var layout = TableGeometry.arrangeDisplay(width, 420, seats, 68, 68, width, 84, 4, true,
                        TableGeometry.CenterAlignment.BALANCED);
                assertEquals(seats, layout.seats().size());
                for (var seat : layout.seats()) assertFalse(seat.overlaps(layout.center()));
            }
        }
    }

    private static void play(LocalHoldemGame game, ActionType action, int amount) {
        game.play(game.turn(), new LocalHoldemGame.Move(action, amount));
    }
}
