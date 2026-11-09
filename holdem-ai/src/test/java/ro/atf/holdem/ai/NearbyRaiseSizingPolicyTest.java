package ro.atf.holdem.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.ai.NearbyRaiseSizing.Direction;
import ro.atf.holdem.ai.NearbyRaiseSizing.Scope;
import ro.atf.holdem.ai.PreflopResponseModel.Counts;
import ro.atf.holdem.ai.PreflopResponseModel.Sequence;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemHand;
import ro.atf.holdem.core.LegalAction;

import static org.junit.jupiter.api.Assertions.*;

class NearbyRaiseSizingPolicyTest {
    private static final NearbyRaiseSizing.Limits LIMITS = new NearbyRaiseSizing.Limits(100, 25, 5, 35);
    private static final Counts FOLDS = new Counts(40, 40, 0);
    private static final Counts RAISES = new Counts(40, 0, 40);

    @Test void quotesWithinTheSamePriceBandAndMatchesTheAmountTheOpponentFaces() {
        var hand = headsUp(1000);
        var pair = quotes(hand, 500, 30).getFirst();
        assertEquals(Direction.UP, pair.key().direction());
        assertEquals(37, pair.alternate().amount());
        assertEquals(27, pair.alternate().opponentPrice());
        assertEquals(pair.reference().spot(), pair.alternate().spot());

        hand.act(hand.actor(), ActionType.RAISE, pair.alternate().amount());
        var reply = hand.observeActor();
        assertEquals(reply.toCall(), pair.alternate().opponentPrice());
        assertEquals(PreflopResponseModel.facedRaise(reply), pair.alternate().spot());
    }

    @Test void honorsTheShorterOpponentStackAndKeepsAControlAboveTheCap() {
        var shortOpponent = headsUp(100);
        var capped = quotes(shortOpponent, 500, 30).getFirst();
        assertEquals(90, capped.effectiveStack());
        assertEquals(31, capped.alternate().amount());
        assertTrue(quotes(shortOpponent, 500, 32).isEmpty());
        assertTrue(quotes(shortOpponent, 30, 30).isEmpty());
        assertTrue(quotes(shortOpponent, 500, 29).isEmpty());

        var deeperOpponent = headsUp(200);
        var boundedDelta = quotes(deeperOpponent, 500, 50);
        assertEquals(List.of(41, 59), boundedDelta.stream().map(pair -> pair.alternate().amount()).toList());
        assertEquals(List.of(100, 140), quotes(headsUp(1000), 500, 120).stream()
                .map(pair -> pair.alternate().amount()).toList());
    }

    @Test void separatesFirstRaiseAfterLimpAndReraiseUsingThePublicPrefix() {
        var hand = headsUp(1000);
        assertEquals(Sequence.OPEN, quotes(hand, 500, 30).getFirst().key().spot().sequence());
        hand.act(hand.actor(), ActionType.RAISE, 30);
        var reply = quotes(hand, 500, 50).getFirst();
        assertEquals(Sequence.RERAISE, reply.key().spot().sequence());
        assertEquals(Scope.RERAISE, Scope.forSpot(reply.key().spot()));
        assertThrows(IllegalArgumentException.class, () -> model(Scope.FIRST_RAISE, reply, RAISES, FOLDS));

        var limped = headsUp(1000);
        limped.act(limped.actor(), ActionType.CALL, 10);
        assertTrue(quotes(limped, 500, 30).isEmpty());
        var afterLimp = new PreflopResponseModel.Spot(Sequence.AFTER_LIMP, true, true, false);
        assertEquals(Scope.FIRST_RAISE, Scope.forSpot(afterLimp));
    }

