package ro.atf.holdem.android;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Display order within each tier. Each milestone progresses independently. */
final class GameplayAchievementCatalog {
    enum Tier { EASY, MEDIUM, HARD }
    record Milestone(String id, GameplayAchievements.Metric metric, int target, int label, boolean plural,
                     String ruleRo, String ruleEn) {
        Milestone {
            if (target < 1) throw new IllegalArgumentException("Invalid achievement target: " + id);
        }

        String rule(boolean romanian) {
            return (romanian ? ruleRo : ruleEn).replace("{target}", Integer.toString(target));
        }

        long rowId() {
            return UUID.nameUUIDFromBytes(id.getBytes(StandardCharsets.UTF_8)).getMostSignificantBits() & Long.MAX_VALUE;
        }

        int progress(Map<GameplayAchievements.Metric, Integer> counts) {
            return Math.min(target, counts.getOrDefault(metric, 0));
        }
    }

    private static final Map<Tier, List<Milestone>> MILESTONES = new EnumMap<>(Tier.class);
    private static final Map<GameplayAchievements.Metric, Integer> TARGETS =
            new EnumMap<>(GameplayAchievements.Metric.class);

    static {
        for (Tier tier : Tier.values()) {
            List<Milestone> milestones = GameplayAchievementCatalogData.forTier(tier);
            MILESTONES.put(tier, milestones);
            for (Milestone milestone : milestones) {
                TARGETS.merge(milestone.metric(), milestone.target(), Math::max);
            }
        }
    }

    private GameplayAchievementCatalog() {}

    static List<Milestone> forTier(Tier tier) {
        return MILESTONES.get(tier);
    }

    static int maxTarget(GameplayAchievements.Metric metric) {
        return TARGETS.getOrDefault(metric, 0);
    }
}
