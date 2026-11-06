package ro.atf.holdem.ai;

import ro.atf.holdem.core.ActionType;

public record Decision(ActionType action, int amount, String policyVersion, String explanation) {
    public Decision {
        if (amount < 0) throw new IllegalArgumentException("Negative amount");
        if (policyVersion == null || policyVersion.isBlank()) throw new IllegalArgumentException("Policy version required");
        if (explanation == null || explanation.isBlank()) throw new IllegalArgumentException("Explanation required");
    }
}
