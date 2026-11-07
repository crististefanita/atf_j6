package ro.atf.holdem.core;

import java.util.List;
import java.util.Collections;
import java.util.Objects;

/** Information visible to one actor. It deliberately has no opponent hole cards or dealer seed. */
public record HoldemObservation(
        int handNumber,
        int heroSeat,
        int playerCount,
        int activeOpponentCount,
        Street street,
        List<Card> holeCards,
        List<Card> communityCards,
        int pot,
        int toCall,
        int heroStack,
        int positionFromButton,
        List<Integer> visibleStacks,
        List<LegalAction> legalActions,
        List<String> publicActionHistory,
        int physicalHeroSeat,
        int bigBlind,
        List<Integer> visibleContributions) {
    public HoldemObservation(int handNumber, int heroSeat, int playerCount, int activeOpponentCount, Street street,
                             List<Card> holeCards, List<Card> communityCards, int pot, int toCall, int heroStack,
                             int positionFromButton, List<Integer> visibleStacks, List<LegalAction> legalActions,
                             List<String> publicActionHistory, int physicalHeroSeat) {
        this(handNumber, heroSeat, playerCount, activeOpponentCount, street, holeCards, communityCards, pot, toCall,
                heroStack, positionFromButton, visibleStacks, legalActions, publicActionHistory, physicalHeroSeat,
                1, Collections.nCopies(playerCount, 0));
    }

    public HoldemObservation(int handNumber, int heroSeat, int playerCount, int activeOpponentCount, Street street,
                             List<Card> holeCards, List<Card> communityCards, int pot, int toCall, int heroStack,
                             int positionFromButton, List<Integer> visibleStacks, List<LegalAction> legalActions,
                             List<String> publicActionHistory) {
        this(handNumber, heroSeat, playerCount, activeOpponentCount, street, holeCards, communityCards, pot, toCall,
                heroStack, positionFromButton, visibleStacks, legalActions, publicActionHistory, heroSeat);
    }

    public HoldemObservation(int handNumber, int heroSeat, int playerCount, Street street,
                             List<Card> holeCards, List<Card> communityCards, int pot, int toCall,
                             int heroStack, int positionFromButton, List<Integer> visibleStacks,
                             List<LegalAction> legalActions, List<String> publicActionHistory) {
        this(handNumber, heroSeat, playerCount, Math.max(0, playerCount - 1), street, holeCards,
                communityCards, pot, toCall, heroStack, positionFromButton, visibleStacks,
                legalActions, publicActionHistory, heroSeat);
    }

    public HoldemObservation {
        if (handNumber < 0 || heroSeat < 0 || playerCount < 2 || heroSeat >= playerCount
                || activeOpponentCount < 0 || activeOpponentCount > playerCount - 1 || physicalHeroSeat < 0)
            throw new IllegalArgumentException("Invalid table identity or active-opponent count");
        Objects.requireNonNull(street, "street");
        holeCards = List.copyOf(holeCards);
        communityCards = List.copyOf(communityCards);
        visibleStacks = List.copyOf(visibleStacks);
        legalActions = List.copyOf(legalActions);
        publicActionHistory = List.copyOf(publicActionHistory);
        visibleContributions = List.copyOf(visibleContributions);
        if (holeCards.size() > 2 || communityCards.size() > 5) throw new IllegalArgumentException("Card visibility bounds");
        if (pot < 0 || toCall < 0 || heroStack < 0 || bigBlind < 1) throw new IllegalArgumentException("Invalid chip value");
        if (visibleStacks.size() != playerCount || visibleContributions.size() != playerCount)
            throw new IllegalArgumentException("Visible stack/contribution count");
        if (visibleStacks.stream().anyMatch(value -> value < 0)
                || visibleContributions.stream().anyMatch(value -> value < 0))
            throw new IllegalArgumentException("Negative visible stack or contribution");
    }
}
