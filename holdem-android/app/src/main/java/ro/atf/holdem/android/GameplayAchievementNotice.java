package ro.atf.holdem.android;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Selects a compact, deterministic preview for the shared achievement notice. */
final class GameplayAchievementNotice {
    record Summary(List<GameplayAchievements.Change> previews, int remaining) {
        Summary {
            previews = List.copyOf(previews);
            if (remaining < 0 || previews.size() > 2) {
                throw new IllegalArgumentException("Invalid achievement notice summary");
            }
        }
    }

    private GameplayAchievementNotice() { }

    static Summary summarize(List<GameplayAchievements.Change> changes) {
        Objects.requireNonNull(changes);
        var previews = new ArrayList<GameplayAchievements.Change>(2);
        changes.stream().filter(GameplayAchievements.Change::unlocked).findFirst().ifPresent(previews::add);
        changes.stream().filter(change -> !change.unlocked()).findFirst().ifPresent(previews::add);
        return new Summary(previews, changes.size() - previews.size());
    }

}
