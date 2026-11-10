package ro.atf.holdem.ai;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.ai.ShortStackResponseModel.*;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.PublicOpponentTendency;

import static org.junit.jupiter.api.Assertions.*;

class ShortStackResponseModelPolicyTest {
    @Test void quotesPriceFromTheRespondersPotOddsAndSeparatesCoverage() {
        var hand = afterOpeningRaise();
        var before = hand.observeActor();
        assertNull(ShortStackResponseModel.quote(before, 39));
        assertEquals(Price.UNDER20, ShortStackResponseModel.quote(before, 45).cell().price());
        assertEquals(Price.FROM20_TO26, ShortStackResponseModel.quote(before, 50).cell().price());
        assertEquals(Price.FROM27_TO33, ShortStackResponseModel.quote(before, 75).cell().price());

        var covered = ShortStackResponseModel.quote(before, 160);
        assertEquals(new Cell(Coverage.COVERED, Price.AT_LEAST34, Style.UNKNOWN), covered.cell());
        assertEquals(160, covered.effectiveStack());
        hand.act(hand.actor(), ActionType.RAISE, 160);
        var responder = hand.observeActor();
        assertEquals(responder.heroStack(), responder.toCall());
        assertEquals(100L * responder.toCall() / (responder.pot() + responder.toCall()), covered.pricePercent());
    }

    @Test void derivesStyleFromTheOpponentAndLeavesSmallHistoriesUnknown() {
        var hand = new HoldemHand(1, List.of(0, 180, 600), 1, new BlindLevel(10, 20, 20),
                HoldemHand.shuffledDeck(21));
        hand.act(hand.actor(), ActionType.RAISE, 30);
        var heroHistory = new PublicOpponentTendency(2, 8, 8);
        var emptySeat = new PublicOpponentTendency(0, 8, 8);
        var unknown = hand.observeActor(List.of(heroHistory, emptySeat, new PublicOpponentTendency(1, 3, 3)));
        var restrained = hand.observeActor(List.of(heroHistory, emptySeat, new PublicOpponentTendency(1, 8, 0)));
        var pushy = hand.observeActor(List.of(heroHistory, emptySeat, new PublicOpponentTendency(1, 8, 4)));
        assertEquals(2, restrained.physicalHeroSeat());
        assertEquals(Style.UNKNOWN, ShortStackResponseModel.quote(unknown, 50).cell().style());
        assertEquals(Style.RESTRAINED, ShortStackResponseModel.quote(restrained, 50).cell().style());
        assertEquals(Style.PUSHY, ShortStackResponseModel.quote(pushy, 50).cell().style());
    }

    @Test void conditionsByPriceAndStyleAndFallsBackWithoutEvidence() {
        var restrained = new Cell(Coverage.FUNDED, Price.UNDER20, Style.RESTRAINED);
        var pushy = new Cell(Coverage.FUNDED, Price.UNDER20, Style.PUSHY);
        var unknown = new Cell(Coverage.FUNDED, Price.UNDER20, Style.UNKNOWN);
        var expensive = new Cell(Coverage.FUNDED, Price.AT_LEAST34, Style.UNKNOWN);
        var model = new ShortStackResponseModel(Map.of(restrained, new Counts(5, 20, 0),
                pushy, new Counts(2, 3, 20), unknown, new Counts(2, 10, 3),
                expensive, new Counts(22, 3, 5)), 48, 24, 24);
        var conditioned = model.estimate(pushy, Detail.PRICE_AND_STYLE);
        assertTrue(conditioned.raise() > model.estimate(restrained, Detail.PRICE_AND_STYLE).raise());
        assertTrue(model.estimate(expensive, Detail.PRICE).fold() > model.estimate(unknown, Detail.PRICE).fold());
        assertEquals(model.estimate(unknown, Detail.PRICE), model.estimate(unknown, Detail.PRICE_AND_STYLE));
        assertEquals(1.0, conditioned.fold() + conditioned.call() + conditioned.raise(), 1e-12);
        var unseen = new Cell(Coverage.FUNDED, Price.FROM20_TO26, Style.PUSHY);
        assertEquals(model.estimate(unseen, Detail.POOLED), model.estimate(unseen, Detail.PRICE_AND_STYLE));
    }

    @Test void coveredPoolCannotBorrowFundedRaiseProbability() {
        var covered = new Cell(Coverage.COVERED, Price.AT_LEAST34, Style.PUSHY);
        var funded = new Cell(Coverage.FUNDED, Price.AT_LEAST34, Style.PUSHY);
        assertThrows(IllegalArgumentException.class, () ->
                new ShortStackResponseModel(Map.of(covered, new Counts(20, 25, 1)), 48, 24, 24));
        var unsupported = new ShortStackResponseModel(Map.of(funded, new Counts(10, 30, 30),
                covered, new Counts(10, 15, 0)), 48, 24, 24);
        assertNull(unsupported.estimate(covered, Detail.PRICE_AND_STYLE));
        var supported = new ShortStackResponseModel(Map.of(funded, new Counts(10, 30, 30),
                covered, new Counts(20, 40, 0)), 48, 24, 24);
        for (Detail detail : Detail.values()) {
            var estimate = supported.estimate(covered, detail);
            assertEquals(0.0, estimate.raise());
            assertEquals(1.0, estimate.fold() + estimate.call(), 1e-12);
        }
    }

    private static HoldemHand afterOpeningRaise() {
        var hand = new HoldemHand(1, List.of(180, 600), 0, new BlindLevel(10, 20, 20), HoldemHand.shuffledDeck(21));
        hand.act(hand.actor(), ActionType.RAISE, 30);
        return hand;
    }
}
