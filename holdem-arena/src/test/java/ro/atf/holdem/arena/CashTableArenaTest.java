package ro.atf.holdem.arena;

import atf.testkit.AtfComponentTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.ai.CallingStationPolicy;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.ai.DecisionPolicy;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.BlindLevel;

import static org.junit.jupiter.api.Assertions.*;

@AtfComponentTest
class CashTableArenaTest {
    @Test void cashSessionKeepsFixedBlindsRotatesButtonAndConservesChips() {
        var rules = new CashTableArena.Rules(4, 800, new BlindLevel(10, 20, 20));
        var policies = Collections.<DecisionPolicy>nCopies(4, new CallingStationPolicy());
        var trace = new StringWriter();

        var result = CashTableArena.play(rules, 1234L, 12, policies, 0, new PrintWriter(trace));

        assertEquals(12, result.handsPlayed());
        assertEquals(List.of(800, 800, 800, 800).stream().mapToInt(Integer::intValue).sum(),
                result.stacks().stream().mapToInt(Integer::intValue).sum());
        assertEquals(12, result.buttonSequence().size());
        assertEquals(List.of(2, 3, 0, 1, 2, 3, 0, 1, 2, 3, 0, 1), result.buttonSequence());
        assertEquals(12, trace.toString().lines().filter(line -> line.startsWith("CASH_HAND ")).count());
        assertTrue(trace.toString().contains("fixedBlinds=BlindLevel[smallBlind=10, bigBlind=20, minimumOpen=20]"));
        assertFalse(trace.toString().contains("levelIndex="));
        assertFalse(trace.toString().contains("TOURNAMENT_COMPLETE"));
    }

    @Test void supportsHeadsUpCashWithoutTournamentEliminationOrBlindEscalation() {
        var rules = new CashTableArena.Rules(2, 300, new BlindLevel(5, 10, 10));
        var policies = Collections.<DecisionPolicy>nCopies(2, new CallingStationPolicy());
        var result = CashTableArena.play(rules, 22L, 6, policies, 0, new PrintWriter(new StringWriter()));

        assertEquals(6, result.handsPlayed());
        assertTrue(result.stacks().stream().allMatch(stack -> stack > 0));
        assertEquals(600, result.stacks().stream().mapToInt(Integer::intValue).sum());
        assertThrows(IllegalArgumentException.class,
                () -> new CashTableArena.Rules(1, 300, new BlindLevel(5, 10, 10)));
    }

    @Test void cashSessionStopsWhenOnlyOneFundedSeatRemainsAndKeepsChips() {
        var rules = new CashTableArena.Rules(2, 50, new BlindLevel(5, 10, 10));
        var shove = (ro.atf.holdem.ai.DecisionPolicy) observation -> observation.legalActions().stream()
                .filter(action -> action.type() == ActionType.ALL_IN)
                .findFirst()
                .map(action -> new Decision(ActionType.ALL_IN, action.amount(), "TEST-SHOVE", "All-in stress policy"))
                .orElseGet(() -> observation.legalActions().stream()
                        .filter(action -> action.type() == ActionType.CALL || action.type() == ActionType.CHECK)
                        .findFirst()
                        .map(action -> new Decision(action.type(), action.amount(), "TEST-CALL", "Continue"))
                        .orElseGet(() -> new Decision(ActionType.FOLD, 0, "TEST-FOLD", "No continue action")));
        var result = CashTableArena.play(rules, 9, 100, List.of(shove, shove), 0,
                new PrintWriter(new StringWriter()));

        assertTrue(result.handsPlayed() < 100);
        assertEquals(100, result.stacks().stream().mapToInt(Integer::intValue).sum());
        assertEquals(1, result.stacks().stream().filter(stack -> stack > 0).count());
    }

    @Test void cashEquityCandidateSubstitutesOnlyTheSeededSlotAndRemainsCashMode() throws Exception {
        var policyPath = Path.of("config", "j6-policy.yaml");
        if (!Files.exists(policyPath)) policyPath = Path.of("..", "config", "j6-policy.yaml");
        var config = TournamentPolicyConfig.load(policyPath);
        for (int seats : List.of(4, 5, 6)) {
            for (int seedResidue = 0; seedResidue < 4; seedResidue++) {
                int candidateCount = 0;
                for (int seat = 0; seat < seats; seat++) {
                    var equity = CashTableArena.policyForSeat(seat, seedResidue, "cash-equity", config);
                    var control = CashTableArena.policyForSeat(seat, seedResidue, "cash-control", config);
                    boolean candidate = Math.floorMod(seat + seedResidue, 4) == 3;
                    if (candidate) {
                        candidateCount++;
                        assertInstanceOf(ro.atf.holdem.ai.ContextualEquityPolicy.class, equity);
                        var observed = new ro.atf.holdem.core.HoldemObservation(1, 0, 2, 1,
                                ro.atf.holdem.core.Street.RIVER,
                                List.of(ro.atf.holdem.core.Card.fromLabel("Ac"), ro.atf.holdem.core.Card.fromLabel("Ad")),
                                List.of(ro.atf.holdem.core.Card.fromLabel("Ah"), ro.atf.holdem.core.Card.fromLabel("Kh"),
                                        ro.atf.holdem.core.Card.fromLabel("Qh"), ro.atf.holdem.core.Card.fromLabel("Jh"),
                                        ro.atf.holdem.core.Card.fromLabel("Th")),
                                20, 0, 100, 0, List.of(100, 100),
                                List.of(ro.atf.holdem.core.LegalAction.noAmount(ActionType.CHECK)), List.of());
                        assertTrue(equity.decide(observed).policyVersion().contains("cash"));
                    } else {
                        assertEquals(control.getClass(), equity.getClass());
                    }
                }
                assertTrue(candidateCount > 0 && candidateCount <= 2);
            }
        }
        assertThrows(IllegalArgumentException.class,
                () -> CashTableArena.policyForSeat(0, 2, "unknown", config));
    }
}
