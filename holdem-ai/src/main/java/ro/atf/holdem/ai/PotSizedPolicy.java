package ro.atf.holdem.ai;

import java.util.Objects;
import ro.atf.holdem.core.*;

/** Applies an explicit pot-relative size to an existing decision without seeing hidden cards. */
public final class PotSizedPolicy implements DecisionPolicy {
    private final DecisionPolicy base;
    private final int potPercent;

    public PotSizedPolicy(DecisionPolicy base, int potPercent) {
        this.base = Objects.requireNonNull(base, "base");
        if (potPercent < 0 || potPercent > 200)
            throw new IllegalArgumentException("Pot sizing must be 0..200 percent");
        this.potPercent = potPercent;
    }

    @Override
    public Decision decide(HoldemObservation observation) {
        Objects.requireNonNull(observation, "observation");
        Decision decision = base.decide(observation);
        if (potPercent == 0)
            return decision;
        if (decision.action() != ActionType.BET && decision.action() != ActionType.RAISE)
            return tagged(decision, decision.explanation());

        LegalAction legal = observation.legalActions().stream()
                .filter(action -> action.type() == decision.action())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Base policy returned a non-legal pressure action"));
        long potAfterCall = (long) observation.pot() + observation.toCall();
        long minimumIncrement = decision.action() == ActionType.BET
                ? legal.minAmount()
                : (long) legal.minAmount() - observation.toCall();
        long requestedIncrement = Math.max(minimumIncrement, potAfterCall * potPercent / 100);
        long target = decision.action() == ActionType.BET
                ? requestedIncrement
                : observation.toCall() + requestedIncrement;

        // A rounded pot size that cannot fit is declined instead of being clamped into an
        // accidental all-in. The base policy's smaller legal decision remains authoritative.
        if (target <= decision.amount())
            return tagged(decision, decision.explanation() + "; calculated pot size does not exceed the base size");
        if (target > legal.maxAmount() || target >= observation.heroStack())
            return tagged(decision, decision.explanation() + "; retain the base size rather than force an unsupported all-in");
        return new Decision(decision.action(), Math.toIntExact(target), version(decision),
                "Size pressure to " + potPercent + "% of the pot after call; stay below the effective stack");
    }

    private Decision tagged(Decision decision, String explanation) {
        return new Decision(decision.action(), decision.amount(), version(decision), explanation);
    }

    private String version(Decision decision) {
        return "POT-SIZED-" + potPercent + "/" + decision.policyVersion();
    }
}
