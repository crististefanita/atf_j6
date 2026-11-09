package ro.atf.holdem.ai;

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

class RaiseEvSizingPolicyTest {
    private static final RaiseEvSizing.Settings SETTINGS = new RaiseEvSizing.Settings(8, 20, List.of(50, 100, 150));

    @Test void valuesFoldCallAndReraiseFromTheCurrentDecision() {
        assertEquals(0, RaiseEvSizing.expectedChips(100, 50, 50, 0, 1.0 / 3, 0), 1e-9);
        assertEquals(56, RaiseEvSizing.expectedChips(100, 60, 40, 0.6, 0.2, 0.1), 1e-9);
        assertEquals(-60, RaiseEvSizing.expectedChips(100, 60, 40, 1, 0, 1), 1e-9);
        assertEquals(100, RaiseEvSizing.expectedChips(100, 60, 40, 0, 1, 0), 1e-9);
    }

    @Test void quotesTwoDifferentPriceBandsAndHonorsTheLegalAndCommitmentCaps() {
        var hand = headsUp();
        var observation = hand.observeActor();
        var legal = legalRaise(hand);
        var offers = RaiseEvSizing.offers(observation, legal, 500, 30, SETTINGS);
        assertEquals(List.of(30, 70), offers.stream().map(RaiseEvSizing.Offer::amount).toList());
        assertEquals(List.of(20, 60), offers.stream().map(RaiseEvSizing.Offer::opponentPrice).toList());
        assertEquals(1, RaiseEvSizing.offers(observation, legal, 69, 30, SETTINGS).size());
        assertTrue(RaiseEvSizing.offers(observation, legal, 29, 30, SETTINGS).isEmpty());

        var alternate = offers.getLast();
        hand.act(hand.actor(), ActionType.RAISE, alternate.amount());
        assertEquals(alternate.opponentPrice(), hand.observeActor().toCall());
        assertEquals(alternate.spot(), PreflopResponseModel.facedRaise(hand.observeActor()));
    }

    @Test void usesFoldEquityButKeepsTheReferenceWhenReraiseRiskOrMissingSupportDominates() {
        var hand = headsUp();
        var observation = hand.observeActor();
        var offers = RaiseEvSizing.offers(observation, legalRaise(hand), 500, 30, SETTINGS);
        var cheap = new PreflopResponseModel.Counts(200, 0, 0);
        var folds = response(offers, cheap, new PreflopResponseModel.Counts(200, 180, 0));
        var attacks = response(offers, cheap, new PreflopResponseModel.Counts(200, 0, 180));
        assertEquals(70, RaiseEvSizing.compare(observation, offers, .30, folds, SETTINGS).selected().offer().amount());
        assertEquals(30, RaiseEvSizing.compare(observation, offers, .30, attacks, SETTINGS).selected().offer().amount());
        var sparse = response(offers, cheap, new PreflopResponseModel.Counts(5, 4, 0));
        assertNull(RaiseEvSizing.compare(observation, offers, .30, sparse, SETTINGS));
    }

    @Test void integrationChangesSizingWithoutChangingTheRaiseDecisionOrItsThreshold() {
        var hand = headsUp();
        var observation = hand.observeActor();
        var baseline = ContextualEquityPolicy.HardPressure.TOURNAMENT;
        var control = policy(baseline).decide(observation);
        assertEquals(ActionType.RAISE, control.action());
        var offers = RaiseEvSizing.offers(observation, legalRaise(hand), 500, control.amount(), SETTINGS);
        var response = response(offers, new PreflopResponseModel.Counts(200, 0, 180),
                new PreflopResponseModel.Counts(200, 0, 0));
        var candidate = new ContextualEquityPolicy.HardPressure(3, 76, 86, 94, false,
                baseline.raiseContext(), response, SETTINGS);
        var chosen = policy(candidate).decide(observation);
        assertEquals(ActionType.RAISE, chosen.action());
        assertEquals(offers.getLast().amount(), chosen.amount());
        assertNotEquals(control.amount(), chosen.amount());
        assertTrue(chosen.explanation().startsWith(control.explanation().split(";")[0] + " evReferenceAmount="));
        assertTrue(chosen.policyVersion().contains("-ev.8.20.50.100.150"));
        hand.act(hand.actor(), chosen.action(), chosen.amount());
    }

    private static PreflopResponseModel response(List<RaiseEvSizing.Offer> offers,
                                                  PreflopResponseModel.Counts first, PreflopResponseModel.Counts second) {
        return new PreflopResponseModel(24, 30, 0, 0, 0,
                Map.of(offers.getFirst().spot(), first, offers.getLast().spot(), second));
    }

    private static ContextualEquityPolicy policy(ContextualEquityPolicy.HardPressure pressure) {
        return new ContextualEquityPolicy(128, 2, 8, 18, 60, ContextualEquityPolicy.GameMode.TOURNAMENT,
                ContextualEquityPolicy.ContextWeights.defaults(), pressure);
    }

    private static LegalAction legalRaise(HoldemHand hand) {
        return hand.legal().stream().filter(action -> action.type() == ActionType.RAISE).findFirst().orElseThrow();
    }

    private static HoldemHand headsUp() {
        var deck = new ArrayList<>(HoldemHand.shuffledDeck(19));
        Card first = Card.fromLabel("As"), second = Card.fromLabel("Ad");
        deck.removeAll(List.of(first, second));
        deck.add(1, first);
        deck.add(3, second);
        return new HoldemHand(1, List.of(1000, 1000), 0, new BlindLevel(10, 20, 20), deck);
    }
}