    @Test void requiresPairedSupportAndChoosesOnlyAQuoteWithAnAdvantage() {
        var hand = headsUp(1000);
        var observation = hand.observeActor();
        var quotes = quotes(hand, 500, 50);
        var lower = quotes.getFirst();
        var higher = quotes.getLast();
        assertEquals(38, lower.alternate().amount());
        assertEquals(62, higher.alternate().amount());
        assertEquals(38, model(Scope.FIRST_RAISE, lower, RAISES, FOLDS)
                .compare(observation, quotes, .45, 4).selected().offer().amount());
        assertEquals(50, model(Scope.FIRST_RAISE, higher, FOLDS, RAISES)
                .compare(observation, quotes, .45, 4).selected().offer().amount());
        var sparse = new Counts(23, 0, 0);
        assertNull(model(Scope.FIRST_RAISE, higher, sparse, sparse).compare(observation, quotes, .45, 4));
        assertThrows(IllegalArgumentException.class, () -> model(Scope.FIRST_RAISE, higher, FOLDS, sparse));
    }

    @Test void changesOnlyTheSelectedRaiseAndLeavesTheOtherStageAtControl() {
        var hand = headsUp(1000);
        var observation = hand.observeActor();
        var baseline = ContextualEquityPolicy.HardPressure.TOURNAMENT;
        var controlPolicy = policy(baseline);
        var control = controlPolicy.decide(observation);
        assertEquals(ActionType.RAISE, control.action());
        var up = quotes(hand, 500, control.amount()).getLast();
        assertEquals(Direction.UP, up.key().direction());
        var settings = new RaiseEvSizing.Settings(8, 20, List.of(), model(Scope.FIRST_RAISE, up, RAISES, FOLDS));
        var candidate = policy(new ContextualEquityPolicy.HardPressure(3, 76, 86, 94, false,
                baseline.raiseContext(), null, settings));
        var selected = candidate.decide(observation);
        assertEquals(control.action(), selected.action());
        assertEquals(up.alternate().amount(), selected.amount());
        assertTrue(selected.explanation().startsWith(control.explanation().split(";")[0] + " evReferenceAmount="));
        assertTrue(selected.policyVersion().contains("-ev.8.20.near."));

        hand.act(hand.actor(), ActionType.RAISE, control.amount());
        var reraiseObservation = hand.observeActor();
        var firstOnly = candidate.decide(reraiseObservation);
        var unchanged = controlPolicy.decide(reraiseObservation);
        assertEquals(unchanged.action(), firstOnly.action());
        assertEquals(unchanged.amount(), firstOnly.amount());
        assertEquals(unchanged.explanation(), firstOnly.explanation());
    }

    private static NearbyRaiseSizing model(Scope scope, NearbyRaiseSizing.QuotePair pair, Counts reference, Counts alternate) {
        return new NearbyRaiseSizing(scope, LIMITS, 24,
                Map.of(pair.key(), new NearbyRaiseSizing.PairedCounts(reference, alternate)));
    }

    private static List<NearbyRaiseSizing.QuotePair> quotes(HoldemHand hand, int cap, int reference) {
        var legal = hand.legal().stream().filter(action -> action.type() == ActionType.RAISE).findFirst()
                .orElse(LegalAction.noAmount(ActionType.BET));
        return NearbyRaiseSizing.quotes(hand.observeActor(), legal, cap, reference, LIMITS);
    }

    private static ContextualEquityPolicy policy(ContextualEquityPolicy.HardPressure pressure) {
        return new ContextualEquityPolicy(128, 2, 8, 18, 60, ContextualEquityPolicy.GameMode.TOURNAMENT,
                ContextualEquityPolicy.ContextWeights.defaults(), pressure);
    }

    private static HoldemHand headsUp(int opponentStack) {
        var deck = new ArrayList<>(HoldemHand.shuffledDeck(19));
        var aces = List.of(Card.fromLabel("As"), Card.fromLabel("Ad"));
        deck.removeAll(aces);
        deck.add(1, aces.getFirst());
        deck.add(3, aces.getLast());
        return new HoldemHand(1, List.of(1000, opponentStack), 0, new BlindLevel(10, 20, 20), deck);
    }
}
