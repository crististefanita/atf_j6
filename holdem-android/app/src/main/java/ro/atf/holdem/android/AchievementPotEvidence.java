package ro.atf.holdem.android;

import java.util.List;
import ro.atf.holdem.core.HandRank;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.PotAward;

/** Settlement evidence for the player, with uncalled returns excluded by the engine. */
record AchievementPotEvidence(boolean sideEligible, boolean sideWon, boolean splitEligible, boolean splitWon,
                              boolean sideOnlyWon, boolean sweptSidePots, boolean boardPlaysSplit,
                              boolean mainPotSoleWinner) {
    static final AchievementPotEvidence NONE = new AchievementPotEvidence(false, false, false, false,
            false, false, false, false);

    static AchievementPotEvidence from(List<PotAward> layers, int viewer) {
        return from(layers, viewer, List.of(), null);
    }

    static AchievementPotEvidence from(List<PotAward> layers, HoldemTableViewState frame) {
        var evaluation = frame.seats().get(frame.viewer()).handEvaluation();
        return from(layers, frame.viewer(), frame.board(), evaluation);
    }

    private static AchievementPotEvidence from(List<PotAward> layers, int viewer,
                                               List<ro.atf.holdem.core.Card> board,
                                               ro.atf.holdem.core.HandEvaluation viewerEvaluation) {
        boolean sideEligible = false, sideWon = false, splitEligible = false, splitWon = false;
        boolean sideWonOnly = false, sweptSidePots = layers.size() > 1, boardSplit = false;
        boolean mainPotSoleWinner = false, mainPotWon = false;
        for (int index = 0; index < layers.size(); index++) {
            PotAward layer = layers.get(index);
            boolean won = layer.winners().contains(viewer);
            if (index == 0) {
                mainPotWon = won;
                mainPotSoleWinner = layer.winners().size() == 1 && won;
            }
            sweptSidePots &= layer.winners().size() == 1 && layer.winners().get(0) == viewer;
            if (layer.eligible().size() < 2 || !layer.eligible().contains(viewer)) continue;
            if (index > 0) {
                sideEligible = true;
                sideWon |= won;
                sideWonOnly |= won;
            }
            if (layer.winners().size() > 1) {
                splitEligible = true;
                splitWon |= won;
                if (won && viewerEvaluation != null && board.size() == 5
                        && viewerEvaluation.score() == HandRank.evaluateBestOf(board).score()) boardSplit = true;
            }
        }
        sideWonOnly &= !mainPotWon;
        sweptSidePots &= sideEligible;
        return new AchievementPotEvidence(sideEligible, sideWon, splitEligible, splitWon, sideWonOnly,
                sweptSidePots, boardSplit, mainPotSoleWinner);
    }
}
