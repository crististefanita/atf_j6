package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WagerConfirmationComponentTest {
    private static final LegalAction RAISE = new LegalAction(ActionType.RAISE, 40, 40, 100);

    @Test public void confirmsEnteredRaiseExactlyOnceAndKeepsTheLedgerAfterReplay() {
        var game = new LocalHoldemGame(2, 0, 62);
        var turn = game.turn();
        var option = game.view().legalActions().stream().filter(action -> action.type() == ActionType.RAISE)
                .findFirst().orElseThrow();
        var names = List.of("You", "Mina");
        var before = HoldemTablePresentation.actionRows(game.view(), names);
        var submissions = new ArrayList<LocalHoldemGame.Move>();
        var editor = new WagerConfirmation(option, move -> {
            submissions.add(move);
            try {
                game.play(turn, move);
                return true;
            } catch (IllegalArgumentException rejected) {
                return false;
            }
        });

        assertTrue(editor.confirm("60"));
        assertTrue("A repeated event should retain the accepted editor state", editor.confirm("80"));
        assertEquals(List.of(new LocalHoldemGame.Move(ActionType.RAISE, 60)), submissions);

        var expected = new ArrayList<>(before);
        expected.add(new HoldemTablePresentation.ActionRow(3, 0, "You", Street.PREFLOP,
                BettingAction.Kind.RAISE, 60, 70));
        assertEquals(expected, HoldemTablePresentation.actionRows(game.view(), names));
        assertEquals(expected, HoldemTablePresentation.actionRows(LocalHoldemGame.restore(game.save()).view(), names));
    }

    @Test public void rejectsInvalidInputAndIllegalSizesBeforeDispatch() {
        var submissions = new ArrayList<LocalHoldemGame.Move>();
        var editor = new WagerConfirmation(RAISE, submissions::add);

        for (String value : List.of("", "six", "60.5", "2147483648", "-1", "39", "101")) {
            assertFalse("Rejected amount: " + value, editor.confirm(value));
        }
        assertTrue(submissions.isEmpty());
        assertTrue(editor.confirm("40"));
        assertEquals(List.of(new LocalHoldemGame.Move(ActionType.RAISE, 40)), submissions);
    }

    @Test public void canRetryWithACorrectedAmountAfterSubmitRejects() {
        var submissions = new ArrayList<LocalHoldemGame.Move>();
        var editor = new WagerConfirmation(RAISE, move -> {
            submissions.add(move);
            return submissions.size() == 2;
        });

        assertFalse(editor.confirm("60"));
        assertTrue(editor.confirm("100"));
        assertEquals(List.of(new LocalHoldemGame.Move(ActionType.RAISE, 60),
                new LocalHoldemGame.Move(ActionType.RAISE, 100)), submissions);
    }

    @Test public void keepsBetAndRaiseDistinctWhenSubmittingAmounts() {
        var submissions = new ArrayList<LocalHoldemGame.Move>();
        var editor = new WagerConfirmation(new LegalAction(ActionType.BET, 20, 20, 100), submissions::add);

        assertTrue(editor.confirm("45"));
        assertEquals(List.of(new LocalHoldemGame.Move(ActionType.BET, 45)), submissions);
    }
}
