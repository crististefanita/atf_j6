package ro.atf.holdem.android;

import ro.atf.holdem.ai.MonteCarloEquityEstimator;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

import java.util.List;

/** Stable sampling input from the human's table projection, independent of whose turn it is. */
record HoldemWinChance(List<Card> ownCards, List<Card> board, int opponents) {
    private static final int SAMPLES = 256;

    static HoldemWinChance from(HoldemTableViewState frame) {
        var viewer = frame.seats().get(frame.viewer());
        if (frame.street() == Street.COMPLETE || viewer.folded() || frame.ownCards().size() != 2) return null;
        int opponents = 0;
        for (int seat = 0; seat < frame.seats().size(); seat++) {
            var opponent = frame.seats().get(seat);
            if (seat != frame.viewer() && opponent.dealtCards() && !opponent.folded()) opponents++;
        }
        return opponents == 0 ? null : new HoldemWinChance(frame.ownCards(), frame.board(), opponents);
    }

    int estimatePercent() {
        long seed = opponents;
        for (Card card : ownCards) seed = 31 * seed + card.label().hashCode();
        for (Card card : board) seed = 31 * seed + card.label().hashCode();
        var result = new MonteCarloEquityEstimator().estimate(ownCards, board, opponents, SAMPLES, seed);
        return (int) Math.round(100 * result.winProbability());
    }
}
