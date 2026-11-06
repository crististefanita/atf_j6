package ro.atf.holdem.ai;

import ro.atf.holdem.core.HoldemObservation;

public interface DecisionPolicy {
    Decision decide(HoldemObservation observation);
}
