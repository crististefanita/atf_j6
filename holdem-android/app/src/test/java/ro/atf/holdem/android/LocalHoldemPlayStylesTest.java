package ro.atf.holdem.android;

import org.junit.Test;
import ro.atf.holdem.ai.TournamentPlayStyles;
import ro.atf.holdem.ai.TournamentPlayStyles.Style;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.PublicShoveHistory;
import ro.atf.holdem.core.Street;
import ro.atf.holdem.core.Tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class LocalHoldemPlayStylesTest {
    @Test public void changingDelegationDuringAnOpponentTurnLeavesTheirWorkScheduled() {
        var autoPlay = new HandAutoPlay();
        var humanRefreshes = new ArrayList<Boolean>();
        Runnable refreshHumanTurn = () -> humanRefreshes.add(autoPlay.isEnabled());

        autoPlay.change(true, false, refreshHumanTurn);
        assertTrue(autoPlay.accepts(autoPlay.revision()));
        long previousRequest = autoPlay.revision();
        autoPlay.change(false, false, refreshHumanTurn);
        assertFalse(autoPlay.accepts(previousRequest));
        assertTrue(humanRefreshes.isEmpty());

        autoPlay.change(true, true, refreshHumanTurn);
        autoPlay.change(true, true, refreshHumanTurn);
        autoPlay.change(false, true, refreshHumanTurn);
        assertEquals(List.of(true, false), humanRefreshes);
    }

    @Test public void uncheckingAndRecheckingDoesNotAcceptTheOldDelegatedDecision() {
        var game = new LocalHoldemGame(4, 811);
        var delegation = new HandAutoPlay();
        assertFalse(delegation.isEnabled());
        assertTrue(delegation.setEnabled(true));
        var turn = game.turn();
        long oldRequest = delegation.revision();
        var move = new LocalHoldemGame.Move(ActionType.FOLD, 0);
        Runnable delayedResult = () -> {
            if (delegation.accepts(oldRequest)) game.playDelegated(turn, move);
        };

        delegation.setEnabled(false);
        delayedResult.run();
        assertEquals(turn, game.turn());
        delegation.setEnabled(true);
        delayedResult.run();
        assertTrue(game.save().rounds().get(0).isEmpty());
        assertEquals(turn, game.turn());
        assertTrue(delegation.accepts(delegation.revision()));
        game.playDelegated(turn, move);
        assertEquals(LocalHoldemGame.ActionSource.AUTO_PLAY, game.save().rounds().get(0).get(0).source());
        assertThrows(IllegalArgumentException.class, () -> game.playDelegated(turn, move));
    }

    @Test public void sixSeatHardRoutesEachOpponentIncludingNormalAndPreservesTheRosterOnRestore() {
        long seed = 418;
        var game = LocalHoldemGame.tournament(6, seed, 1, "Ana", false, LocalHoldemGame.Difficulty.HARD);
        var rules = LocalHoldemGame.tournamentRules(6, 1);
        var hand = new Tournament(rules, 0).startHand(0, new Random(seed).nextLong()).hand();
        int human = hand.actor();
        assertNotEquals(0, human);

        var call = new LocalHoldemGame.Move(ActionType.CALL, 20);
        game.play(game.turn(), call);
        hand.act(human, call.action(), call.amount());
        var roster = List.of(Style.BALANCED, Style.PRUDENT, Style.ACTIVE, Style.POSITIONAL, Style.NORMAL);
        var history = new PublicShoveHistory(6);
        for (int i = 0; i < roster.size(); i++) {
            int actor = hand.actor();
            assertEquals((human + i + 1) % 6, actor);
            assertEquals(actor, game.view().actor());
            var observation = hand.observeActor(history.snapshot());
            var decision = roster.get(i).policy(512).decide(observation);
            var expected = new LocalHoldemGame.Move(decision.action(), decision.amount());
            var token = game.turn();
            assertEquals(expected, game.suggestOpponent(token));
            if (roster.get(i) == Style.NORMAL) {
                assertEquals(new LocalHoldemGame.Move(ActionType.CHECK, 0), expected);
                var restored = LocalHoldemGame.restore(game.save());
                assertEquals(expected, restored.suggestOpponent(restored.turn()));
            }
            var checkOrCall = hand.legal().stream()
                    .filter(action -> action.type() == ActionType.CHECK || action.type() == ActionType.CALL)
                    .findFirst().orElseThrow();
            game.playOpponent(token, new LocalHoldemGame.Move(checkOrCall.type(), checkOrCall.amount()));
            hand.act(actor, checkOrCall.type(), checkOrCall.amount());
            assertThrows(IllegalArgumentException.class, () -> game.suggestOpponent(token));
        }
        assertEquals(Street.FLOP, game.view().street());
    }

    @Test public void normalSeatInHardCallsWhereTheBalancedOpponentWouldFold() {
        long seed = 1;
        var game = LocalHoldemGame.tournament(6, seed, 1, "Ana", false, LocalHoldemGame.Difficulty.HARD);
        game.play(game.turn(), new LocalHoldemGame.Move(ActionType.RAISE, 60));
        for (int amount : List.of(60, 60, 60, 50)) {
            game.playOpponent(game.turn(), new LocalHoldemGame.Move(ActionType.CALL, amount));
        }
        assertEquals(2, game.view().actor());
        var expected = new LocalHoldemGame.Move(ActionType.CALL, 40);
        assertEquals(expected, game.suggestOpponent(game.turn()));
        var restored = LocalHoldemGame.restore(game.save());
        assertEquals(expected, restored.suggestOpponent(restored.turn()));
        restored.playOpponent(restored.turn(), expected);
        assertEquals(Street.FLOP, restored.view().street());
        assertEquals(360, restored.view().pot());
    }

    @Test public void hardCoachAndAutoPlayUseTheSameSelectedPolicyAndAdvanceOneTurn() {
        long seed = 418;
        var game = LocalHoldemGame.tournament(5, seed, 1, "Ana", false, LocalHoldemGame.Difficulty.HARD);
        var hand = new Tournament(LocalHoldemGame.tournamentRules(5, 1), 0)
                .startHand(0, new Random(seed).nextLong()).hand();
        var decision = TournamentPlayStyles.hardAutoPlayStyle().policy(512)
                .decide(hand.observeActor(new PublicShoveHistory(5).snapshot()));
        var move = new LocalHoldemGame.Move(decision.action(), decision.amount());

        var token = game.turn();
        assertEquals(new LocalHoldemGame.AiSuggestion(move, decision.policyVersion(), decision.explanation()),
                game.suggest(token));
        assertEquals(token, game.turn());
        game.autoPlayAction(token);
        assertEquals(List.of(new LocalHoldemGame.Action(hand.actor(), move, LocalHoldemGame.ActionSource.AUTO_PLAY)),
                game.save().rounds().get(0));
        assertThrows(IllegalArgumentException.class, () -> game.autoPlayAction(token));
    }

    @Test public void normalSixSeatTournamentUsesOneUniformCheckCallPolicy() {
        var game = LocalHoldemGame.tournament(6, 418);
        game.play(game.turn(), new LocalHoldemGame.Move(ActionType.CALL, 20));
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
        var moves = game.save().rounds().get(0).stream().skip(1).map(action -> action.move().action()).toList();
        assertEquals(List.of(ActionType.CALL, ActionType.CALL, ActionType.CALL, ActionType.CALL, ActionType.CHECK),
                moves.subList(0, 5));
    }
}
