package ro.atf.holdem.android;

import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

/** Scroll and focus state carried between renderings of the same unfinished hand. */
record TableViewportPosition(int tableY, boolean historyExpanded, int historyY, String focusTag) {
    static final TableViewportPosition TOP = new TableViewportPosition(0, false, 0, null);

    static boolean sameOpenHand(LocalHoldemGame displayed, int displayedHand, HoldemTableViewState previous,
                                LocalHoldemGame current, HoldemTableViewState next) {
        return displayed == current && displayedHand == current.handNumber()
                && previous.street() != Street.COMPLETE && next.street() != Street.COMPLETE;
    }

    static TableViewportPosition capture(int tableY, boolean historyExpanded, int historyY, String focus,
                                         HoldemTableViewState previous, HoldemTableViewState next) {
        var actions = next.bettingActions();
        if (ActionType.CHECK.name().equals(focus) && actions.size() > previous.bettingActions().size()) {
            var last = actions.get(actions.size() - 1);
            if (last.seat() == next.viewer() && last.kind() == BettingAction.Kind.CHECK) {
                focus = SettlementAwardAnimator.SEAT_TAG + next.viewer();
            }
        }
        return new TableViewportPosition(tableY, historyExpanded, historyY, focus);
    }

    static boolean canRestoreFocus(String tag) {
        return tag != null && (tag.startsWith(SettlementAwardAnimator.SEAT_TAG)
                || tag.startsWith(BettingLedgerView.LEDGER_ACTION_TAG)
                || tag.equals(BettingLedgerView.LEDGER_TOGGLE_TAG));
    }
}
