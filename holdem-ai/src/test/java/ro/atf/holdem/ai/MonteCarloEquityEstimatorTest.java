package ro.atf.holdem.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloEquityEstimatorTest {
    @Test void defaultSeedUsesStableVisibleCardValuesAndPublicHistory() {
        var observation = new HoldemObservation(11, 0, 2, 1, Street.FLOP,
                List.of(card("As"), card("Ad")), List.of(card("7c"), card("9h"), card("Js")), 40,
                0, 180, 0, List.of(180, 180), List.of(LegalAction.noAmount(ActionType.CHECK)),
                List.of("FLOP [7c, 9h, Js]", "FLOP 1 CHECK 0"));
        var estimator = new MonteCarloEquityEstimator();
        long expectedSeed = 0x85a6981de16fe4c0L;
        assertEquals(estimator.estimate(observation, 32, expectedSeed), estimator.estimate(observation, 32));
        assertEquals(estimator.estimate(observation, 513, expectedSeed), estimator.estimate(observation, 513));
        assertEquals(94, PolicySeed.rollPercent(observation));
    }

    @Test void aBoardRoyalFlushAlwaysSplitsThePotAndIsReproducible() {
        var observation = observation(List.of(card("2c"), card("3d")),
                List.of(card("Ah"), card("Kh"), card("Qh"), card("Jh"), card("Th")), 1);
        var estimator = new MonteCarloEquityEstimator();

        var first = estimator.estimate(observation, 256, 12345L);
        var second = estimator.estimate(observation, 256, 12345L);

        assertEquals(first, second);
        assertEquals(0.0, first.winProbability());
        assertEquals(1.0, first.tieProbability());
        assertEquals(0.5, first.expectedShare());
        assertEquals(0.0, first.standardError());
    }

    @Test void moreLiveOpponentsReducePremiumPreflopEquityOnTheSameSampleBudget() {
        var oneOpponent = observation(List.of(card("Ac"), card("Ad")), List.of(), 1);
        var fiveOpponents = observation(List.of(card("Ac"), card("Ad")), List.of(), 5);
        var estimator = new MonteCarloEquityEstimator();

        var headsUp = estimator.estimate(oneOpponent, 5000, 88L);
        var multiway = estimator.estimate(fiveOpponents, 5000, 88L);

        assertTrue(headsUp.expectedShare() > multiway.expectedShare() + 0.15,
                () -> "heads-up=" + headsUp + ", five-way=" + multiway);
    }

    @Test void validatesSamplesVisibleCardsAndOpponentCount() {
        var estimator = new MonteCarloEquityEstimator();
        var valid = observation(List.of(card("Ac"), card("Ad")), List.of(), 1);
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(valid, 0, 1L));
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(valid, 1_000_001, 1L));
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(
                observation(List.of(card("Ac"), card("Ad")), List.of(), 0), 10, 1L));
        assertThrows(IllegalArgumentException.class, () -> estimator.estimate(
                observation(List.of(card("Ac"), card("Ad")), List.of(card("Ac"), card("2h"), card("3d")), 1), 10, 1L));
    }

    private static HoldemObservation observation(List<Card> hole, List<Card> board, int opponents) {
        int players = opponents + 1;
        return new HoldemObservation(11, 0, players, opponents, Street.PREFLOP, hole, board,
                30, 10, 200, 0, java.util.Collections.nCopies(players, 200),
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 10, 10, 10)), List.of());
    }

    private static Card card(String label) {
        return Card.fromLabel(label);
    }
}
