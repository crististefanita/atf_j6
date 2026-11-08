package ro.atf.holdem.android;

import ro.atf.holdem.core.HoldemTableViewState;

import java.util.ArrayList;
import java.util.List;

/** Hold'em-specific identities layered over the shared seat frame and card motion primitives. */
final class HoldemTablePresentation {
    private HoldemTablePresentation() { }

    static boolean activeSeat(HoldemTableViewState frame, int physicalSeat) {
        return frame.actor() >= 0 && physicalSeat == frame.actor();
    }

    static String seatHeading(String player, String role, boolean folded, boolean eliminated,
                              String foldBadge, String outBadge) {
        String heading = role.isEmpty() ? player : player + " · " + role;
        if (eliminated) return player + " · " + outBadge;
        return folded ? heading + " · " + foldBadge : heading;
    }

    static List<String> collectionTags(HoldemTableViewState frame) {
        var tags = new ArrayList<String>(2 + frame.board().size() + frame.seats().size() * 2);
        for (var card : frame.ownCards()) tags.add(handCardTag(card.label()));
        for (int index = 0; index < frame.board().size(); index++) tags.add(boardCardTag(index));
        for (int seat = 0; seat < frame.seats().size(); seat++) {
            if (seat == frame.viewer()) continue;
            var cards = frame.seats().get(seat).shownCards();
            for (int index = 0; index < cards.size(); index++) tags.add(opponentCardTag(seat, index));
        }
        return List.copyOf(tags);
    }

    static String handCardTag(String identity) { return "hand-card:" + identity; }
    static String boardCardTag(int index) { return "settlement-card:board:" + index; }
    static String opponentCardTag(int seat, int index) { return "settlement-card:seat:" + seat + ":" + index; }
}
