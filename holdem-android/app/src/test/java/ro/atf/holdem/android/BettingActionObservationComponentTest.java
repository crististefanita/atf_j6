package ro.atf.holdem.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BettingActionObservationComponentTest {
    private static final String STALE = "Ion, Dealer, Stack 1980, In pot 20, To act, "
            + "Action #4 · Ion · Call +20 · Preflop · contribution 20";
    private static final String EXPECTED = "You, Stack 1980, In pot 20, "
            + "Action #9 · You · Check +0 · Flop · contribution 20";
    private static final String NEWER_OPPONENT = "Action #10 · Ion · Check +0 · Flop · contribution 20";
    private static final BettingActionObservation.Expectation CHECK =
            new BettingActionObservation.Expectation(9, "You", "Check", 0, 20);

    @Test public void waitsPastThePreviousAccessibleNodeForTheExactHumanCheck() {
        var frames = new Frames(List.of(List.of(STALE), List.of(STALE, EXPECTED, NEWER_OPPONENT)));
        var result = frames.await(240, CHECK);

        assertNotNull(result.diagnostic(), result.matched());
        assertEquals(9, result.matched().ordinal());
        assertEquals(EXPECTED, result.diagnostic());
        assertEquals(2, frames.reads);
    }

    @Test public void timeoutKeepsTheLastOrdinalAndDescriptionThroughAnEmptyFrame() {
        String wrongContribution = "Action #9 · You · Check +0 · Flop · contribution 200";
        var frames = new Frames(List.of(List.of(STALE), List.of(wrongContribution), List.of()));
        var result = frames.await(125, CHECK);

        assertNull(result.matched());
        assertEquals(125L, frames.uptime);
        assertEquals(3, frames.reads);
        assertEquals("last ordinal #9 · " + wrongContribution, result.diagnostic());
    }

    @Test public void ignoresChecksFromTheWrongPlayerOrdinalOrAmount() {
        var frames = new Frames(List.of(
                List.of("Action #9 · Ion · Check +0 · Flop · contribution 20"),
                List.of("Action #8 · You · Check +0 · Flop · contribution 20",
                        "Action #10 · You · Check +0 · Turn · contribution 20"),
                List.of("Action #9 · You · Check +10 · Flop · contribution 20"),
                List.of(EXPECTED)));

        assertEquals(EXPECTED, frames.await(240, CHECK).diagnostic());
        assertEquals(4, frames.reads);
    }

    @Test public void latestModeDoesNotUseAnOlderMatchingEntry() {
        var frames = new Frames(List.of(List.of(EXPECTED,
                "Action #10 · Ion · Raise +40 · Turn · contribution 60"), List.of(NEWER_OPPONENT)));
        var latestCheck = new BettingActionObservation.Expectation(0, null, "Check", 0, 20);

        assertEquals(NEWER_OPPONENT, frames.await(100, latestCheck).diagnostic());
        assertEquals(2, frames.reads);
    }

    @Test public void reportsUnparseableAccessibilityTextWhenNoOrdinalWasAvailable() {
        String malformed = "Action #not-a-number · You · Check +0 · Flop · contribution 20";
        var frames = new Frames(List.of(List.of(malformed), List.of()));

        var result = frames.await(40, CHECK);

        assertNull(result.matched());
        assertTrue(result.diagnostic().contains("last ordinal unavailable"));
        assertTrue(result.diagnostic().contains(malformed));
    }

    @Test public void rejectsAnExactMatchReadAfterTheDeadline() {
        var frames = new Frames(List.of(List.of(STALE)));
        var result = BettingActionObservation.await(() -> {
            frames.uptime += 126;
            return List.of(EXPECTED);
        }, () -> frames.uptime, millis -> frames.uptime += millis, 125, CHECK);

        assertNull(result.matched());
        assertEquals("last ordinal #9 · " + EXPECTED, result.diagnostic());
    }

    private static final class Frames {
        private final List<List<String>> snapshots;
        private int reads;
        private long uptime;

        Frames(List<List<String>> snapshots) { this.snapshots = snapshots; }

        BettingActionObservation.Result await(long timeout, BettingActionObservation.Expectation expected) {
            return BettingActionObservation.await(
                    () -> snapshots.get(Math.min(reads++, snapshots.size() - 1)),
                    () -> uptime, millis -> uptime += millis, timeout, expected);
        }
    }
}
