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

    private static ContextualEquityPolicy policy(ContextualEquityPolicy.HardPressure pressure) {
        return new ContextualEquityPolicy(128, 2, 8, 18, 60, ContextualEquityPolicy.GameMode.TOURNAMENT,
                ContextualEquityPolicy.ContextWeights.defaults(), pressure);
    }

    private static List<Card> cards(String... labels) {
        return java.util.Arrays.stream(labels).map(Card::fromLabel).toList();
    }
}
