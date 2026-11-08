package ro.atf.holdem.core;

import java.util.List;

/** What one viewer may see at the table. Seat identifiers are physical and stable. */
public record HoldemTableViewState(int viewer, int actor, int dealer, int smallBlind, int bigBlind, Street street, int pot,
                                   List<Card> ownCards, List<Card> board, List<Seat> seats,
                                   List<LegalAction> legalActions) {
    /** Presentation-only reveal; empty during a hand and populated after settlement. */
    public record Seat(int stack, int contributed, boolean folded, int received, boolean dealtCards,
                       List<Card> shownCards) {
        public Seat { shownCards = List.copyOf(shownCards); }
    }

    public HoldemTableViewState {
        ownCards = List.copyOf(ownCards);
        board = List.copyOf(board);
        seats = List.copyOf(seats);
        legalActions = List.copyOf(legalActions);
    }

    public int physicalSeat(int displayedSeat) {
        return (viewer + displayedSeat) % seats.size();
    }
}
