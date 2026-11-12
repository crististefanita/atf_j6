package ro.atf.holdem.ai;

import atf.testkit.AtfComponentTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.LegalAction;

import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class PreflopResponsePolicyTest {
    private static final PreflopResponseModel.Spot OPEN_LATE =
            PreflopResponseModel.Spot.fromKey("OPEN.late.cheap.funded");
    private static final PreflopResponseModel.Spot OPEN_EARLY =
            PreflopResponseModel.Spot.fromKey("OPEN.early.cheap.funded");

    @Test void quotedPriceAndPositionMatchTheActualFirstRaiseAndReraise() {
        var hand = headsUp();
        var opening = PreflopResponseModel.proposedRaise(hand.observeActor(), 40);
        assertEquals(OPEN_LATE, opening);
        hand.act(hand.actor(), ActionType.RAISE, 40);
        assertEquals(opening, PreflopResponseModel.facedRaise(hand.observeActor()));

        var minimum = hand.legal().stream().filter(action -> action.type() == ActionType.RAISE)
                .mapToInt(LegalAction::minAmount).findFirst().orElseThrow();
        var reraise = PreflopResponseModel.proposedRaise(hand.observeActor(), minimum);
        assertEquals("RERAISE.early.cheap.funded", reraise.key());
        hand.act(hand.actor(), ActionType.RAISE, minimum);
        assertEquals(reraise, PreflopResponseModel.facedRaise(hand.observeActor()));
    }

    @Test void recognizesLimpOrderAndAbstainsForMultiwayAndForcedAllInResponses() {
        var hand = headsUp();
        assertNull(PreflopResponseModel.proposedRaise(hand.observeActor(), 1010));
        hand.act(hand.actor(), ActionType.CALL, 10);
        var afterLimp = PreflopResponseModel.proposedRaise(hand.observeActor(), 20);
        assertEquals("AFTER_LIMP.early.cheap.funded", afterLimp.key());
        hand.act(hand.actor(), ActionType.RAISE, 20);
        assertEquals(afterLimp, PreflopResponseModel.facedRaise(hand.observeActor()));

        var three = new HoldemHand(1, List.of(1000, 1000, 1000), 0, new BlindLevel(10, 20, 20),
                HoldemHand.shuffledDeck(19));
        assertNull(PreflopResponseModel.proposedRaise(three.observeActor(), 40));
        assertNull(PreflopResponseModel.facedRaise(three.observeActor()));

        var shallow = new HoldemHand(1, List.of(1000, 100), 0, new BlindLevel(10, 20, 20),
                HoldemHand.shuffledDeck(19));
        assertEquals("OPEN.late.priced.short", PreflopResponseModel.proposedRaise(shallow.observeActor(), 60).key());
        assertNull(PreflopResponseModel.proposedRaise(shallow.observeActor(), 90));
    }

    @Test void shrinksToTheSamePriceAndStackPoolAndBoundsBothAdjustmentDirections() {
        var model = model(32);
        var estimate = model.estimate(OPEN_LATE);
        assertEquals(0.44, estimate.priorFold(), 1e-12);
        assertEquals(0.30, estimate.priorRaise(), 1e-12);
        assertEquals((32 + 30 * 0.44) / 70, estimate.fold(), 1e-12);
        assertEquals(30 * 0.30 / 70, estimate.raise(), 1e-12);
        assertEquals(-4, estimate.adjustment());
        assertEquals(4, model.estimate(OPEN_EARLY).adjustment());
        assertNull(model(41).estimate(OPEN_LATE));
        assertNotEquals(model.version(), model(41).version());
        assertThrows(IllegalArgumentException.class, () -> new PreflopResponseModel.Counts(10, 6, 5));
    }

    @Test void modelChangesOnlyTheRaiseThresholdAndKeepsTheQuotedSize() {
        var spot = headsUp().observeActor();
        var control = policy(null).decide(spot);
        var candidate = policy(model(32)).decide(spot);
        var unsupported = policy(model(41)).decide(spot);
        assertEquals(ActionType.RAISE, control.action());
        assertEquals(control.action(), candidate.action());
        assertEquals(control.amount(), candidate.amount());
        assertEquals(metric(control, "required"), metric(candidate, "required"));
        assertEquals(metric(control, "raiseCap"), metric(candidate, "raiseCap"));
        assertEquals(metric(control, "raiseRequired") - 4, metric(candidate, "raiseRequired"));
        assertTrue(candidate.explanation().contains("responseSpot=" + OPEN_LATE.key()));
        assertEquals(control.explanation(), unsupported.explanation());
        assertTrue(candidate.policyVersion().contains("-response."));
        assertFalse(control.policyVersion().contains("-response."));
    }

    @Test void isolatedSignalsKeepTheSameForecastsAndOnlyThePenaltyOmitsTheRaiseDiscount() {
        var fit = model(32);
        var foldOnly = new PreflopResponseModel(32, 30, 16, 0, 4, fit.observations());
        var signedRaise = new PreflopResponseModel(32, 30, 0, 24, 4, fit.observations());
        var penalty = new PreflopResponseModel(32, 30, 0, 24, 4, true, fit.observations());

        assertEquals(fit.estimate(OPEN_LATE).fold(), foldOnly.estimate(OPEN_LATE).fold());
        assertEquals(signedRaise.estimate(OPEN_LATE).raise(), penalty.estimate(OPEN_LATE).raise());
        assertEquals(-3, foldOnly.estimate(OPEN_LATE).adjustment());
        assertEquals(3, foldOnly.estimate(OPEN_EARLY).adjustment());
        assertEquals(-4, signedRaise.estimate(OPEN_LATE).adjustment());
        assertEquals(0, penalty.estimate(OPEN_LATE).adjustment());
        assertEquals(3, penalty.estimate(OPEN_EARLY).adjustment());
        assertNotEquals(signedRaise.version(), penalty.version());
    }

    @Test void isolatedProfilesPreserveTheCallThresholdAndTheQuotedRaise() {
        var fit = model(32);
        var observation = headsUp().observeActor();
        var control = policy(null).decide(observation);
        var fold = policy(new PreflopResponseModel(32, 30, 16, 0, 4, fit.observations())).decide(observation);
        var penalty = policy(new PreflopResponseModel(32, 30, 0, 24, 4, true, fit.observations())).decide(observation);

        for (var decision : List.of(fold, penalty)) {
            assertEquals(ActionType.RAISE, decision.action());
            assertEquals(control.amount(), decision.amount());
            assertEquals(metric(control, "required"), metric(decision, "required"));
            assertEquals(metric(control, "raiseCap"), metric(decision, "raiseCap"));
        }
        assertEquals(metric(control, "raiseRequired") - 3, metric(fold, "raiseRequired"));
        assertEquals(metric(control, "raiseRequired"), metric(penalty, "raiseRequired"));
    }

    private static PreflopResponseModel model(int minSamples) {
        return new PreflopResponseModel(minSamples, 30, 16, 24, 4, Map.of(
                OPEN_LATE, new PreflopResponseModel.Counts(40, 32, 0),
                OPEN_EARLY, new PreflopResponseModel.Counts(60, 12, 30),
                PreflopResponseModel.Spot.fromKey("OPEN.late.priced.funded"), new PreflopResponseModel.Counts(50, 0, 50)));
    }

    private static ContextualEquityPolicy policy(PreflopResponseModel model) {
        var baseline = ContextualEquityPolicy.HardPressure.TOURNAMENT;
        var hard = new ContextualEquityPolicy.HardPressure(baseline.cheapPreflopDiscount(), baseline.fullPotEquity(),
                baseline.doublePotEquity(), baseline.triplePotEquity(), false, baseline.raiseContext(), model);
        return new ContextualEquityPolicy(128, 2, 8, 18, 60, ContextualEquityPolicy.GameMode.TOURNAMENT,
                ContextualEquityPolicy.ContextWeights.defaults(), hard);
    }

    private static HoldemHand headsUp() {
        var deck = new ArrayList<>(HoldemHand.shuffledDeck(19));
        Card first = Card.fromLabel("As"), second = Card.fromLabel("Ad");
        deck.removeAll(List.of(first, second));
        deck.add(1, first);
        deck.add(3, second);
        return new HoldemHand(1, List.of(1000, 1000), 0, new BlindLevel(10, 20, 20), deck);
    }

    private static int metric(Decision decision, String name) {
        var match = java.util.regex.Pattern.compile(" " + name + "=(\\d+)").matcher(decision.explanation());
        assertTrue(match.find(), decision::explanation);
        return Integer.parseInt(match.group(1));
    }
}
