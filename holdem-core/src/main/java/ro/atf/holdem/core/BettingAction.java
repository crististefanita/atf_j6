package ro.atf.holdem.core;

import java.util.Objects;

/** One public chip movement or decision, in the order it happened during a hand. */
public record BettingAction(int ordinal, Street street, int seat, Kind kind, int amount, int totalContribution) {
    public enum Kind {
        SMALL_BLIND, BIG_BLIND, FOLD, CHECK, CALL, BET, RAISE, ALL_IN
    }

    public BettingAction {
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(kind, "kind");
        if (ordinal < 1 || seat < 0 || amount < 0 || totalContribution < 0 || totalContribution < amount)
            throw new IllegalArgumentException("Invalid public betting action");
        if ((kind == Kind.FOLD || kind == Kind.CHECK) && amount != 0)
            throw new IllegalArgumentException("Fold and check cannot move chips");
    }
}
