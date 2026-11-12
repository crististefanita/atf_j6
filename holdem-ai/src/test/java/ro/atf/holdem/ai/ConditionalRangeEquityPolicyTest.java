package ro.atf.holdem.ai;

import atf.testkit.AtfComponentTest;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.ai.ConditionalRangeEquity.Bucket;
import ro.atf.holdem.ai.ConditionalRangeEquity.Counts;
import ro.atf.holdem.ai.ConditionalRangeEquity.Estimate;
import ro.atf.holdem.ai.ConditionalRangeEquity.Response;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemHand;

import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ConditionalRangeEquityPolicyTest {
    @Test void partitionsTheFullDeckAndRecoversUniformEquityWithProportionalWeights() {
        var deck = HoldemHand.shuffledDeck(1);
        var population = new EnumMap<Bucket, Integer>(Bucket.class);
        for (int i = 0; i < deck.size(); i++)
            for (int j = i + 1; j < deck.size(); j++)
                population.merge(Bucket.of(deck.get(i), deck.get(j)), 1, Integer::sum);
        assertEquals(Map.of(Bucket.PREMIUM, 62, Bucket.SMALL_PAIR, 48, Bucket.BROADWAY, 128, Bucket.OTHER, 1088),
                population);
        var weights = new EnumMap<Bucket, Counts>(Bucket.class);
        population.forEach((bucket, size) -> weights.put(bucket, new Counts(0, size, 0)));
        var estimate = new ConditionalRangeEquity(128, 61483).estimate(cards("As", "Jh"), weights);
        assertEquals(1, estimate.call().probability());
        assertEquals(estimate.uniformEquity(), estimate.call().equity(), 1e-12);
        assertEquals(0, estimate.raise().probability());
        assertTrue(Double.isNaN(estimate.raise().equity()));
    }

    @Test void responseRangesUseTheSameBoardsAndCardRemoval() {
        var estimator = new ConditionalRangeEquity(256, 61483);
        var weights = weights(new Counts(0, 0, 62), new Counts(0, 1088, 0));
        var normal = estimator.estimate(cards("As", "Jh"), weights);
        var equivalent = estimator.estimate(cards("Jc", "Ah"), weights);
        assertEquals(normal, equivalent);
        assertTrue(normal.call().equity() - normal.raise().equity() > .15);

        var switched = estimator.estimate(cards("As", "Jh"),
                weights(new Counts(0, 62, 0), new Counts(0, 0, 1088)));
        assertEquals(normal.call(), switched.raise());
        assertEquals(normal.raise(), switched.call());
        var aces = estimator.estimate(cards("As", "Ad"), weights);
        var low = estimator.estimate(cards("7s", "2h"), weights);
        assertTrue(aces.raise().probability() < low.raise().probability());
        assertEquals(1.0, normal.foldProbability() + normal.call().probability() + normal.raise().probability(), 1e-12);
    }

    @Test void valuesCallAndReraiseAtTheirOwnEquityAndPreservesTheFoldOption() {
        var spot = new PreflopResponseModel.Spot(PreflopResponseModel.Sequence.RERAISE, true, true, true);
        var offer = new RaiseEvSizing.Offer(40, 20, spot);
        var strongAgainstRaise = new Estimate(.25, new Response(.50, .50), new Response(.25, .80), .55);
        var weakAgainstRaise = new Estimate(.25, new Response(.50, .50), new Response(.25, .10), .55);
        assertEquals(15, RangeReraiseValue.chips(60, 100, offer, strongAgainstRaise, 0, false), 1e-12);
        assertEquals(48, RangeReraiseValue.chips(60, 100, offer, strongAgainstRaise, 0, true), 1e-12);
        assertEquals(15, RangeReraiseValue.chips(60, 100, offer, weakAgainstRaise, 0, true), 1e-12);
        var everyoneFolds = new Estimate(1, new Response(0, Double.NaN), new Response(0, Double.NaN), .55);
        assertEquals(60, RangeReraiseValue.chips(60, 100, offer, everyoneFolds, .08, true), 1e-12);
    }

    @Test void changingOnlyTheQuotePreservesTheRollAndTheActualResponseStillUsesItsPrice() {
        var small = afterQuote(45).observeActor();
        var large = afterQuote(65).observeActor();
        var policy = new LooseAggressivePolicy(50, 0, 29);
        assertEquals(LooseAggressivePolicy.sharedPreflopRoll(small), LooseAggressivePolicy.sharedPreflopRoll(large));
        assertEquals(ActionType.CALL, policy.decideWithSharedPreflopDraw(small).action());
        assertEquals(ActionType.FOLD, policy.decideWithSharedPreflopDraw(large).action());
    }

    private static Map<Bucket, Counts> weights(Counts premium, Counts other) {
        return Map.of(Bucket.PREMIUM, premium, Bucket.SMALL_PAIR, new Counts(0, 0, 0),
                Bucket.BROADWAY, new Counts(0, 0, 0), Bucket.OTHER, other);
    }

    private static List<Card> cards(String first, String second) {
        return List.of(Card.fromLabel(first), Card.fromLabel(second));
    }

    private static HoldemHand afterQuote(int amount) {
        var hand = new HoldemHand(1, List.of(240, 600), 0, new BlindLevel(10, 20, 20), HoldemHand.shuffledDeck(21));
        hand.act(hand.actor(), ActionType.RAISE, 30);
        hand.act(hand.actor(), ActionType.RAISE, amount);
        return hand;
    }
}
