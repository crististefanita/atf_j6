package ro.atf.holdem.android;

import java.util.List;
import java.util.Map;

/** Display order within each tier. Each milestone progresses independently. */
final class GameplayAchievementCatalog {
    record Milestone(String id, GameplayAchievements.Metric metric, int target, int label, boolean plural) {
        Milestone {
            if (target < 1 || target > metric.cap) throw new IllegalArgumentException("Invalid achievement target: " + id);
        }

        int progress(Map<GameplayAchievements.Metric, Integer> counts) {
            return Math.min(target, counts.getOrDefault(metric, 0));
        }
    }

    private GameplayAchievementCatalog() {}

    static List<Milestone> forTier(AchievementCatalog.Tier tier) {
        return GameplayAchievementCatalogData.forTier(tier);
    }
}
