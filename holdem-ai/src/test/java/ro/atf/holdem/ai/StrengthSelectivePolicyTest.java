package ro.atf.holdem.ai;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;
import ro.atf.holdem.core.Card;
import ro.atf.holdem.core.HoldemHand;

import static org.junit.jupiter.api.Assertions.*;

class StrengthSelectivePolicyTest {
    private final StrengthSelectivePolicy policy = new StrengthSelectivePolicy(StrengthSelectivePolicy.Style.NARROW);

    @Test void firstRaiseAndReraiseRequireHandStrength() {
        assertEquals(ActionType.RAISE, policy.decide(hand("As", "Ad").observeActor()).action());
        assertEquals(ActionType.FOLD, policy.decide(hand("7s", "2d").observeActor()).action());
        assertEquals(ActionType.FOLD, policy.decide(reply(45, "7s", "2d").observeActor()).action());

        var shortHand = reply(45, "As", "Ad");
        var shove = policy.decide(shortHand.observeActor());
        assertEquals(ActionType.ALL_IN, shove.action());
        assertTrue(shove.amount() > shortHand.observeActor().toCall());
        shortHand.act(shortHand.actor(), shove.action(), shove.amount());
    }

    @Test void mediumHandRespondsToPriceWithTheSameRandomDraw() {
        var cheap = reply(45, "Ah", "5c").observeActor();
        var expensive = reply(190, "Ah", "5c").observeActor();
        assertEquals(LooseAggressivePolicy.sharedPreflopRoll(cheap), LooseAggressivePolicy.sharedPreflopRoll(expensive));
        assertEquals(ActionType.CALL, policy.decide(cheap).action());
        assertEquals(ActionType.FOLD, policy.decide(expensive).action());
    }

    @Test void aCoveredPremiumCanCallButCannotReraise() {
        var covered = reply(220, "As", "Ad");
        var observation = covered.observeActor();
        assertEquals(observation.heroStack(), observation.toCall());
        var response = policy.decide(observation);
        assertEquals(ActionType.CALL, response.action());
        assertEquals(observation.heroStack(), response.amount());
        covered.act(covered.actor(), response.action(), response.amount());
    }

    private static HoldemHand reply(int amount, String first, String second) {
        var hand = hand(first, second);
        hand.act(hand.actor(), ActionType.RAISE, 30);
        hand.act(hand.actor(), ActionType.RAISE, amount);
        return hand;
    }

    private static HoldemHand hand(String first, String second) {
        var deck = new ArrayList<>(HoldemHand.shuffledDeck(21));
        var cards = List.of(Card.fromLabel(first), Card.fromLabel(second));
        deck.removeAll(cards);
        deck.add(1, cards.get(0));
        deck.add(3, cards.get(1));
        return new HoldemHand(1, List.of(240, 600), 0, new BlindLevel(10, 20, 20), deck);
    }
}
