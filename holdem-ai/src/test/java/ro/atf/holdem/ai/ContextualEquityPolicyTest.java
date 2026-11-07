package ro.atf.holdem.ai;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class ContextualEquityPolicyTest {
    @Test void potOddsAndEquityChooseCallOrFoldRatherThanBlindCalling() {
        var policy = policy(ContextualEquityPolicy.GameMode.CASH);
        var strong = observation(List.of(card("Ac"), card("Ad")),
                List.of(card("2c"), card("3d"), card("7h"), card("9s"), card("Jc")),
                1, 100, 20, List.of(LegalAction.noAmount(ActionType.FOLD),
                        new LegalAction(ActionType.CALL, 20, 20, 20)));
        var weak = observation(List.of(card("2s"), card("3s")),
                List.of(card("Ah"), card("Kd"), card("Qh"), card("Js"), card("9c")),
                1, 40, 100, List.of(LegalAction.noAmount(ActionType.FOLD),
                        new LegalAction(ActionType.CALL, 100, 100, 100)));

        assertEquals(ActionType.CALL, policy.decide(strong).action());
        assertEquals(ActionType.FOLD, policy.decide(weak).action());
        assertTrue(policy.decide(strong).explanation().contains("equity="));
        assertTrue(policy.decide(strong).explanation().contains("potOdds="));
    }

    @Test void strongMadeEquityCanValueBetButNeverSilentlyShove() {
        var policy = policy(ContextualEquityPolicy.GameMode.CASH);
        var deep = observation(List.of(card("Ks"), card("Kd")),
                List.of(card("Ac"), card("Ad"), card("Ah"), card("As"), card("2c")),
                1, 100, 0, List.of(LegalAction.noAmount(ActionType.CHECK),
                        new LegalAction(ActionType.BET, 20, 20, 500)));
        var shortStack = observation(List.of(card("Ks"), card("Kd")),
                List.of(card("Ac"), card("Ad"), card("Ah"), card("As"), card("2c")),
                1, 100, 0, List.of(LegalAction.noAmount(ActionType.CHECK),
                        new LegalAction(ActionType.BET, 20, 20, 40)));

        var valueBet = policy.decide(deep);
        var shortStackDecision = policy.decide(shortStack);
        assertEquals(ActionType.BET, valueBet.action());
        assertTrue(valueBet.amount() > 20 && valueBet.amount() < deep.heroStack());
        assertEquals(ActionType.BET, shortStackDecision.action());
        assertEquals(20, shortStackDecision.amount());
    }

    @Test void tournamentRiskMarginIsMoreConservativeThanCashForTheSameObservation() {
        var observation = observation(List.of(card("Ac"), card("Ad")), List.of(), 1, 100, 30,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 30, 30, 30)));
        var cashPolicy = policy(ContextualEquityPolicy.GameMode.CASH);
        var tournamentPolicy = policy(ContextualEquityPolicy.GameMode.TOURNAMENT);
        var cash = cashPolicy.decide(observation);
        var tournament = tournamentPolicy.decide(observation);

        assertEquals(ActionType.CALL, cash.action());
        assertEquals(ActionType.CALL, tournament.action());
        assertTrue(tournamentPolicy.requiredEquityPercent(observation)
                        > cashPolicy.requiredEquityPercent(observation),
                "Tournament policy must apply a larger decision margin to identical public state");
    }

    @Test void onlyAnOpponentsVisibleAggressionRaisesTheCallRiskMargin() {
        var policy = policy(ContextualEquityPolicy.GameMode.CASH);
        var ownRaise = observation(List.of(card("Ac"), card("Ad")), List.of(), 1, 100, 30,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 30, 30, 30)),
                List.of("PREFLOP 0 RAISE 30"), 0);
        var opponentRaise = observation(List.of(card("Ac"), card("Ad")), List.of(), 1, 100, 30,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 30, 30, 30)),
                List.of("PREFLOP 1 RAISE 30"), 0);

        assertEquals(policy.requiredEquityPercent(ownRaise) + 3,
                policy.requiredEquityPercent(opponentRaise));
    }

    @Test void yamlContextWeightsCanScaleMultiwayAndPositionRisk() {
        var observation = observation(List.of(card("Ac"), card("Ad")), List.of(), 3, 100, 30,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.CALL, 30, 30, 30)));
        var baseline = policy(ContextualEquityPolicy.GameMode.CASH);
        var tuned = new ContextualEquityPolicy(2048, 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.CASH,
                new ContextualEquityPolicy.ContextWeights(7, 4, 6, 9, 5, 1, 8, 2, 4, 5, 200));

        assertTrue(tuned.requiredEquityPercent(observation) > baseline.requiredEquityPercent(observation));
    }

    @Test void potSizedPublicRaiseAddsASeparatePressurePremium() {
        var quiet = publicPreflopObservation(List.of("SEATS [0, 1]", "SB 0 10", "BB 1 20"),
                110, 90, 500, List.of(10, 100), List.of(500, 700));
        var potSizedRaise = publicPreflopObservation(List.of("SEATS [0, 1]", "SB 0 10", "BB 1 20",
                        "PREFLOP 1 RAISE 80"),
                110, 90, 500, List.of(10, 100), List.of(500, 700));
        var policy = policy(ContextualEquityPolicy.GameMode.CASH);

        assertEquals(6, policy.requiredEquityPercent(potSizedRaise) - policy.requiredEquityPercent(quiet),
                "The public raise adds one aggression margin and one pot-sized-pressure margin");
    }

    @Test void coveredAllInCommitmentChangesTournamentCallRiskButNotCashRisk() {
        var allIn = publicPreflopObservation(List.of("SEATS [0, 1]", "SB 0 10", "BB 1 20",
                        "PREFLOP 1 ALL_IN 280"),
                310, 290, 100, List.of(10, 300), List.of(100, 0));
        var baselineWeights = new ContextualEquityPolicy.ContextWeights(
                3, 3, 2, 5, 2, 2, 3, 3, 2, 2, 164, 0, 0, 0, 0, 75);
        var stackAwareWeights = ContextualEquityPolicy.ContextWeights.defaults();
        var baseline = new ContextualEquityPolicy(256, 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.TOURNAMENT, baselineWeights);
        var stackAware = new ContextualEquityPolicy(256, 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.TOURNAMENT, stackAwareWeights);
        var cashBaseline = new ContextualEquityPolicy(256, 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.CASH, baselineWeights);
        var cashAware = new ContextualEquityPolicy(256, 2, 8, 18, 60,
                ContextualEquityPolicy.GameMode.CASH, stackAwareWeights);

        assertEquals(12, stackAware.requiredEquityPercent(allIn) - baseline.requiredEquityPercent(allIn),
                "Covered commitment adds large-bet, all-in, and elimination risk, offset by short-stack urgency");
        assertEquals(9, cashAware.requiredEquityPercent(allIn) - cashBaseline.requiredEquityPercent(allIn),
                "Cash keeps public pressure premiums but does not apply tournament survival penalties");
        assertTrue(stackAware.decide(allIn).explanation().contains("opponentAllIn=true"));
        assertTrue(stackAware.decide(allIn).explanation().contains("covered=true"));
        assertTrue(stackAware.decide(allIn).explanation().contains("shortStack=true"));
    }

    @Test void postflopPositionUsesOnlyPlayersWhoCanStillAct() {
        var policy = policy(ContextualEquityPolicy.GameMode.CASH);
        var early = livePositionObservation(1, 1, List.of("RIVER 0 FOLD 0"));
        var late = livePositionObservation(2, 2, List.of("RIVER 0 FOLD 0"));

        assertFalse(ContextualEquityPolicy.isLate(early));
        assertTrue(ContextualEquityPolicy.isLate(late));
        assertTrue(policy.valueBetThreshold(early) > policy.valueBetThreshold(late));
    }

    private static ContextualEquityPolicy policy(ContextualEquityPolicy.GameMode mode) {
        return new ContextualEquityPolicy(2048, 2, 8, 18, 60, mode);
    }

    private static HoldemObservation observation(List<Card> hole, List<Card> board, int opponents,
                                                  int pot, int toCall, List<LegalAction> legal) {
        return observation(hole, board, opponents, pot, toCall, legal, List.of(), 0);
    }

    private static HoldemObservation observation(List<Card> hole, List<Card> board, int opponents,
                                                  int pot, int toCall, List<LegalAction> legal,
                                                  List<String> history, int physicalHeroSeat) {
        int players = opponents + 1;
        return new HoldemObservation(33, 0, players, opponents, board.isEmpty() ? Street.PREFLOP : Street.RIVER,
                hole, board, pot, toCall, 1000, 0, Collections.nCopies(players, 1000), legal, history,
                physicalHeroSeat);
    }

    private static HoldemObservation livePositionObservation(int heroSeat, int position, List<String> history) {
        return new HoldemObservation(1, heroSeat, 4, 2, Street.RIVER, List.of(card("Ac"), card("Ad")),
                List.of(card("2c"), card("3d"), card("7h"), card("9s"), card("Jc")),
                100, 0, 100, position, List.of(100, 100, 100, 100),
                List.of(LegalAction.noAmount(ActionType.CHECK), new LegalAction(ActionType.BET, 10, 10, 100)),
                prependSeats(history), heroSeat);
    }

    private static HoldemObservation publicPreflopObservation(List<String> history, int pot, int toCall,
                                                               int heroStack, List<Integer> contributions,
                                                               List<Integer> stacks) {
        return new HoldemObservation(1, 0, 2, 1, Street.PREFLOP, List.of(card("Ac"), card("Ad")), List.of(),
                pot, toCall, heroStack, 0, stacks,
                List.of(LegalAction.noAmount(ActionType.FOLD), new LegalAction(ActionType.ALL_IN,
                        heroStack, heroStack, heroStack)), history, 0, 20, contributions);
    }

    private static List<String> prependSeats(List<String> history) {
        var events = new java.util.ArrayList<String>();
        events.add("SEATS [0, 1, 2, 3]");
        events.addAll(history);
        return List.copyOf(events);
    }

    private static Card card(String label) { return Card.fromLabel(label); }
}
