package ro.atf.holdem.android;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;
import ro.cardtable.ui.screens.ScreenViews;

import java.util.List;

/** Renders the ordered betting history and keeps the focused entry visible in both scroll areas. */
final class BettingLedgerView {
    static final String LEDGER_ACTION_TAG = "betting-action:";
    static final String LEDGER_TOGGLE_TAG = "betting-history-toggle";
    static final String LEDGER_LIST_TAG = "betting-history-list";

    private final ScreenViews views;
    private final HoldemTableView.Text text;

    BettingLedgerView(ScreenViews views, HoldemTableView.Text text) {
        this.views = views;
        this.text = text;
    }

    void addTo(HoldemViewport viewport, LinearLayout parent, HoldemTableViewState frame,
                                  List<String> names, boolean compact, TableViewportPosition position) {
        var section = new LinearLayout(views.activity());
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(views.dp(8), views.dp(2), views.dp(8), views.dp(2));
        var title = views.text(text.get(R.string.betting_history_title), compact ? 10 : 11,
                HoldemTablePresentation.SECONDARY_TABLE_TEXT, true);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        title.setTag(LEDGER_TOGGLE_TAG);
        title.setClickable(true);
        title.setFocusable(true);
        title.setPadding(views.dp(4), views.dp(4), views.dp(4), views.dp(4));
        title.setContentDescription(text.get(position.historyExpanded()
                ? R.string.betting_history_hide : R.string.betting_history_show));
        title.setSelected(position.historyExpanded());
        section.addView(title, views.matchWrap());
        var history = new ScrollView(views.activity());
        var entries = new LinearLayout(views.activity());
        entries.setOrientation(LinearLayout.VERTICAL);
        var rows = HoldemTablePresentation.actionRows(frame, names);
        var style = HoldemTablePresentation.ledgerStyle(compact);
        for (var row : rows) {
            String label = text.get(actionLabel(row.kind()));
            int street = streetLabel(row.street());
            String entry = text.get(R.string.betting_ledger_entry, row.ordinal(), row.player(), label, row.amount(),
                    text.get(street), row.totalContribution());
            var line = views.text(entry, style.textSp(), style.textColor(), true);
            line.setTag(LEDGER_ACTION_TAG + row.ordinal());
            line.setPadding(views.dp(5), views.dp(2), views.dp(5), views.dp(2));
            line.setBackground(views.roundedBackground(0x1affffff, style.borderColor(), 4));
            line.setContentDescription(entry);
            line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            line.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                    if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()) {
                        revealLedgerRow(viewport, history, host);
                        return true;
                    }
                    boolean handled = super.performAccessibilityAction(host, action, arguments);
                    if (handled && action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                        revealLedgerRow(viewport, history, host);
                    }
                    return handled;
                }
            });
            entries.addView(line, views.matchWrap());
        }
        history.setTag(LEDGER_LIST_TAG);
        history.setContentDescription(text.get(R.string.betting_history_list_description));
        history.setFillViewport(false);
        history.setVerticalScrollBarEnabled(true);
        history.setNestedScrollingEnabled(true);
        history.addView(entries, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        section.addView(history, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                views.dp(compact ? 92 : 116)));
        history.setVisibility(position.historyExpanded() ? View.VISIBLE : View.GONE);
        HoldemTableView.restoreScrollOnLayout(history, position.historyY());
        title.setOnClickListener(ignored -> {
            boolean expanded = history.getVisibility() != View.VISIBLE;
            history.setVisibility(expanded ? View.VISIBLE : View.GONE);
            title.setContentDescription(text.get(expanded
                    ? R.string.betting_history_hide : R.string.betting_history_show));
            title.setSelected(expanded);
            if (expanded && entries.getChildCount() > 0) {
                View newest = entries.getChildAt(entries.getChildCount() - 1);
                HoldemViewport.afterLayout(history, () -> history.post(() -> {
                    if (history.isShown()) revealLedgerRow(viewport, history, newest);
                }));
            }
        });
        if (frame.bettingActions().isEmpty()) section.setVisibility(View.INVISIBLE);
        parent.addView(section, views.matchWrap());
    }

    private static void revealLedgerRow(HoldemViewport viewport, ScrollView history, View row) {
        HoldemViewport.revealWithin(history, row);
        HoldemViewport.revealWithin(viewport, row);
    }

    static int streetLabel(Street street) {
        return switch (street) {
            case PREFLOP -> R.string.street_preflop;
            case FLOP -> R.string.street_flop;
            case TURN -> R.string.street_turn;
            case RIVER -> R.string.street_river;
            case SHOWDOWN, COMPLETE -> R.string.settled;
        };
    }

    static int actionLabel(BettingAction.Kind action) {
        return switch (action) {
            case SMALL_BLIND -> R.string.ledger_small_blind;
            case BIG_BLIND -> R.string.ledger_big_blind;
            case FOLD -> R.string.fold;
            case CHECK -> R.string.check;
            case CALL -> R.string.call;
            case BET -> R.string.bet;
            case RAISE -> R.string.raise;
            case ALL_IN -> R.string.all_in;
        };
    }
}
