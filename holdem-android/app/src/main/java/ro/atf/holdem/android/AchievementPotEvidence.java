package ro.atf.holdem.android;

import java.util.List;
import ro.atf.holdem.core.PotAward;

/** Settlement evidence for the player, with uncalled returns excluded by the engine. */
record AchievementPotEvidence(boolean sideEligible, boolean sideWon, boolean splitEligible, boolean splitWon) {
    static final AchievementPotEvidence NONE = new AchievementPotEvidence(false, false, false, false);

    static AchievementPotEvidence from(List<PotAward> layers, int viewer) {
        boolean sideEligible = false, sideWon = false, splitEligible = false, splitWon = false;
        for (int index = 0; index < layers.size(); index++) {
            PotAward layer = layers.get(index);
            if (layer.eligible().size() < 2 || !layer.eligible().contains(viewer)) continue;
            boolean won = layer.winners().contains(viewer);
            if (index > 0) {
                sideEligible = true;
                sideWon |= won;
            }
            if (layer.winners().size() > 1) {
                splitEligible = true;
                splitWon |= won;
            }
        }
        return new AchievementPotEvidence(sideEligible, sideWon, splitEligible, splitWon);
    }
}
