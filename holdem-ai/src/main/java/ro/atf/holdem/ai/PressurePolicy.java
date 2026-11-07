package ro.atf.holdem.ai;

import java.util.Objects;
import ro.atf.holdem.core.*;

/** High-pressure style for stress-testing legal openings and premium-card all-ins. */
public final class PressurePolicy implements DecisionPolicy {
    public static final String VERSION = "PRESSURE-1";

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        boolean highCards = observation.holeCards().size() == 2
                && observation.holeCards().stream().allMatch(card -> card.rank().value() >= 11);
        for (LegalAction action : observation.legalActions()) {
            if (action.type() == ActionType.BET)
                return new Decision(ActionType.BET, action.minAmount(), VERSION, "Apply minimum legal opening pressure");
            if (action.type() == ActionType.ALL_IN && highCards && action.amount() <= observation.toCall())
                return new Decision(ActionType.ALL_IN, action.amount(), VERSION, "Match a short all-in with high private cards");
        }
        LegalAction safe = observation.legalActions().stream()
                .filter(action -> action.type() == ActionType.CHECK || action.type() == ActionType.CALL)
                .findFirst().orElseGet(() -> observation.legalActions().stream().findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("No legal action")));
        return new Decision(safe.type(), safe.amount(), VERSION, "No pressure action is currently legal");
    }
}
