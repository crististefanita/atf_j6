package ro.atf.holdem.arena;

import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.core.HoldemObservation;

import java.util.Objects;

public final class ArenaRun {
    private final DecisionPolicy policy;

    public ArenaRun(DecisionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public Decision decide(HoldemObservation observation) {
        return policy.decide(observation);
    }
}
