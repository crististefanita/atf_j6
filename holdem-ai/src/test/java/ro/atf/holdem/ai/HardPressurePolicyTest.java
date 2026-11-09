package ro.atf.holdem.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class HardPressurePolicyTest {
    private static final List<Card> ROYAL = cards("Qs", "Js", "Ts", "2d", "3c");
    private static final List<LegalAction> BET = List.of(LegalAction.noAmount(ActionType.CHECK),
            new LegalAction(ActionType.BET, 20, 20, 1000));

    @Test void cheapPreflopPriceGetsDiscountWithoutReducingShoveOrExpensiveCallThresholds() {
        var baseline = policy(null);
        var hard = hard();
        var cheap = preflop(20, List.of());
        var expensive = preflop(50, List.of("PREFLOP 1 RAISE 50"));
        var shoved = preflop(20, List.of("PREFLOP 1 ALL_IN 20"));
        assertEquals(baseline.requiredEquityPercent(cheap) - 3, hard.requiredEquityPercent(cheap));
        assertEquals(baseline.requiredEquityPercent(expensive), hard.requiredEquityPercent(expensive));
        assertEquals(baseline.requiredEquityPercent(shoved), hard.requiredEquityPercent(shoved));
    }

    @Test void deepHeadsUpNutHandUsesTwoPotOnTurnAndThreeOnRiver() {
        var stacks = List.of(1000, 1000);
        var turn = observation(Street.TURN, ROYAL.subList(0, 4), stacks, 1, 0, BET, List.of());
        var river = observation(Street.RIVER, ROYAL, stacks, 1, 0, BET, List.of());
        assertEquals(200, hard().decide(turn).amount());
        var decision = hard().decide(river);
        assertEquals(ActionType.BET, decision.action());
        assertEquals(300, decision.amount());
        assertTrue(decision.policyVersion().endsWith("-hard2.3.76.86.94"));
        assertTrue(policy(null).decide(river).amount() < 100);
    }

    @Test void hardRaisesPremiumBigBlindAfterLimpButChecksWeakHolding() {
        var premiumHand = limpedHeadsUp("As", "Ad", 1000);
        var premium = premiumHand.observeActor();
        assertEquals(0, premium.toCall());
        assertEquals(ActionType.CHECK, policy(null).decide(premium).action());
        assertEquals(ActionType.CHECK, hard().decide(premium).action());

        var raise = bigBlindPolicy().decide(premium);
        assertEquals(ActionType.RAISE, raise.action());
        assertEquals(premium.pot() / 2, raise.amount());
        assertTrue(raise.policyVersion().endsWith("-bb-option"));
        premiumHand.act(premiumHand.actor(), raise.action(), raise.amount());
        assertEquals(raise.amount(), premiumHand.observeActor().toCall());

        var weak = limpedHeadsUp("7s", "2d", 1000).observeActor();
        assertEquals(ActionType.CHECK, bigBlindPolicy().decide(weak).action());
    }

    @Test void hardChecksWhenPremiumRaiseWouldCommitItsEntireStack() {
        var shortStack = limpedHeadsUp("As", "Ad", 40).observeActor();
        assertTrue(shortStack.legalActions().stream().anyMatch(action -> action.type() == ActionType.RAISE));
        assertEquals(ActionType.CHECK, bigBlindPolicy().decide(shortStack).action());

        var underfunded = limpedHeadsUp("As", "Ad", 1000, 30).observeActor();
        assertTrue(underfunded.legalActions().stream().anyMatch(action -> action.type() == ActionType.RAISE));
        assertEquals(ActionType.CHECK, bigBlindPolicy().decide(underfunded).action());
    }

    @Test void hardScalesTheBigBlindValueRaiseWhenSeveralOpponentsLimp() {
        var hand = limpedHand("As", "Ad", List.of(1000, 1000, 1000, 1000));
        var observation = hand.observeActor();
        assertEquals(3, observation.activeOpponentCount());
        var decision = bigBlindPolicy().decide(observation);
        assertEquals(ActionType.RAISE, decision.action());
        assertEquals(40, decision.amount());
        hand.act(hand.actor(), decision.action(), decision.amount());
        assertEquals(40, hand.observeActor().toCall());
    }

    private static HoldemHand limpedHeadsUp(String first, String second, int bigBlindStack) {
        return limpedHeadsUp(first, second, bigBlindStack, 1000);
    }

    private static HoldemHand limpedHeadsUp(String first, String second, int bigBlindStack, int opponentStack) {
        return limpedHand(first, second, List.of(opponentStack, bigBlindStack));
    }

    private static HoldemHand limpedHand(String first, String second, List<Integer> stacks) {
        var deck = new java.util.ArrayList<>(HoldemHand.shuffledDeck(42));
        var hole = cards(first, second);
        deck.removeAll(hole);
        int bigBlindDealIndex = stacks.size() == 2 ? 0 : 1;
        deck.add(bigBlindDealIndex, hole.getFirst());
        deck.add(bigBlindDealIndex + stacks.size(), hole.getLast());
        var hand = new HoldemHand(1, stacks, 0, new BlindLevel(10, 20, 20), deck);
        while (hand.actor() != hand.bigBlindSeat()) {
            var call = hand.legal().stream().filter(action -> action.type() == ActionType.CALL).findFirst().orElseThrow();
            hand.act(hand.actor(), call.type(), call.amount());
        }
        return hand;
    }

    @Test void potMultiplesRespectLegalMaximumAndOnlyTheRemainingOpponentCanFundThem() {
        var liveShort = observation(Street.RIVER, ROYAL, List.of(1000, 250, 4000), 1, 0, BET,
                List.of("FLOP 2 FOLD 0"));
        var multiway = observation(Street.RIVER, ROYAL, List.of(1000, 1000, 4000), 2, 0, BET, List.of());
        var constrained = observation(Street.RIVER, ROYAL, List.of(1000, 1000), 1, 0,
                List.of(LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 199)),
                List.of());
        var tooShort = observation(Street.RIVER, ROYAL, List.of(1000, 90), 1, 0, BET, List.of());
        assertEquals(200, hard().decide(liveShort).amount());
        assertEquals(policy(null).decide(multiway).amount(), hard().decide(multiway).amount());
        assertEquals(100, hard().decide(constrained).amount());
        assertEquals(policy(null).decide(tooShort).amount(), hard().decide(tooShort).amount());
    }

    private static HoldemObservation preflop(int price, List<String> history) {
        return observation(Street.PREFLOP, List.of(), List.of(1000, 1000), 1, price,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, price, price, price)),
                history);
    }

    private static HoldemObservation observation(Street street, List<Card> board, List<Integer> stacks, int opponents,
                                                  int price, List<LegalAction> actions, List<String> history) {
        return new HoldemObservation(1, 0, stacks.size(), opponents, street, cards("As", "Ks"), board, 100,
                price, stacks.getFirst(), 0, stacks, actions, history, 0, 20,
                java.util.Collections.nCopies(stacks.size(), 0));
    }

    private static ContextualEquityPolicy hard() {
        return policy(new ContextualEquityPolicy.HardPressure(3, 76, 86, 94));
    }

    private static ContextualEquityPolicy bigBlindPolicy() {
        return policy(new ContextualEquityPolicy.HardPressure(3, 76, 86, 94, true));
    }

    private static ContextualEquityPolicy policy(ContextualEquityPolicy.HardPressure pressure) {
        return new ContextualEquityPolicy(128, 2, 8, 18, 60, ContextualEquityPolicy.GameMode.TOURNAMENT,
                ContextualEquityPolicy.ContextWeights.defaults(), pressure);
    }

    private static List<Card> cards(String... labels) {
        return java.util.Arrays.stream(labels).map(Card::fromLabel).toList();
    }
}
