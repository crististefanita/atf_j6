package ro.atf.holdem.ai;

import java.util.List;
import java.util.Objects;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemObservation;

final class PolicySeed {
    private PolicySeed() {}

    static int cardsHash(List<Card> cards) {
        int hash = 1;
        for (Card card : cards) {
            // Rank and suit values are stable across JVMs. Enum hashCode uses object identity.
            int cardId = card.rank().value() * 4 + card.suit().ordinal();
            hash = 31 * hash + cardId;
        }
        return hash;
    }

    static int rollPercent(HoldemObservation observation) {
        return Math.floorMod(Objects.hash(observation.handNumber(), observation.heroSeat(),
                observation.positionFromButton(), observation.street().ordinal(), cardsHash(observation.holeCards()),
                cardsHash(observation.communityCards()), observation.publicActionHistory()), 100);
    }
}
