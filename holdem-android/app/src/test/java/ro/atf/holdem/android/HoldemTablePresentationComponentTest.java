package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.HandEvaluation;
import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

import java.util.List;

import static org.junit.Assert.*;

public final class HoldemTablePresentationComponentTest {
    @Test public void measuredViewportFitsCardsAtTheLargestScaleSupportedByTheBudget() {
        assertEquals(100, HoldemTablePresentation.fitCardScale(626, 570, 510));
        assertEquals(100, HoldemTablePresentation.fitCardScale(570, 570, 510));
        assertEquals(95, HoldemTablePresentation.fitCardScale(560, 570, 510));
        assertEquals(85, HoldemTablePresentation.fitCardScale(540, 570, 510));
        assertEquals(70, HoldemTablePresentation.fitCardScale(510, 570, 510));
        assertEquals(70, HoldemTablePresentation.fitCardScale(480, 570, 510));
        assertEquals(70, HoldemTablePresentation.fitCardScale(480, 550, 550));
    }

    @Test public void opponentCardsAreShownOnlyForADealtSeatAtSettlement() {
        var cards = List.of(new Card(Card.Rank.ACE, Card.Suit.SPADES),
                new Card(Card.Rank.KING, Card.Suit.HEARTS));
        var active = new HoldemTableViewState.Seat(100, 20, false, 0, true, List.of());
        var revealed = new HoldemTableViewState.Seat(100, 20, true, 0, true, cards);
        var out = new HoldemTableViewState.Seat(0, 0, false, 0, false, List.of());

        assertFalse(HoldemTablePresentation.showOpponentCards(false, false, active));
        assertFalse(HoldemTablePresentation.showOpponentCards(false, false, revealed));
        assertFalse(HoldemTablePresentation.showOpponentCards(true, true, revealed));
        assertFalse(HoldemTablePresentation.showOpponentCards(true, false, out));
        assertTrue(HoldemTablePresentation.showOpponentCards(true, false, revealed));
    }

    @Test public void seededAiNamesStayStableWithinAMatchAndVaryAcrossMatchSeeds() {
        for (int seats : List.of(2, 4, 5, 6)) {
            var first = HoldemTablePresentation.playerNames(seats, "You", 0x1a2b3c4dL);
            var replay = HoldemTablePresentation.playerNames(seats, "You", 0x1a2b3c4dL);
            var otherMatch = HoldemTablePresentation.playerNames(seats, "You", 0x5e6f7081L);
            assertEquals(seats, first.size());
            assertEquals("You", first.get(0));
            assertEquals("A resumed match keeps its aliases", first, replay);
            assertEquals(seats, first.stream().distinct().count());
            assertTrue("A new deal seed changes the randomized opponent lineup", !first.equals(otherMatch));
        }
    }

    @Test public void displayNamesAreMappedToPhysicalSeatsBeforeLeaderboardRecording() {
        var frame = frame(4, 2, false);
        var displayNames = List.of("You", "Radu", "Mara", "Nora");
        var physicalNames = HoldemTablePresentation.physicalSeatNames(frame, displayNames);

        for (int display = 0; display < displayNames.size(); display++)
            assertEquals(displayNames.get(display), physicalNames.get(frame.physicalSeat(display)));
        assertEquals(displayNames.size(), physicalNames.stream().distinct().count());
        assertThrows(IllegalArgumentException.class,
                () -> HoldemTablePresentation.physicalSeatNames(frame, List.of("You", "", "Mara", "Nora")));
    }

    @Test public void sharedActiveFrameIsSetOnlyForTheEngineActorInPhysicalSeatOrder() {
        for (int count : List.of(2, 4, 5, 6)) {
            for (int actor = -1; actor < count; actor++) {
                var frame = frame(count, actor, false);
                int active = 0;
                for (int display = 0; display < count; display++) {
                    int physicalSeat = frame.physicalSeat(display);
                    if (HoldemTablePresentation.activeSeat(frame, physicalSeat)) {
                        active++;
                        assertEquals("The shared active frame follows the engine's physical actor", actor, physicalSeat);
                    }
                }
                assertEquals("A live table highlights exactly one seat; settlement highlights none",
                        actor < 0 ? 0 : 1, active);
            }
        }
    }

