package ro.atf.holdem.core;

import java.util.List;
import java.util.Objects;

/** Information visible to one actor. It deliberately has no opponent hole cards or dealer seed. */
public record HoldemObservation(
        int handNumber,
        int heroSeat,
        int playerCount,
        Street street,
        List<Card> holeCards,
        List<Card> communityCards,
        int pot,
        int toCall,
        int heroStack,
        int positionFromButton,
        List<Integer> visibleStacks,
        List<LegalAction> legalActions,
        List<String> publicActionHistory) {
    public HoldemObservation {
        if (handNumber < 0 || heroSeat < 0 || playerCount < 2 || heroSeat >= playerCount) throw new IllegalArgumentException("Invalid table identity");
        Objects.requireNonNull(street, "street");
        Objects.requireNonNull(holeCards, "holeCards");
        Objects.requireNonNull(communityCards, "communityCards");
        Objects.requireNonNull(visibleStacks, "visibleStacks");
        Objects.requireNonNull(legalActions, "legalActions");
        Objects.requireNonNull(publicActionHistory, "publicActionHistory");
        if (holeCards.size() > 2 || communityCards.size() > 5) throw new IllegalArgumentException("Card visibility bounds");
        if (pot < 0 || toCall < 0 || heroStack < 0) throw new IllegalArgumentException("Negative chip value");
        if (visibleStacks.size() != playerCount) throw new IllegalArgumentException("Stack count");
    }
}
