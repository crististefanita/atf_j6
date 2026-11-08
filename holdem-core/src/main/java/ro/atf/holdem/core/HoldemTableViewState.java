package ro.atf.holdem.core;

import java.util.List;

/** What one viewer may see at the table. Seat identifiers are physical and stable. */
public record HoldemTableViewState(int viewer, int actor, int dealer, int smallBlind, int bigBlind, Street street, int pot,
                                   List<Card> ownCards, List<Card> board, List<Seat> seats,
                                   List<LegalAction> legalActions, List<BettingAction> bettingActions) {
    public HoldemTableViewState(int viewer, int actor, int dealer, int smallBlind, int bigBlind, Street street, int pot,
                                List<Card> ownCards, List<Card> board, List<Seat> seats,
                                List<LegalAction> legalActions) {
        this(viewer, actor, dealer, smallBlind, bigBlind, street, pot, ownCards, board, seats, legalActions, List.of());
    }

    /** Presentation-only reveal; empty during a hand and populated after settlement. */
    public record Seat(int stack, int contributed, boolean folded, int received, int potWon, int returned,
                       boolean dealtCards, List<Card> shownCards, HandEvaluation handEvaluation) {
        public Seat(int stack, int contributed, boolean folded, int received, boolean dealtCards,
                    List<Card> shownCards) {
            this(stack, contributed, folded, received, received, 0, dealtCards, shownCards, null);
        }
        public Seat {
            shownCards = List.copyOf(shownCards);
            if (stack < 0 || contributed < 0 || received < 0 || potWon < 0 || returned < 0
                    || received != potWon + returned)
                throw new IllegalArgumentException("Invalid public seat amounts");
        }
    }

    public HoldemTableViewState {
        ownCards = List.copyOf(ownCards);
        board = List.copyOf(board);
        seats = List.copyOf(seats);
        legalActions = List.copyOf(legalActions);
        bettingActions = List.copyOf(bettingActions);
        if (!bettingActions.isEmpty()) {
            int[] totals = new int[seats.size()];
            for (int index = 0; index < bettingActions.size(); index++) {
                var action = bettingActions.get(index);
                if (action.ordinal() != index + 1 || action.seat() >= seats.size()
                        || action.totalContribution() < totals[action.seat()])
                    throw new IllegalArgumentException("Betting actions must be ordered and cumulative per physical seat");
                totals[action.seat()] = action.totalContribution();
            }
            for (int seat = 0; seat < seats.size(); seat++) {
                if (totals[seat] != seats.get(seat).contributed())
                    throw new IllegalArgumentException("Action ledger does not reconcile with seat contribution");
            }
        }
    }

    public int physicalSeat(int displayedSeat) {
        return (viewer + displayedSeat) % seats.size();
    }
}
