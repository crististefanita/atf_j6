package ro.atf.holdem.android;

import ro.atf.holdem.core.ActionType;

import java.util.List;

import static ro.atf.holdem.core.ActionType.ALL_IN;
import static ro.atf.holdem.core.ActionType.CALL;
import static ro.atf.holdem.core.ActionType.CHECK;

enum SettlementAwardFixture {
    SPLIT(33, 0, 2000, List.of(new HoldemTablePresentation.PotAward(1, 4000), new HoldemTablePresentation.PotAward(3, 4000))),
    SIDE(24, 2, 2030, List.of(new HoldemTablePresentation.PotAward(2, 7840), new HoldemTablePresentation.PotAward(3, 160)));

    private final long seed;
    private final int priorHands;
    final int allInChips;
    final List<HoldemTablePresentation.PotAward> awards;

    SettlementAwardFixture(long seed, int priorHands, int allInChips, List<HoldemTablePresentation.PotAward> awards) {
        this.seed = seed;
        this.priorHands = priorHands;
        this.allInChips = allInChips;
        this.awards = awards;
    }

    LocalHoldemGame beforeAllIn() {
        var game = LocalHoldemGame.tournament(4, seed);
        for (int round = 0; round < priorHands; round++) {
            advancePassive(game);
            boolean call = game.view().legalActions().stream().anyMatch(action -> action.type() == CALL);
            play(game, call ? CALL : CHECK);
            for (int street = 0; street < 3; street++) play(game, CHECK);
            game.nextHandAtElapsedSeconds(0);
        }
        advancePassive(game);
        return game;
    }

    LocalHoldemGame settled() {
        var game = beforeAllIn();
        game.play(game.turn(), new LocalHoldemGame.Move(ALL_IN, allInChips));
        advancePassive(game);
        return game;
    }

    private static void play(LocalHoldemGame game, ActionType type) {
        var legal = game.view().legalActions().stream().filter(action -> action.type() == type).findFirst().orElseThrow();
        game.play(game.turn(), new LocalHoldemGame.Move(type, legal.amount()));
        advancePassive(game);
    }

    private static void advancePassive(LocalHoldemGame game) {
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
    }
}
