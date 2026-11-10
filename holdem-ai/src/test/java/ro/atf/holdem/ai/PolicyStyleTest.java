package ro.atf.holdem.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class PolicyStyleTest {
    @Test void callingStationChecksOrMatchesButNeverChoosesAVoluntaryRaise() {
        var policy = new CallingStationPolicy();
        var free = observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 100)));
        assertLegal(free, policy.decide(free));
        assertEquals(ActionType.CHECK, policy.decide(free).action());

        var facingPrice = observation(35, 20, 100, List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                new LegalAction(ActionType.RAISE, 40, 40, 200)));
        var decision = policy.decide(facingPrice);
        assertLegal(facingPrice, decision);
        assertEquals(ActionType.CALL, decision.action());
        assertEquals(20, decision.amount());
    }

    @Test void looseAggressiveUsesMinimumLegalPressureAndIsReplayable() {
        var policy = new LooseAggressivePolicy(100, 100, 35);
        var opening = observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 25, 25, 100)));
        var first = policy.decide(opening);
        assertEquals(first, policy.decide(opening));
        assertLegal(opening, first);
        assertEquals(ActionType.BET, first.action());
        assertEquals(25, first.amount());

        var raise = observation(20, 0, 100, List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                new LegalAction(ActionType.RAISE, 45, 45, 100)));
        var decision = policy.decide(raise);
        assertLegal(raise, decision);
        assertEquals(ActionType.RAISE, decision.action());
        assertEquals(45, decision.amount());
        assertNotEquals(decision.policyVersion(), new LooseAggressivePolicy(100, 0, 35)
                .decide(raise).policyVersion(), "Policy versions must expose YAML differences in the replay trace");
    }

    @Test void looseAggressiveAppliesPotPriceCapAndZeroFrequencySettings() {
        var policy = new LooseAggressivePolicy(0, 0, 50);
        var affordable = observation(30, 0, 100, List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 30, 30, 30),
                new LegalAction(ActionType.RAISE, 50, 50, 150)));
        assertEquals(ActionType.CALL, policy.decide(affordable).action());

        var expensive = observation(51, 0, 100, affordable.legalActions());
        assertEquals(ActionType.FOLD, policy.decide(expensive).action());

        var free = observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 100)));
        assertEquals(ActionType.CHECK, policy.decide(free).action());
        assertThrows(IllegalArgumentException.class, () -> new LooseAggressivePolicy(101, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> new LooseAggressivePolicy(0, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> new LooseAggressivePolicy(0, 0, 101));
    }

    @Test void potSizingScalesOnlyLegalPressureAndNeverTurnsItIntoAnAccidentalShove() {
        var base = new LooseAggressivePolicy(100, 100, 35);
        var halfPot = new PotSizedPolicy(base, 50);
        var opening = observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 500)));
        var bet = halfPot.decide(opening);
        assertLegal(opening, bet);
        assertEquals(ActionType.BET, bet.action());
        assertEquals(50, bet.amount(), "Open for half of the pot when that size is legal");

        var raise = observation(20, 0, 100, List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                new LegalAction(ActionType.RAISE, 45, 45, 500)));
        var raised = halfPot.decide(raise);
        assertLegal(raise, raised);
        assertEquals(80, raised.amount(), "Call 20, then raise by half of the 120-chip pot");

        var shortStack = new HoldemObservation(7, 2, 6, Street.FLOP, List.of(card("Kc"), card("Qd")),
                List.of(card("2c"), card("7h"), card("8d")), 100, 0, 40, 0,
                List.of(40, 40, 40, 40, 40, 40), List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 40)),
                List.of("FLOP [2c, 7h, 8d]"));
        var safe = halfPot.decide(shortStack);
        assertLegal(shortStack, safe);
        assertEquals(ActionType.BET, safe.action());
        assertEquals(20, safe.amount(), "Do not silently convert a pot-sized bet into a short-stack shove");
        assertEquals("POT-SIZED-50/" + LooseAggressivePolicy.VERSION + "-open100-raise100-call35-late0",
                safe.policyVersion());

        var overbet = new PotSizedPolicy(base, 150).decide(observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 20, 20, 200))));
        assertEquals(150, overbet.amount(), "The diagnostic profile can test a 150% pot pressure bet");
        assertThrows(IllegalArgumentException.class, () -> new PotSizedPolicy(base, 201));
    }

    @Test void looseOpenFrequencyControlsTheFirstPreflopRaiseBeforeReraiseFrequency() {
        var unraised = new HoldemObservation(2, 2, 6, Street.PREFLOP, List.of(card("Kc"), card("Qd")),
                List.of(), 100, 20, 200, 2, List.of(200, 200, 200, 200, 200, 200), List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 20, 20, 20),
                new LegalAction(ActionType.RAISE, 40, 40, 200)), List.of("SB 1 10", "BB 2 20"));
        var policy = new LooseAggressivePolicy(100, 0, 35);
        var firstAggression = policy.decide(unraised);
        assertEquals(ActionType.RAISE, firstAggression.action(),
                "An unraised preflop pot is an open, not a reraise");
        assertEquals(40, firstAggression.amount());

        var alreadyRaised = new HoldemObservation(2, 2, 6, Street.PREFLOP, unraised.holeCards(),
                List.of(), 160, 60, 200, 2, unraised.visibleStacks(), unraised.legalActions(),
                List.of("SB 1 10", "BB 2 20", "PREFLOP 0 RAISE 40"));
        var reraiser = new LooseAggressivePolicy(0, 100, 100).decide(alreadyRaised);
        assertEquals(ActionType.RAISE, reraiser.action(), "A response to prior aggression uses rerraise frequency");
    }

    @Test void looseOpeningFrequencyIsWeightedByPublicTablePositionAndClamped() {
        var policy = new LooseAggressivePolicy(50, 15, 35, 20);
        assertEquals(70, policy.openingFrequencyFor(observation(0, 0, 100, List.of())),
                "Button is a late position");
        assertEquals(70, policy.openingFrequencyFor(observation(0, 5, 100, List.of())),
                "Cutoff is the other late position in a six-seat table");
        assertEquals(30, policy.openingFrequencyFor(observation(0, 1, 100, List.of())),
                "Small blind is early after the flop");
        assertEquals(100, new LooseAggressivePolicy(95, 15, 35, 20)
                .openingFrequencyFor(observation(0, 0, 100, List.of())), "Late adjustment is clamped");
        assertEquals(0, new LooseAggressivePolicy(5, 15, 35, 20)
                .openingFrequencyFor(observation(0, 1, 100, List.of())), "Early adjustment is clamped");
        assertThrows(IllegalArgumentException.class, () -> new LooseAggressivePolicy(50, 15, 35, 51));
    }

    @Test void looseOpeningAndReraiseFrequenciesAreScopedToTheCurrentStreet() {
        var board = List.of(card("2c"), card("7h"), card("8d"));
        var facingPrice = List.of(LegalAction.noAmount(ActionType.FOLD),
                new LegalAction(ActionType.CALL, 20, 20, 20), new LegalAction(ActionType.RAISE, 40, 40, 200));
        var newPostflopStreet = new HoldemObservation(3, 2, 6, Street.FLOP, List.of(card("Kc"), card("Qd")),
                board, 100, 20, 200, 2, List.of(200, 200, 200, 200, 200, 200), facingPrice,
                List.of("PREFLOP 0 RAISE 40", "FLOP [2c, 7h, 8d]"));
        assertEquals(ActionType.RAISE, new LooseAggressivePolicy(100, 0, 35)
                .decide(newPostflopStreet).action(), "Prior-street aggression does not turn a new street into a reraise");

        var postflopReraise = new HoldemObservation(3, 2, 6, Street.FLOP, newPostflopStreet.holeCards(),
                board, 140, 20, 200, 2, newPostflopStreet.visibleStacks(), facingPrice,
                List.of("PREFLOP 0 RAISE 40", "FLOP [2c, 7h, 8d]", "FLOP 1 BET 20"));
        assertEquals(ActionType.CALL, new LooseAggressivePolicy(0, 0, 100)
                .decide(postflopReraise).action(), "The reraise frequency is zero, so a checked call is preferred");
        assertEquals(ActionType.RAISE, new LooseAggressivePolicy(0, 100, 100)
                .decide(postflopReraise).action(), "A postflop raise after a bet uses reraise frequency");
    }

    @Test void pressureSelectsTheSmallestLegalOpenAndOnlyCallsShortAllInWithHighCards() {
        var policy = new PressurePolicy();
        var opening = observation(0, 0, 100, List.of(
                LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 30, 30, 100)));
        assertEquals(new Decision(ActionType.BET, 30, PressurePolicy.VERSION,
                "Apply minimum legal opening pressure"), policy.decide(opening));

        var shortAllIn = new HoldemObservation(8, 2, 6, Street.PREFLOP,
                List.of(card("Ac"), card("Kh")), List.of(), 80, 10, 10, 2,
                List.of(100, 100, 10, 100, 100, 100), List.of(
                LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 10, 10, 10),
                new LegalAction(ActionType.ALL_IN, 10, 10, 10)), List.of());
        assertEquals(ActionType.ALL_IN, policy.decide(shortAllIn).action());

        var weakShortAllIn = new HoldemObservation(8, 2, 6, Street.PREFLOP,
                List.of(card("4c"), card("5h")), List.of(), 80, 10, 10, 2,
                List.of(100, 100, 10, 100, 100, 100), shortAllIn.legalActions(), List.of());
        assertEquals(ActionType.CALL, policy.decide(weakShortAllIn).action());
    }

    private static HoldemObservation observation(int toCall, int position, int pot, List<LegalAction> legal) {
        return new HoldemObservation(7, 2, 6, Street.FLOP, List.of(card("Kc"), card("Qd")),
                List.of(card("2c"), card("7h"), card("8d")), pot, toCall, 200, position,
                List.of(200, 200, 200, 200, 200, 200), legal, List.of("FLOP [2c, 7h, 8d]"));
    }

    private static void assertLegal(HoldemObservation observation, Decision decision) {
        assertTrue(observation.legalActions().stream().anyMatch(action -> action.type() == decision.action()
                && decision.amount() >= action.minAmount() && decision.amount() <= action.maxAmount()),
                () -> "Illegal decision " + decision + " for " + observation.legalActions());
    }

    private static Card card(String label) {
        char rank = label.charAt(0), suit = label.charAt(1);
        Card.Rank cardRank = switch (rank) {
            case '2' -> Card.Rank.TWO; case '3' -> Card.Rank.THREE; case '4' -> Card.Rank.FOUR;
            case '5' -> Card.Rank.FIVE; case '6' -> Card.Rank.SIX; case '7' -> Card.Rank.SEVEN;
            case '8' -> Card.Rank.EIGHT; case '9' -> Card.Rank.NINE; case 'T' -> Card.Rank.TEN;
            case 'J' -> Card.Rank.JACK; case 'Q' -> Card.Rank.QUEEN; case 'K' -> Card.Rank.KING;
            case 'A' -> Card.Rank.ACE; default -> throw new IllegalArgumentException(label);
        };
        Card.Suit cardSuit = switch (suit) {
            case 'c' -> Card.Suit.CLUBS; case 'd' -> Card.Suit.DIAMONDS;
            case 'h' -> Card.Suit.HEARTS; case 's' -> Card.Suit.SPADES;
            default -> throw new IllegalArgumentException(label);
        };
        return new Card(cardRank, cardSuit);
    }
}
