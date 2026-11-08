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
                assertEquals(compact ? 8 : 9, style.labelSp());
                assertEquals(compact ? 10 : 11, style.valueSp());
            }
        }
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

        assertEquals(List.of(1), HoldemTablePresentation.awardSeats(settled));
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
