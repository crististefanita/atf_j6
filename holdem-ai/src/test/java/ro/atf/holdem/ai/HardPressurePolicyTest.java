package ro.atf.holdem.ai;

import java.util.List;
import java.util.regex.Pattern;
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

    @Test void preflopCallDiscountCannotChangeRaiseThresholdOrSizing() {
        var spot = new HoldemObservation(5, 0, 2, 1, Street.PREFLOP, cards("As", "Ad"), List.of(), 120,
                20, 500, 0, List.of(500, 500), pricedActions(40), List.of("SEATS [0, 1]", "BB 1 20"),
                0, 20, List.of(0, 20));
        var tight = splitPolicy(0).decide(spot);
        var loose = splitPolicy(8).decide(spot);
        assertTrue(metric(loose, "required") < metric(tight, "required"));
        assertEquals(metric(tight, "raiseRequired"), metric(loose, "raiseRequired"));
        assertEquals(ActionType.RAISE, tight.action());
        assertEquals(tight.action(), loose.action());
        assertEquals(tight.amount(), loose.amount());
        assertTrue(loose.policyVersion().contains("-split.8.5.4.3.4.6.10.60"));
    }

    @Test void raiseSeparatesHeadsUpPositionAndPublicPressure() {
        var candidate = splitPolicy(3);
        var neutral = List.of("SEATS [0, 1]", "RIVER [Qs, Js, Ts, 2d, 3c]");
        var withBet = List.of("SEATS [0, 1]", "RIVER [Qs, Js, Ts, 2d, 3c]", "RIVER 1 BET 20");
        var onButton = candidate.decide(pricedRiver(0, 500, List.of(500, 500), List.of(40, 60), 40, neutral));
        var outOfPosition = candidate.decide(pricedRiver(1, 500, List.of(500, 500), List.of(40, 60), 40, neutral));
        var underPressure = candidate.decide(pricedRiver(0, 500, List.of(500, 500), List.of(40, 60), 40, withBet));
        assertEquals(8, metric(outOfPosition, "raiseRequired") - metric(onButton, "raiseRequired"));
        assertEquals(3, metric(underPressure, "raiseRequired") - metric(onButton, "raiseRequired"));
    }

    @Test void minimumRaiseCommitmentRaisesOnlyTheAggressionThreshold() {
        var candidate = splitPolicy(3);
        var history = List.of("SEATS [0, 1]", "RIVER [Qs, Js, Ts, 2d, 3c]", "RIVER 1 BET 20");
        var uncommitted = candidate.decide(pricedRiver(0, 100, List.of(100, 500), List.of(40, 60), 59, history));
        var committed = candidate.decide(pricedRiver(0, 100, List.of(100, 500), List.of(40, 60), 60, history));
        assertEquals(metric(uncommitted, "required"), metric(committed, "required"));
        assertEquals(10, metric(committed, "raiseRequired") - metric(uncommitted, "raiseRequired"));
        assertEquals(ActionType.RAISE, committed.action());
        assertTrue(committed.amount() >= 60 && committed.amount() < 100);
    }

    @Test void moderateEquityCanRaiseBelowTheCommitmentCutoffOrCallIfTheMinimumExceedsIt() {
        var weights = new ContextualEquityPolicy.RaiseContext(25, 5, 4, 3, 4, 6, 35, 60);
        var candidate = policy(new ContextualEquityPolicy.HardPressure(3, 76, 86, 94, false, weights));
        var sized = candidate.decide(overpairFacingBet(40));
        assertEquals(ActionType.RAISE, sized.action());
        assertEquals(59, sized.amount());
        assertEquals(ActionType.CALL, candidate.decide(overpairFacingBet(60)).action());
    }

    @Test void sidePotRaiseRequiresAFundedNonFoldedOpponentWhoCanMatchTheMinimum() {
        var history = List.of("SEATS [0, 2, 4, 5]", "PREFLOP 2 FOLD 0",
                "RIVER [Qs, Js, Ts, 2d, 3c]", "RIVER 5 ALL_IN 20");
        var candidate = splitPolicy(3);
        var shortOpponent = pricedRiver(0, 500, List.of(500, 900, 10, 0),
                List.of(40, 10, 60, 200), 40, history);
        var fundedOpponent = pricedRiver(0, 500, List.of(500, 900, 24, 0),
                List.of(40, 10, 60, 200), 40, history);
        assertEquals(ActionType.CALL, candidate.decide(shortOpponent).action());
        var raise = candidate.decide(fundedOpponent);
        assertEquals(ActionType.RAISE, raise.action());
        assertEquals(44, raise.amount());
    }

    private static HoldemObservation pricedRiver(int position, int stack, List<Integer> stacks,
                                                List<Integer> contributions, int minRaise, List<String> history) {
        return new HoldemObservation(1, 0, stacks.size(), stacks.size() == 2 ? 1 : 2,
                Street.RIVER, cards("As", "Ks"), ROYAL, 150, 20, stack, position, stacks,
                pricedActions(minRaise), history, 0, 20, contributions);
    }

    private static HoldemObservation overpairFacingBet(int minRaise) {
        return new HoldemObservation(11, 0, 2, 1, Street.FLOP, cards("As", "Ad"), cards("7c", "9h", "Js"),
                150, 20, 100, 0, List.of(100, 500), pricedActions(minRaise),
                List.of("SEATS [0, 1]", "FLOP [7c, 9h, Js]", "FLOP 1 BET 20"), 0, 20, List.of(65, 85));
    }

    private static List<LegalAction> pricedActions(int minRaise) {
        return List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                new LegalAction(ActionType.RAISE, minRaise, minRaise, 500));
    }

    private static int metric(Decision decision, String name) {
        var match = Pattern.compile(" " + name + "=(\\d+)(?:\\.0)?%").matcher(decision.explanation());
        assertTrue(match.find(), decision::explanation);
        return Integer.parseInt(match.group(1));
    }

    private static ContextualEquityPolicy splitPolicy(int discount) {
        var context = new ContextualEquityPolicy.RaiseContext(8, 5, 4, 3, 4, 6, 10, 60);
        return policy(new ContextualEquityPolicy.HardPressure(discount, 76, 86, 94, false, context));
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
