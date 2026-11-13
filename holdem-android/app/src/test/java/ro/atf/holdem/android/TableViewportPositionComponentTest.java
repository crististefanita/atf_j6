package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Street;

import static org.junit.Assert.*;

public class TableViewportPositionComponentTest {
    @Test public void checkRestoresFocusToViewerAndCarriesBothScrollPositions() {
        var game = readyToCheck();
        var before = game.view();
        game.play(game.turn(), new LocalHoldemGame.Move(ActionType.CHECK, 0));
        var after = game.view();

        assertTrue(TableViewportPosition.sameOpenHand(game, game.handNumber(), before, game, after));
        var restored = TableViewportPosition.capture(27, true, 41, "CHECK", before, after);
        assertEquals(new TableViewportPosition(27, true, 41, "settlement-target:seat:" + after.viewer()), restored);
        assertTrue(TableViewportPosition.canRestoreFocus(restored.focusTag()));
    }

    @Test public void sameHandRetainsLedgerFocusButIgnoresObsoleteActionButtons() {
        var frame = readyToCheck().view();
        String row = "betting-action:1";
        var restored = TableViewportPosition.capture(5, true, 10, row, frame, frame);
        assertEquals(row, restored.focusTag());
        assertTrue(TableViewportPosition.canRestoreFocus(row));
        assertTrue(TableViewportPosition.canRestoreFocus("betting-history-toggle"));
        assertFalse(TableViewportPosition.canRestoreFocus("CHECK"));
        assertFalse(TableViewportPosition.canRestoreFocus(null));
    }

    @Test public void replacedAndCompletedHandsDoNotInheritScrollState() {
        var game = readyToCheck();
        var before = game.view();
        var other = readyToCheck();
        assertFalse(TableViewportPosition.sameOpenHand(game, game.handNumber(), before, other, other.view()));
        assertFalse(TableViewportPosition.sameOpenHand(game, game.handNumber() - 1, before, game, before));

        for (int action = 0; action < 8 && game.view().street() != Street.COMPLETE; action++) {
            if (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
            else game.play(game.turn(), new LocalHoldemGame.Move(ActionType.CHECK, 0));
        }
        var complete = game.view();
        assertEquals(Street.COMPLETE, complete.street());
        assertFalse(TableViewportPosition.sameOpenHand(game, game.handNumber(), before, game, complete));
    }

    private static LocalHoldemGame readyToCheck() {
        var game = new LocalHoldemGame(2, 0, 62);
        var call = game.view().legalActions().stream().filter(action -> action.type() == ActionType.CALL)
                .findFirst().orElseThrow();
        game.play(game.turn(), new LocalHoldemGame.Move(call.type(), call.amount()));
        game.advancePassiveAction(game.turn());
        game.advancePassiveAction(game.turn());
        assertTrue(game.view().legalActions().stream().anyMatch(action -> action.type() == ActionType.CHECK));
        return game;
    }
}
