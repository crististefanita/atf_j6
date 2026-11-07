package ro.atf.holdem.ai;

import java.util.Objects;
import ro.atf.holdem.core.*;

/** Matches every available price and never raises; useful as a conservative opponent model. */
public final class CallingStationPolicy implements DecisionPolicy {
    public static final String VERSION = "CALLING-STATION-0.1";

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        for (LegalAction action : observation.legalActions()) {
            if (action.type() == ActionType.CHECK)
                return new Decision(ActionType.CHECK, 0, VERSION, "Take the free check");
        }
        for (LegalAction action : observation.legalActions()) {
            if (action.type() == ActionType.CALL)
                return new Decision(ActionType.CALL, action.amount(), VERSION, "Match the current price");
        }
        LegalAction fallback = observation.legalActions().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No legal action"));
        return new Decision(fallback.type(), fallback.amount(), VERSION, "Use the only available legal action");
    }
}