    @Test public void foldedSeatKeepsADiscreetLabelAndEliminationTakesPriority() {
        assertEquals("Nora · SB · Fold", HoldemTablePresentation.seatHeading(
                "Nora", "SB", true, false, "Fold", "Out"));
        assertEquals("Nora · Out", HoldemTablePresentation.seatHeading(
                "Nora", "SB", true, true, "Fold", "Out"));
        assertEquals("Nora", HoldemTablePresentation.seatHeading(
                "Nora", "", false, false, "Fold", "Out"));
    }

    @Test public void seatFactCaptionsStaySecondaryWhileValuesRemainReadableInBothLayouts() {
        for (boolean compact : List.of(false, true)) {
            for (boolean active : List.of(false, true)) {
                var style = HoldemTablePresentation.seatFactStyle(active, compact);
                assertTrue("The numeric value must have greater text size than its caption",
                        style.labelSp() < style.valueSp());
                assertNotEquals("Caption and value must keep a visible contrast hierarchy",
                        style.labelColor(), style.valueColor());
                assertEquals(8, style.labelSp());
                assertEquals(compact ? 9 : 10, style.valueSp());
                // Card Table 0.1.7 uses android.graphics.Color.rgb, which is unavailable in this JVM suite.
                int fill = active ? 0xfff1e6c8 : 0xff1f5743;
                double captionContrast = contrast(fill, style.labelColor());
                assertTrue("Small captions meet 4.5:1 against their seat fill", captionContrast >= 4.5);
                assertTrue("The amount keeps the stronger contrast", contrast(fill, style.valueColor()) > captionContrast);
            }
        }
    }

    @Test public void onlyFactsThatExceedTheMeasuredWidthStack() {
        assertFalse(HoldemTablePresentation.stackSeatFact(100, 54, 42, 4));
        assertTrue(HoldemTablePresentation.stackSeatFact(99, 54, 42, 4));
        assertFalse(HoldemTablePresentation.stackSeatFact(100, 40, 48, 4));
        assertTrue(HoldemTablePresentation.stackSeatFact(100, 59, 48, 4));
    }

    @Test public void committedChipsAndUncalledReturnsStaySeparateFromPotAwards() {
        var seat = new HoldemTableViewState.Seat(12000, 500, false, 450, 300, 150, true, List.of(), null);
        assertEquals(List.of(metric("STACK", 12000), metric("COMMITTED", 500)),
                HoldemTablePresentation.seatMetrics(seat, false));
        assertEquals(List.of(metric("STACK", 12000), metric("WON", 300), metric("RETURNED", 150)),
                HoldemTablePresentation.seatMetrics(seat, true));
        var noReturn = new HoldemTableViewState.Seat(8000, 400, true, 0, 0, 0, true, List.of(), null);
        assertEquals(List.of(metric("STACK", 8000), metric("WON", 0)), HoldemTablePresentation.seatMetrics(noReturn, true));
    }

    private static HoldemTablePresentation.SeatMetric metric(String kind, int amount) {
        return new HoldemTablePresentation.SeatMetric(HoldemTablePresentation.SeatMetricKind.valueOf(kind), amount);
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
    }

    private static double luminance(int color) {
        return .2126 * linearChannel(color >> 16) + .7152 * linearChannel(color >> 8) + .0722 * linearChannel(color);
    }

    private static double linearChannel(int color) {
        double srgb = (color & 255) / 255.0;
        return srgb <= .04045 ? srgb / 12.92 : Math.pow((srgb + .055) / 1.055, 2.4);
    }

