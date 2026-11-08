package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;
import ro.cardtable.ui.animation.TrickCollectionMotion;

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

    @Test public void collectionUsesOnlyCardsTheCurrentViewerMaySeeAndIncludesShowdownCards() {
        var flop = frame(4, 2, false);
        assertEquals(List.of("hand-card:" + flop.ownCards().get(0).label(),
                "hand-card:" + flop.ownCards().get(1).label(),
                "settlement-card:board:0", "settlement-card:board:1", "settlement-card:board:2"),
                HoldemTablePresentation.collectionTags(flop));

        var showdown = frame(4, -1, true);
        var tags = HoldemTablePresentation.collectionTags(showdown);
        assertEquals("Two private cards, five community cards and three opponents' exposed pairs", 13, tags.size());
        assertTrue(tags.contains("settlement-card:seat:1:0"));
        assertTrue(tags.contains("settlement-card:seat:3:1"));
        assertEquals("Only the visible, distinct card faces can be collected", tags.size(), tags.stream().distinct().count());
    }

    @Test public void finalCollectionUsesTheSharedWindowAndEveryCardConvergesOnThePot() {
        var timing = TrickCollectionMotion.timing(TrickCollectionMotion.DISPLAY_MILLIS, 0);
        assertEquals("The cards remain visible for the established common window", 1000L, timing.delayMillis());
        assertEquals("The shared motion reserves its final half-second for collection",
                TrickCollectionMotion.COLLECT_MILLIS, timing.durationMillis());
        var target = new TrickCollectionMotion.Point(420, 280);
        for (var source : List.of(new TrickCollectionMotion.Point(12, 24),
                new TrickCollectionMotion.Point(150, 180), new TrickCollectionMotion.Point(700, 500))) {
            var finalFrame = TrickCollectionMotion.frame(source, target, 1f);
            assertEquals("Every card reaches the pot's horizontal center", target.x(), source.x() + finalFrame.translationX(), .001f);
            assertEquals("Every card reaches the pot's vertical center", target.y(), source.y() + finalFrame.translationY(), .001f);
            assertEquals("The collected card fades out", 0f, finalFrame.alpha(), 0f);
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
