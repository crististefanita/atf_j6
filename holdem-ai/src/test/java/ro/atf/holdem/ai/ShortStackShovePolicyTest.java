package ro.atf.holdem.ai;

import atf.testkit.AtfComponentTest;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;
import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class ShortStackShovePolicyTest {
    @Test void shortStackUsesPremiumUnopenedAllInOnlyWhenLegal() {
        var policy = new ShortStackShovePolicy(8);
        var observation = observation(List.of(card("Ah"), card("Ad")), 160, 0,
                List.of(LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 160),
                        new LegalAction(ActionType.ALL_IN, 160, 160, 160)));

        var decision = policy.decide(observation);

        assertEquals(ActionType.ALL_IN, decision.action());
        assertEquals(160, decision.amount());
        assertTrue(decision.explanation().contains("unopened push"));
        assertTrue(decision.policyVersion().contains("BB8"));
    }

    @Test void premiumShortStackMayReShoveAraiseAndCallAnUncoverableShortPrice() {
        var policy = new ShortStackShovePolicy(8);
        var reRaise = observation(List.of(card("As"), card("Kc")), 160, 40,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 40, 40, 40),
                        new LegalAction(ActionType.RAISE, 80, 80, 160),
                        new LegalAction(ActionType.ALL_IN, 160, 160, 160)));
        var shortCall = observation(List.of(card("As"), card("Kc")), 60, 90,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 60, 60, 60),
                        new LegalAction(ActionType.ALL_IN, 60, 60, 60)));

        assertEquals(ActionType.ALL_IN, policy.decide(reRaise).action());
        assertTrue(policy.decide(reRaise).explanation().contains("premium re-shove"));
        assertEquals(ActionType.ALL_IN, policy.decide(shortCall).action());
        assertTrue(policy.decide(shortCall).explanation().contains("premium short call"));
    }

    @Test void weakOrDeeperStacksUseTheTagFallbackInsteadOfAnAutomaticShove() {
        var policy = new ShortStackShovePolicy(8);
        var weakShort = observation(List.of(card("2c"), card("7d")), 160, 0,
                List.of(LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 160),
                        new LegalAction(ActionType.ALL_IN, 160, 160, 160)));
        var premiumDeep = observation(List.of(card("Ah"), card("Ad")), 180, 0,
                List.of(LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.RAISE, 40, 40, 180),
                        new LegalAction(ActionType.ALL_IN, 180, 180, 180)));

        assertEquals(ActionType.CHECK, policy.decide(weakShort).action());
        assertEquals(ActionType.RAISE, policy.decide(premiumDeep).action());
    }

    @Test void thresholdIsBoundedAndVersioned() {
        assertThrows(IllegalArgumentException.class, () -> new ShortStackShovePolicy(0));
        assertThrows(IllegalArgumentException.class, () -> new ShortStackShovePolicy(21));
        assertEquals("SHORT-STACK-SHOVE-0.1-BB8", new ShortStackShovePolicy(8).version());
    }

    private static HoldemObservation observation(List<Card> hole, int stack, int toCall,
                                                  List<LegalAction> legal) {
        return new HoldemObservation(1, 0, 4, 3, Street.PREFLOP, hole, List.of(), 120, toCall, stack, 0,
                List.of(stack, 800, 800, 800), legal, List.of("SEATS [0, 1, 2, 3]"), 0, 20,
                List.of(0, 0, 0, 0));
    }

    private static Card card(String label) { return Card.fromLabel(label); }
}