    @Test public void eachPhysicalSeatKeepsItsOwnLatestActionAndTheTableHistoryKeepsOrder() {
        var base = frame(4, 1, false);
        var actions = List.of(
                new BettingAction(1, Street.PREFLOP, 1, BettingAction.Kind.SMALL_BLIND, 5, 5),
                new BettingAction(2, Street.PREFLOP, 2, BettingAction.Kind.BIG_BLIND, 10, 10),
                new BettingAction(3, Street.PREFLOP, 3, BettingAction.Kind.CALL, 10, 10),
                new BettingAction(4, Street.PREFLOP, 0, BettingAction.Kind.RAISE, 20, 20),
                new BettingAction(5, Street.PREFLOP, 1, BettingAction.Kind.CALL, 15, 20),
                new BettingAction(6, Street.PREFLOP, 2, BettingAction.Kind.FOLD, 0, 10),
                new BettingAction(7, Street.FLOP, 3, BettingAction.Kind.CHECK, 0, 10),
                new BettingAction(8, Street.FLOP, 0, BettingAction.Kind.BET, 30, 50),
                new BettingAction(9, Street.TURN, 1, BettingAction.Kind.RAISE, 50, 70),
                new BettingAction(10, Street.RIVER, 3, BettingAction.Kind.ALL_IN, 90, 100));
        var seats = new java.util.ArrayList<>(base.seats());
        int[] contributions = {50, 70, 10, 100};
        for (int seat = 0; seat < seats.size(); seat++) {
            var previous = seats.get(seat);
            seats.set(seat, new HoldemTableViewState.Seat(previous.stack(), contributions[seat], previous.folded(),
                    0, true, previous.shownCards()));
        }
        var state = new HoldemTableViewState(base.viewer(), base.actor(), base.dealer(), base.smallBlind(),
                base.bigBlind(), base.street(), 230, base.ownCards(), base.board(), seats,
                base.legalActions(), actions);

        var latestBySeat = HoldemTablePresentation.latestActionsByPhysicalSeat(
                state, List.of("You", "Mina", "Ion", "Eva"));

        assertEquals(4, latestBySeat.size());
        assertEquals(BettingAction.Kind.BET, latestBySeat.get(0).kind());
        assertEquals(8, latestBySeat.get(0).ordinal());
        assertEquals("You", latestBySeat.get(0).player());
        assertEquals(BettingAction.Kind.RAISE, latestBySeat.get(1).kind());
        assertEquals(50, latestBySeat.get(1).amount());
        assertEquals(70, latestBySeat.get(1).totalContribution());
        assertEquals(BettingAction.Kind.FOLD, latestBySeat.get(2).kind());
        assertEquals(BettingAction.Kind.ALL_IN, latestBySeat.get(3).kind());
        assertEquals(10, latestBySeat.get(3).ordinal());
        assertEquals("Eva", latestBySeat.get(3).player());

        var rotated = new HoldemTableViewState(2, base.actor(), base.dealer(), base.smallBlind(), base.bigBlind(),
                base.street(), 230, base.ownCards(), base.board(), seats, base.legalActions(), actions);
        assertEquals("Mina", HoldemTablePresentation.latestActionsByPhysicalSeat(rotated,
                List.of("You", "Mina", "Ion", "Eva")).get(3).player());
        assertEquals(10, HoldemTablePresentation.actionRows(state,
                List.of("You", "Mina", "Ion", "Eva")).size());
    }

    @Test public void lastSeatActionIsAccentedAndCompactSeatFactsUseLessSpace() {
        var regular = HoldemTablePresentation.seatActionStyle(false, false, false);
        var compact = HoldemTablePresentation.seatActionStyle(true, false, false);
        var latest = HoldemTablePresentation.seatActionStyle(true, true, false);
        var active = HoldemTablePresentation.seatActionStyle(true, false, true);
        assertTrue(compact.textSp() <= regular.textSp());
        assertNotEquals("The latest move needs a distinct but restrained accent",
                compact.textColor(), latest.textColor());
        assertEquals("The active seat uses dark action text on its light frame", 0xff29362e, active.textColor());
        assertEquals(0xfff0d486, latest.textColor());
        assertEquals(9, HoldemTablePresentation.seatFactStyle(false, true).valueSp());
        assertEquals(10, HoldemTablePresentation.seatFactStyle(false, false).valueSp());
    }

