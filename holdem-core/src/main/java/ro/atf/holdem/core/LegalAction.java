package ro.atf.holdem.core;

import java.util.Objects;

public record LegalAction(ActionType type, int amount, int minAmount, int maxAmount) {
    public LegalAction {
        Objects.requireNonNull(type, "type");
        if (amount < 0 || minAmount < 0 || maxAmount < minAmount) throw new IllegalArgumentException("Invalid action bounds");
    }

    public static LegalAction noAmount(ActionType type) {
        return new LegalAction(type, 0, 0, 0);
    }
}
