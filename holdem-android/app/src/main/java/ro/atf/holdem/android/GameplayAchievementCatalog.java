package ro.atf.holdem.android;

import static ro.atf.holdem.android.GameplayAchievements.Metric.*;

import java.util.List;

/** Display order within each tier; no milestone depends on another. */
final class GameplayAchievementCatalog {
    record Milestone(GameplayAchievements.Metric metric, int target, int label) {
        Milestone {
            if (target < 1 || target > metric.cap) throw new IllegalArgumentException("Invalid achievement target");
        }

        int progress(java.util.Map<GameplayAchievements.Metric, Integer> counts) {
            return Math.min(target, counts.getOrDefault(metric, 0));
        }
    }

    private GameplayAchievementCatalog() {}

    static List<Milestone> forTier(AchievementCatalog.Tier tier) {
        return switch (tier) {
            case EASY -> List.of(
                    new Milestone(POT, 1, R.string.gameplay_achievement_first_pot),
                    new Milestone(UNCONTESTED_POT, 1, R.string.gameplay_achievement_folds),
                    new Milestone(SHOWDOWN_POT, 1, R.string.gameplay_achievement_showdown),
                    new Milestone(TOURNAMENT_POT, 1, R.string.gameplay_achievement_tournament_pot),
                    new Milestone(POT, 3, R.string.gameplay_achievement_three_pots));
            case MEDIUM -> List.of(
                    new Milestone(TITLE, 1, R.string.gameplay_achievement_first_title),
                    new Milestone(PODIUM, 1, R.string.gameplay_achievement_podium),
                    new Milestone(SHOWDOWN_POT, 3, R.string.gameplay_achievement_three_showdowns),
                    new Milestone(STRAIGHT_OR_BETTER, 1, R.string.gameplay_achievement_straight),
                    new Milestone(TOURNAMENT_POT, 5, R.string.gameplay_achievement_five_tournament_pots));
            case HARD -> List.of(
                    new Milestone(TITLE, 3, R.string.gameplay_achievement_three_titles),
                    new Milestone(SOLE_HIGH_CARD_POT, 1, R.string.gameplay_achievement_high_card),
                    new Milestone(SIX_SEAT_TITLE, 2, R.string.gameplay_achievement_six_seat_titles),
                    new Milestone(FULL_HOUSE_OR_BETTER, 1, R.string.gameplay_achievement_full_house),
                    new Milestone(MULTIWAY_TOURNAMENT_UNCONTESTED, 5, R.string.gameplay_achievement_multiway_folds));
        };
    }
}