    @Test public void bettingLedgerKeepsEngineOrderAndMapsPhysicalActorsToTheDisplayedNames() {
        var base = frame(4, 1, false);
        var actions = List.of(
                new BettingAction(1, Street.PREFLOP, 1, BettingAction.Kind.SMALL_BLIND, 5, 5),
                new BettingAction(2, Street.PREFLOP, 2, BettingAction.Kind.BIG_BLIND, 10, 10),
                new BettingAction(3, Street.PREFLOP, 3, BettingAction.Kind.CALL, 10, 10),
                new BettingAction(4, Street.PREFLOP, 0, BettingAction.Kind.RAISE, 20, 20),
                new BettingAction(5, Street.PREFLOP, 1, BettingAction.Kind.CALL, 15, 20),
                new BettingAction(6, Street.PREFLOP, 2, BettingAction.Kind.FOLD, 0, 10),
                new BettingAction(7, Street.FLOP, 3, BettingAction.Kind.CHECK, 0, 10),
                new BettingAction(8, Street.FLOP, 0, BettingAction.Kind.BET, 30, 50),
                new BettingAction(9, Street.TURN, 1, BettingAction.Kind.RAISE, 50, 70),
                new BettingAction(10, Street.RIVER, 3, BettingAction.Kind.ALL_IN, 90, 100));
        var seats = new java.util.ArrayList<>(base.seats());
        var seat0 = seats.get(0);
        seats.set(0, new HoldemTableViewState.Seat(seat0.stack(), 50, seat0.folded(), 0,
                true, seat0.shownCards()));
        var seat2 = seats.get(2);
        seats.set(2, new HoldemTableViewState.Seat(seat2.stack(), 10, true, 0,
                true, seat2.shownCards()));
        var seat1 = seats.get(1);
        seats.set(1, new HoldemTableViewState.Seat(seat1.stack(), 70, seat1.folded(), 0,
                true, seat1.shownCards()));
        var seat3 = seats.get(3);
        seats.set(3, new HoldemTableViewState.Seat(seat3.stack(), 100, seat3.folded(), 0,
                true, seat3.shownCards()));
        var state = new HoldemTableViewState(base.viewer(), base.actor(), base.dealer(), base.smallBlind(),
                base.bigBlind(), base.street(), 230, base.ownCards(), base.board(), seats,
                base.legalActions(), actions);

        var rows = HoldemTablePresentation.actionRows(state, List.of("You", "Mina", "Ion", "Eva"));

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), rows.stream()
                .map(HoldemTablePresentation.ActionRow::ordinal).toList());
        assertEquals(List.of("Mina", "Ion", "Eva", "You", "Mina", "Ion", "Eva", "You", "Mina", "Eva"), rows.stream()
                .map(HoldemTablePresentation.ActionRow::player).toList());
        assertEquals(List.of(BettingAction.Kind.SMALL_BLIND, BettingAction.Kind.BIG_BLIND,
                        BettingAction.Kind.CALL, BettingAction.Kind.RAISE, BettingAction.Kind.CALL,
                        BettingAction.Kind.FOLD, BettingAction.Kind.CHECK, BettingAction.Kind.BET,
                        BettingAction.Kind.RAISE, BettingAction.Kind.ALL_IN),
                rows.stream().map(HoldemTablePresentation.ActionRow::kind).toList());
        assertEquals(List.of(5, 10, 10, 20, 15, 0, 0, 30, 50, 90), rows.stream()
                .map(HoldemTablePresentation.ActionRow::amount).toList());
        assertEquals("Fold and check are explicit zero-chip decisions", List.of(0, 0), rows.stream()
                .filter(row -> row.kind() == BettingAction.Kind.FOLD || row.kind() == BettingAction.Kind.CHECK)
                .map(HoldemTablePresentation.ActionRow::amount).toList());
        assertEquals(List.of(5, 10, 10, 20, 20, 10, 10, 50, 70, 100), rows.stream()
                .map(HoldemTablePresentation.ActionRow::totalContribution).toList());
        assertEquals(List.of(Street.PREFLOP, Street.PREFLOP, Street.PREFLOP, Street.PREFLOP, Street.PREFLOP,
                        Street.PREFLOP, Street.FLOP, Street.FLOP, Street.TURN, Street.RIVER),
                rows.stream().map(HoldemTablePresentation.ActionRow::street).toList());
        var latest = HoldemTablePresentation.latestActionRow(state, List.of("You", "Mina", "Ion", "Eva"));
        assertEquals(rows.get(rows.size() - 1), latest);
        assertEquals(10, latest.ordinal());
        assertEquals("Eva", latest.player());
        assertEquals(BettingAction.Kind.ALL_IN, latest.kind());
        assertEquals(90, latest.amount());
        assertEquals(100, latest.totalContribution());
        var rotated = new HoldemTableViewState(2, base.actor(), base.dealer(), base.smallBlind(), base.bigBlind(),
                base.street(), state.pot(), base.ownCards(), base.board(), seats, base.legalActions(), actions);
        assertEquals("Mina", HoldemTablePresentation.latestActionRow(rotated,
                List.of("You", "Mina", "Ion", "Eva")).player());
        assertNull(HoldemTablePresentation.latestActionRow(base, List.of("You", "Mina", "Ion", "Eva")));
    }

    @Test public void bettingLedgerKeepsHighContrastActionRowsOnCompactAndRegularViewports() {
        for (boolean compact : List.of(true, false)) {
            var style = HoldemTablePresentation.ledgerStyle(compact);
            assertEquals(compact ? 11 : 12, style.textSp());
            assertEquals(0xfff1f5f2, style.textColor());
            assertEquals(0x5584b497, style.borderColor());
        }
    }

    @Test public void onlyCardsFromARealPotWinningShowdownHandAreMarkedAsWinning() {
        var base = frame(4, -1, true);
        var winner = base.seats().get(1);
        var bestFive = List.of(base.board().get(0), base.board().get(1), base.board().get(2),
                winner.shownCards().get(0), winner.shownCards().get(1));
        var evaluation = HandRank.evaluateBestOf(bestFive);
        var seats = new java.util.ArrayList<>(base.seats());
        seats.set(1, new HoldemTableViewState.Seat(winner.stack(), winner.contributed(), false, 50,
                50, 0, true, winner.shownCards(), evaluation));
        var refunded = seats.get(2);
        seats.set(2, new HoldemTableViewState.Seat(refunded.stack(), refunded.contributed(), false, 20,
                0, 20, true, refunded.shownCards(), evaluation));
        var state = new HoldemTableViewState(base.viewer(), base.actor(), base.dealer(), base.smallBlind(),
                base.bigBlind(), base.street(), base.pot(), base.ownCards(), base.board(), seats,
                base.legalActions(), List.of());

        assertTrue(HoldemTablePresentation.isWinningCard(state, 1, winner.shownCards().get(0)));
        assertFalse(HoldemTablePresentation.isWinningCard(state, 2, refunded.shownCards().get(0)));
        assertTrue(HoldemTablePresentation.winningCards(state).containsAll(bestFive));
        assertTrue(HoldemTablePresentation.boardCardsToHighlight(state).containsAll(bestFive));
        assertEquals("A returned unmatched contribution is not a pot win",
                5, HoldemTablePresentation.winningCards(state).size());

        var foldWinSeats = new java.util.ArrayList<>(seats);
        foldWinSeats.set(1, new HoldemTableViewState.Seat(winner.stack(), winner.contributed(), false, 50,
                50, 0, true, winner.shownCards(), null));
        var foldWin = new HoldemTableViewState(base.viewer(), base.actor(), base.dealer(), base.smallBlind(),
                base.bigBlind(), base.street(), base.pot(), base.ownCards(), List.of(), foldWinSeats,
                base.legalActions(), List.of());
        assertTrue(HoldemTablePresentation.winningCards(foldWin).isEmpty());
    }

    @Test public void terminalCardsShareOneHeightAndRefundOnlySeatsAreNotAwardTargets() {
        assertEquals(56, HoldemTablePresentation.terminalCardHeightDp(true));
        assertEquals(ro.cardtable.ui.hand.HandLayoutMetrics.cardHeightDp(),
                HoldemTablePresentation.terminalCardHeightDp(false));
        var base = frame(4, -1, true);
        var seats = new java.util.ArrayList<>(base.seats());
        var winner = seats.get(1);
        seats.set(1, new HoldemTableViewState.Seat(winner.stack(), winner.contributed(), false, 60,
                40, 20, true, winner.shownCards(), null));
        var refundOnly = seats.get(2);
        seats.set(2, new HoldemTableViewState.Seat(refundOnly.stack(), refundOnly.contributed(), false, 15,
                0, 15, true, refundOnly.shownCards(), null));
        var settled = new HoldemTableViewState(base.viewer(), base.actor(), base.dealer(), base.smallBlind(),
                base.bigBlind(), base.street(), base.pot(), base.ownCards(), base.board(), seats, List.of());

        assertEquals(List.of(new HoldemTablePresentation.PotAward(1, 40)), HoldemTablePresentation.potAwards(settled));
    }

    @Test public void awardMarkersRequireTheActualCenterInsideTheClippedSeat() {
        assertTrue(HoldemTablePresentation.containsVisibleCenter(101, 81, 50, 40, 51, 41));
        assertTrue(HoldemTablePresentation.containsVisibleCenter(100, 80, 0, 0, 100, 80));
        assertFalse(HoldemTablePresentation.containsVisibleCenter(100, 80, 0, 0, 100, 40));
        assertFalse(HoldemTablePresentation.containsVisibleCenter(100, 80, 0, 41, 100, 80));
        assertFalse(HoldemTablePresentation.containsVisibleCenter(100, 80, 51, 0, 100, 80));
        assertFalse(HoldemTablePresentation.containsVisibleCenter(100, 80, 0, 0, 0, 0));
    }

    private static HoldemTableViewState frame(int seats, int actor, boolean settled) {
        var own = List.of(new Card(Card.Rank.ACE, Card.Suit.SPADES), new Card(Card.Rank.KING, Card.Suit.HEARTS));
        var board = settled
                ? List.of(new Card(Card.Rank.TWO, Card.Suit.CLUBS), new Card(Card.Rank.THREE, Card.Suit.DIAMONDS),
                        new Card(Card.Rank.FOUR, Card.Suit.CLUBS), new Card(Card.Rank.FIVE, Card.Suit.HEARTS),
                        new Card(Card.Rank.SIX, Card.Suit.SPADES))
                : List.of(new Card(Card.Rank.TWO, Card.Suit.CLUBS), new Card(Card.Rank.THREE, Card.Suit.DIAMONDS),
                        new Card(Card.Rank.FOUR, Card.Suit.CLUBS));
        var players = new java.util.ArrayList<HoldemTableViewState.Seat>();
        for (int seat = 0; seat < seats; seat++)
            players.add(new HoldemTableViewState.Seat(1000, 20, false, 0, true,
                    settled ? List.of(new Card(Card.Rank.NINE, Card.Suit.CLUBS), new Card(Card.Rank.EIGHT, Card.Suit.CLUBS)) : List.of()));
        return new HoldemTableViewState(0, actor, 0, 1 % seats, 2 % seats,
                settled ? Street.COMPLETE : Street.FLOP, 80, own, board, players, List.of());
    }
}
