package ro.atf.holdem.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicBluffPolicyTest {
    private static final DeterministicBluffPolicy POLICY = new DeterministicBluffPolicy();
    private static final List<Card> LOW_CARDS = List.of(card("2c"), card("3d"));
    private static final List<Card> ACE_BOARD = List.of(card("Ac"), card("7h"), card("8d"));
    private static final List<Card> NO_ACE_BOARD = List.of(card("Kc"), card("7h"), card("8d"));
    private static final List<LegalAction> OPENING = List.of(
            LegalAction.noAmount(ActionType.CHECK),
            new LegalAction(ActionType.BET, 25, 25, 200));

    @Test void noAceLatePositionBluffsAtConfiguredReproducibleFrequencyAndOnlyAtMinimum() {
        int bluffs = 0;
        for (int hand = 1; hand <= 100; hand++) {
            var observation = observation(hand, 0, 0, OPENING, ACE_BOARD,
                    List.of("FLOP [Ac, 7h, 8d]", "FLOP 3 CHECK 0"));
            var first = POLICY.decide(observation);
            var replay = POLICY.decide(observation);
            assertEquals(first, replay, "Same public/private actor observation must replay identically");
            assertEquals(DeterministicBluffPolicy.VERSION, first.policyVersion());
            if (first.action() == ActionType.BET) {
                bluffs++;
                assertEquals(25, first.amount(), "Bluff must use the smallest legal opening");
                assertTrue(first.explanation().contains("no ace in hole cards"));
            } else {
                assertEquals(ActionType.CHECK, first.action());
            }
        }
        assertTrue(Math.abs(bluffs - 18) <= 1,
                "A deterministic hash sample should remain within one event of 18% over 100 hands; got " + bluffs);
    }

    @Test void earlyPositionAndFacingAPriceNeverUseTheOpeningBluffBranch() {
        for (int hand = 1; hand <= 100; hand++) {
            var early = POLICY.decide(observation(hand, 3, 0, OPENING, ACE_BOARD,
                    List.of("FLOP [Ac, 7h, 8d]", "FLOP 3 CHECK 0")));
            assertEquals(ActionType.CHECK, early.action());
            assertTrue(early.explanation().startsWith("Fallback:"));

            var facingPrice = POLICY.decide(observation(hand, 4, 20, List.of(
                    LegalAction.noAmount(ActionType.FOLD),
                    new LegalAction(ActionType.CALL, 20, 20, 20),
                    new LegalAction(ActionType.RAISE, 40, 40, 200)), ACE_BOARD,
                    List.of("FLOP [Ac, 7h, 8d]", "FLOP 3 CHECK 0")));
            assertNotEquals(ActionType.BET, facingPrice.action());

            var noScareCard = POLICY.decide(observation(hand, 4, 0, OPENING, NO_ACE_BOARD,
                    List.of("FLOP [Kc, 7h, 8d]", "FLOP 3 CHECK 0")));
            assertEquals(ActionType.CHECK, noScareCard.action());
            var noChecksBeforeHero = POLICY.decide(observation(hand, 4, 0, OPENING, ACE_BOARD,
                    List.of("FLOP [Ac, 7h, 8d]")));
            assertEquals(ActionType.CHECK, noChecksBeforeHero.action());
        }
    }

    @Test void configuredFrequencyAndWindowAreHonoredButPrivateAceRemainsAGate() {
        var wideAlways = new DeterministicBluffPolicy(100, 3);
        var newlyIncludedPosition = observation(12, 0, 0, OPENING, ACE_BOARD,
                List.of("FLOP [Ac, 7h, 8d]", "FLOP 2 CHECK 0"));
        assertEquals(ActionType.BET, wideAlways.decide(newlyIncludedPosition).action());

        var withPrivateAce = new HoldemObservation(12, 3, 6, Street.FLOP,
                List.of(card("Ac"), card("3d")), ACE_BOARD, 40, 0, 200, 3,
                List.of(200, 200, 200, 200, 200, 200), OPENING,
                List.of("FLOP [Ac, 7h, 8d]", "FLOP 2 CHECK 0"));
        assertEquals(ActionType.CHECK, wideAlways.decide(withPrivateAce).action());

        var pairedWithBoard = new HoldemObservation(12, 3, 6, Street.FLOP,
                List.of(card("8c"), card("3d")), ACE_BOARD, 40, 0, 200, 3,
                List.of(200, 200, 200, 200, 200, 200), OPENING,
                List.of("FLOP [Ac, 7h, 8d]", "FLOP 2 CHECK 0"));
        assertEquals(ActionType.CHECK, wideAlways.decide(pairedWithBoard).action());

        var never = new DeterministicBluffPolicy(0, 3);
        assertEquals(ActionType.CHECK, never.decide(newlyIncludedPosition).action());
    }

    @Test void latePositionWindowIncludesButtonAndSeatsImmediatelyBeforeIt() {
        assertTrue(DeterministicBluffPolicy.isLatePosition(0, 6, 2), "Button acts last postflop");
        assertTrue(DeterministicBluffPolicy.isLatePosition(5, 6, 2), "Cutoff is the other seat in a two-seat window");
        assertFalse(DeterministicBluffPolicy.isLatePosition(4, 6, 2));
        assertTrue(DeterministicBluffPolicy.isLatePosition(4, 6, 3));
        assertTrue(DeterministicBluffPolicy.isLatePosition(0, 6, 6));
        assertTrue(DeterministicBluffPolicy.isLatePosition(1, 2, 6), "Configured window shrinks with the live table");
        assertThrows(IllegalArgumentException.class, () -> DeterministicBluffPolicy.isLatePosition(6, 6, 2));
    }

    private static HoldemObservation observation(int hand, int position, int toCall, List<LegalAction> legal,
                                                  List<Card> board, List<String> history) {
        return new HoldemObservation(hand, position, 6, Street.FLOP, LOW_CARDS, board, 40, toCall, 200,
                position, List.of(200, 200, 200, 200, 200, 200), legal, history);
    }

    private static Card card(String label) {
        char rank = label.charAt(0);
        char suit = label.charAt(1);
        Card.Rank cardRank = switch (rank) {
            case '2' -> Card.Rank.TWO;
            case '3' -> Card.Rank.THREE;
            case '4' -> Card.Rank.FOUR;
            case '5' -> Card.Rank.FIVE;
            case '6' -> Card.Rank.SIX;
            case '7' -> Card.Rank.SEVEN;
            case '8' -> Card.Rank.EIGHT;
            case '9' -> Card.Rank.NINE;
            case 'T' -> Card.Rank.TEN;
            case 'J' -> Card.Rank.JACK;
            case 'Q' -> Card.Rank.QUEEN;
            case 'K' -> Card.Rank.KING;
            case 'A' -> Card.Rank.ACE;
            default -> throw new IllegalArgumentException(label);
        };
        Card.Suit cardSuit = switch (suit) {
            case 'c' -> Card.Suit.CLUBS;
            case 'd' -> Card.Suit.DIAMONDS;
            case 'h' -> Card.Suit.HEARTS;
            case 's' -> Card.Suit.SPADES;
            default -> throw new IllegalArgumentException(label);
        };
        return new Card(cardRank, cardSuit);
    }
}
