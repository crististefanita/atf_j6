package ro.atf.holdem.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AchievementPresentation {
    enum Status { LOCKED, READY, IN_PROGRESS, COMPLETE }

    record Row(AchievementCatalog.Entry entry, AchievementProgress.State progress, Status status, boolean startsFamily) {
        boolean canPractice() {
            return status == Status.READY || status == Status.IN_PROGRESS;
        }

        boolean showsCurrentLot() {
            return status == Status.IN_PROGRESS && entry.tier() != AchievementCatalog.Tier.EASY
                    && progress.answered() > 0;
        }
    }

    record Tab(int total, int unlocked, List<Row> rows) {}

    private AchievementPresentation() {}

    static Tab tab(AchievementCatalog catalog, AchievementProgress progress, AchievementCatalog.Tier tier,
                   boolean onlyPlayable) {
        var rows = new ArrayList<Row>();
        var entries = catalog.entries(tier);
        String previousFamily = "";
        int unlocked = 0;
        for (var entry : entries) {
            var row = row(entry, progress, !previousFamily.equals(entry.family()));
            if (row.status() == Status.COMPLETE) unlocked++;
            if (onlyPlayable && !row.canPractice()) continue;
            rows.add(row);
            previousFamily = entry.family();
        }
        return new Tab(entries.size(), unlocked, Collections.unmodifiableList(rows));
    }

    static Row row(AchievementCatalog.Entry entry, AchievementProgress progress, boolean startsFamily) {
        var state = progress.state(entry);
        Status status;
        if (progress.completed(entry, state)) status = Status.COMPLETE;
        else if (!progress.available(entry)) status = Status.LOCKED;
        else status = state.attempts() == 0 ? Status.READY : Status.IN_PROGRESS;
        return new Row(entry, state, status, startsFamily);
    }
}
