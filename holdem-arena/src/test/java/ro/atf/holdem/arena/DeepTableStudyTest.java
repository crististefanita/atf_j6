package ro.atf.holdem.arena;

import java.util.List;
import org.junit.jupiter.api.Test;
import ro.atf.holdem.ai.Decision;
import ro.atf.holdem.core.*;

import static org.junit.jupiter.api.Assertions.*;

class DeepTableStudyTest {
    @Test void changesStyleAtTheConfiguredHandAndPreservesFixedRoles() {
        var first = new PhaseStylePolicy.Phase(1, "open", o -> new Decision(ActionType.CHECK, 0, "first", "fixture"));
        var next = new PhaseStylePolicy.Phase(10, "switch", o -> new Decision(ActionType.CHECK, 0, "second", "fixture"));
        var dynamic = new PhaseStylePolicy("east", List.of(first, next));
        var fixed = new PhaseStylePolicy("anchor", List.of(first));
        assertEquals("east-open-first", dynamic.decide(flop(9).observeActor()).policyVersion());
        assertEquals("east-switch-second", dynamic.decide(flop(10).observeActor()).policyVersion());
        assertEquals("anchor-open-first", fixed.decide(flop(35).observeActor()).policyVersion());
        assertThrows(IllegalArgumentException.class, () -> new PhaseStylePolicy("bad", List.of(next, first)));
    }

    @Test void readPublishesOnlySettledComparableRepliesAndForgetsExpiredEvidence() {
        var read = new PublicBettingRead(2, 4);
        var first = reply(1);
        read.accepted(first, ActionType.FOLD, 0);
        assertEquals(0, read.estimate(0, 1, PublicBettingRead.Mode.CUMULATIVE, .5, 2).samples());
        read.settled(1);
        read.accepted(reply(5), ActionType.CALL, 20);
        read.settled(5);
        var cumulative = read.estimate(0, 6, PublicBettingRead.Mode.CUMULATIVE, .5, 2);
        var recent = read.estimate(0, 6, PublicBettingRead.Mode.RECENT, .5, 2);
        assertEquals(new PublicBettingRead.Estimate(2, .5), cumulative);
        assertEquals(new PublicBettingRead.Estimate(1, 1.0 / 3), recent);
        assertEquals(0, read.estimate(1, 6, PublicBettingRead.Mode.CUMULATIVE, .5, 2).samples());
        assertFalse(PublicBettingRead.facingOffer(flop(8).observeActor()));
    }

    @Test void supportedReadsAddOnlyALegalSmallBetAfterTheControlChecks() {
        var history = new PublicBettingRead(2, 20);
        var policy = new ReadBasedBetPolicy(o -> new Decision(ActionType.CHECK, 0, "base", "fixture"),
                history, PublicBettingRead.Mode.RECENT, .25, 4, 4, 8);
        for (int i = 1; i < 5; i++) {
            history.accepted(reply(i), ActionType.FOLD, 0);
            if (i == 4) assertEquals(ActionType.CHECK, policy.decide(flop(5).observeActor()).action());
            history.settled(i);
        }
        var hand = flop(5);
        var decision = policy.decide(hand.observeActor());
        assertEquals(ActionType.BET, decision.action());
        assertEquals(hand.observeActor().pot() / 2, decision.amount());
        hand.act(hand.actor(), decision.action(), decision.amount());
    }

    @Test void regimeReadUpdatesAfterSettlementAndRelaxesTowardThePriorWithoutReplies() {
        var history = new PublicBettingRead(2, 20, new PublicBettingRead.Regimes(.05, .75, .20, .10));
        history.accepted(reply(1), ActionType.FOLD, 0);
        assertEquals(.20, history.estimate(0, 1, PublicBettingRead.Mode.REGIME, .20, 8).fold(), 1e-12);
        history.settled(1);
        double recent = history.estimate(0, 2, PublicBettingRead.Mode.REGIME, .20, 8).fold();
        double stale = history.estimate(0, 30, PublicBettingRead.Mode.REGIME, .20, 8).fold();
        assertTrue(recent > .50);
        assertTrue(stale > .20 && stale < recent);
        assertEquals(.20, history.estimate(1, 2, PublicBettingRead.Mode.REGIME, .20, 8).fold(), 1e-12);
    }

    private static HoldemObservation reply(int number) {
        var hand = flop(number);
        hand.act(hand.actor(), ActionType.BET, 20);
        var reply = hand.observeActor();
        assertTrue(PublicBettingRead.facingOffer(reply));
        return reply;
    }

    private static HoldemHand flop(int number) {
        var hand = new HoldemHand(number, List.of(1_000, 1_000), 0, new BlindLevel(10, 20, 20),
                HoldemHand.shuffledDeck(12));
        hand.act(hand.actor(), ActionType.CALL, 10);
        hand.act(hand.actor(), ActionType.CHECK, 0);
        return hand;
    }
}
