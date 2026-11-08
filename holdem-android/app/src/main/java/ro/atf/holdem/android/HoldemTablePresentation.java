package ro.atf.holdem.android;

import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.BettingAction;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.Street;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Hold'em-specific identities layered over the shared seat frame and card motion primitives. */
final class HoldemTablePresentation {
    private HoldemTablePresentation() { }

    record ActionRow(int ordinal, String player, Street street, BettingAction.Kind kind,
                     int amount, int totalContribution) { }
    record SeatFactStyle(int labelSp, int valueSp, int labelColor, int valueColor) { }
    record LedgerStyle(int textSp, int textColor, int borderColor) { }

    static int terminalCardHeightDp(boolean compact) {
        return compact ? 56 : ro.cardtable.ui.hand.HandLayoutMetrics.cardHeightDp();
    }

    static SeatFactStyle seatFactStyle(boolean active, boolean compact) {
        return new SeatFactStyle(compact ? 8 : 9, compact ? 10 : 11,
                active ? 0xff788274 : 0xff9fc0ad,
                active ? 0xff29362e : 0xffd3efe2);
    }

    static LedgerStyle ledgerStyle(boolean compact) {
        return new LedgerStyle(compact ? 11 : 12, 0xfff1f5f2, 0x5584b497);
    }

    static boolean activeSeat(HoldemTableViewState frame, int physicalSeat) {
        return frame.actor() >= 0 && physicalSeat == frame.actor();
    }

    static String seatHeading(String player, String role, boolean folded, boolean eliminated,
                              String foldBadge, String outBadge) {
        String heading = role.isEmpty() ? player : player + " · " + role;
        if (eliminated) return player + " · " + outBadge;
        return folded ? heading + " · " + foldBadge : heading;
    }

    static String handCardTag(String identity) { return "hand-card:" + identity; }
    static String boardCardTag(int index) { return "settlement-card:board:" + index; }
    static String opponentCardTag(int seat, int index) { return "settlement-card:seat:" + seat + ":" + index; }

    static List<ActionRow> actionRows(HoldemTableViewState frame, List<String> names) {
        if (names.size() != frame.seats().size()) throw new IllegalArgumentException("One display name is required per seat");
        return Collections.unmodifiableList(frame.bettingActions().stream().map(action -> {
            String player = null;
            for (int display = 0; display < names.size(); display++) {
                if (frame.physicalSeat(display) == action.seat()) {
                    player = names.get(display);
                    break;
                }
            }
            if (player == null) throw new IllegalArgumentException("Action references a missing physical seat");
            return new ActionRow(action.ordinal(), player, action.street(), action.kind(), action.amount(),
                    action.totalContribution());
        }).collect(Collectors.toList()));
    }

    static ActionRow latestActionRow(HoldemTableViewState frame, List<String> names) {
        var rows = actionRows(frame, names);
        return rows.isEmpty() ? null : rows.get(rows.size() - 1);
    }

    static Set<Card> winningCards(HoldemTableViewState frame) {
        var cards = new LinkedHashSet<Card>();
        for (var seat : frame.seats()) {
            if (seat.potWon() > 0 && seat.handEvaluation() != null) cards.addAll(seat.handEvaluation().bestFive());
        }
        return Set.copyOf(cards);
    }

    static Set<Card> boardCardsToHighlight(HoldemTableViewState frame) {
        var cards = new LinkedHashSet<>(winningCards(frame));
        var hero = frame.seats().get(frame.viewer()).handEvaluation();
        if (hero != null) cards.addAll(hero.bestFive());
        return Set.copyOf(cards);
    }

    static boolean isWinningCard(HoldemTableViewState frame, int physicalSeat, Card card) {
        var seat = frame.seats().get(physicalSeat);
        return seat.potWon() > 0 && seat.handEvaluation() != null && seat.handEvaluation().bestFive().contains(card);
    }

    static List<Integer> awardSeats(HoldemTableViewState frame) {
        var seats = new ArrayList<Integer>();
        for (int seat = 0; seat < frame.seats().size(); seat++)
            if (frame.seats().get(seat).potWon() > 0) seats.add(seat);
        return List.copyOf(seats);
    }
}
