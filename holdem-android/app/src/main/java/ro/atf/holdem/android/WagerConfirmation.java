package ro.atf.holdem.android;

import ro.atf.holdem.core.LegalAction;

import java.util.function.Predicate;

/** Submits the chips entered in one open bet or raise editor. */
final class WagerConfirmation {
    private final LegalAction option;
    private final Predicate<LocalHoldemGame.Move> submit;
    private boolean accepted;

    WagerConfirmation(LegalAction option, Predicate<LocalHoldemGame.Move> submit) {
        this.option = option;
        this.submit = submit;
    }

    boolean confirm(CharSequence input) {
        if (accepted) return true;
        int amount;
        try {
            amount = Integer.parseInt(input.toString());
        } catch (NumberFormatException invalid) {
            return false;
        }
        if (amount < option.minAmount() || amount > option.maxAmount()) return false;
        accepted = submit.test(new LocalHoldemGame.Move(option.type(), amount));
        return accepted;
    }
}
