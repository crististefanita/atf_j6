package ro.atf.holdem.arena;

import java.util.List;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.core.HoldemObservation;

final class PhaseStylePolicy implements DecisionPolicy {
    record Phase(int firstHand, String name, DecisionPolicy policy) {}

    private final String role;
    private final List<Phase> phases;

    PhaseStylePolicy(String role, List<Phase> phases) {
        this.role = role;
        this.phases = List.copyOf(phases);
        int last = 0;
        for (var phase : this.phases) {
            if (phase.firstHand() <= last || phase.name().isBlank() || phase.policy() == null)
                throw new IllegalArgumentException("Invalid phase for " + role);
            last = phase.firstHand();
        }
        if (this.phases.isEmpty() || this.phases.getFirst().firstHand() != 1)
            throw new IllegalArgumentException("A role must start in hand one");
    }

    Phase phase(int hand) {
        var selected = phases.getFirst();
        for (var phase : phases) {
            if (phase.firstHand() > hand) break;
            selected = phase;
        }
        return selected;
    }

    @Override public Decision decide(HoldemObservation observation) {
        var phase = phase(observation.handNumber());
        var decision = phase.policy().decide(observation);
        return new Decision(decision.action(), decision.amount(),
                role + "-" + phase.name() + "-" + decision.policyVersion(), decision.explanation());
    }
}
