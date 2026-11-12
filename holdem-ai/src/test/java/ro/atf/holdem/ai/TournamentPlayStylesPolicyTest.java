package ro.atf.holdem.ai;

import atf.testkit.AtfComponentTest;

import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemObservation;
import ro.atf.holdem.core.LegalAction;
import ro.atf.holdem.core.Street;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static ro.atf.holdem.ai.TournamentPlayStyles.Style.*;

@AtfComponentTest
class TournamentPlayStylesPolicyTest {
    @Test void originalSeatsDetermineTheHardRosterForEveryHumanPosition() {
        var roster = List.of(BALANCED, PRUDENT, ACTIVE, POSITIONAL, NORMAL);
        for (int seats : List.of(4, 5, 6)) {
            for (int human = 0; human < seats; human++) {
                var actual = new ArrayList<TournamentPlayStyles.Style>();
                for (int offset = 1; offset < seats; offset++) {
                    actual.add(TournamentPlayStyles.hardOpponentStyle(seats, human, (human + offset) % seats));
                }
                assertEquals(roster.subList(0, seats - 1), actual);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> TournamentPlayStyles.hardOpponentStyle(6, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> TournamentPlayStyles.hardOpponentStyle(6, 0, 6));
    }

    @Test void samePriceAndPublicStateProduceDifferentContinuationThresholds() {
        var spot = pricedSpot();
        var active = (ContextualEquityPolicy) ACTIVE.policy(128);
        var balanced = (ContextualEquityPolicy) BALANCED.policy(128);
        var positional = (ContextualEquityPolicy) POSITIONAL.policy(128);
        var prudent = (ContextualEquityPolicy) PRUDENT.policy(128);
        int activeThreshold = active.requiredEquityPercent(spot);
        int balancedThreshold = balanced.requiredEquityPercent(spot);
        int positionalThreshold = positional.requiredEquityPercent(spot);
        int prudentThreshold = prudent.requiredEquityPercent(spot);
        assertTrue(activeThreshold < balancedThreshold, "Active continues at a lower threshold");
        assertTrue(balancedThreshold < positionalThreshold, "Positional is more selective at the same price");
        assertTrue(positionalThreshold < prudentThreshold, "Prudent requires the most equity to continue");
        assertEquals(4, List.of(active, balanced, positional, prudent).stream()
                .map(policy -> policy.decide(spot).policyVersion()).distinct().count());
        assertEquals(ActionType.CALL, NORMAL.policy(128).decide(spot).action());
    }

    private static HoldemObservation pricedSpot() {
        return new HoldemObservation(5, 0, 2, 1, Street.PREFLOP,
                List.of(Card.fromLabel("Qs"), Card.fromLabel("Td")), List.of(), 120,
                20, 500, 0, List.of(500, 500),
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                        new LegalAction(ActionType.RAISE, 40, 40, 500)),
                List.of("SEATS [0, 1]", "BB 1 20"), 0, 20, List.of(0, 20));
    }
}
