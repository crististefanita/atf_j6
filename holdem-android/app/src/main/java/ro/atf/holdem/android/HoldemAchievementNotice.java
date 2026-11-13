package ro.atf.holdem.android;

import android.app.AlertDialog;
import android.content.Context;
import ro.cardtable.ui.achievements.AchievementNoticeView;
import ro.cardtable.ui.screens.ScreenViews;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Presents persisted achievement progress for the displayed game. */
final class HoldemAchievementNotice {
    private final ScreenViews views;
    private final HoldemTableView.Text text;
    private final Supplier<Context> localized;
    private LocalHoldemGame achievementProgressGame;
    private List<GameplayAchievements.Change> achievementProgress = List.of();
    private AlertDialog achievementNoticeDialog;

    HoldemAchievementNotice(ScreenViews views, HoldemTableView.Text text, Supplier<Context> localized) {
        this.views = views;
        this.text = text;
        this.localized = localized;
    }

    void record(LocalHoldemGame game, List<GameplayAchievements.Change> changes) {
        achievementProgressGame = game;
        achievementProgress = changes;
    }

    void clear() {
        achievementProgressGame = null;
        achievementProgress = List.of();
    }

    void close() {
        if (achievementNoticeDialog != null) {
            achievementNoticeDialog.setOnDismissListener(null);
            achievementNoticeDialog.dismiss();
            achievementNoticeDialog = null;
        }
        clear();
    }

    void show(LocalHoldemGame current) {
        if (achievementProgressGame != current) return;
        if (achievementNoticeDialog != null && achievementNoticeDialog.isShowing()) return;
        var changes = achievementProgress;
        if (changes.isEmpty()) {
            clearAchievementProgress(current);
            return;
        }
        var summary = GameplayAchievementNotice.summarize(changes);
        var lines = new ArrayList<String>();
        for (var change : summary.previews()) lines.add(
                GameplayAchievementsActivity.title(views.activity(), change.milestone()) + " · " +
                text.get(change.unlocked() ? R.string.gameplay_achievement_earned : R.string.gameplay_achievement_progress,
                        change.count(), change.milestone().target()));
        if (summary.remaining() > 0) {
            lines.add(localized.get().getResources().getQuantityString(
                    R.plurals.gameplay_achievement_more, summary.remaining(), summary.remaining()));
        }
        int title = current.outcome() == null ? R.string.gameplay_achievement_notice
                : R.string.gameplay_achievement_match_notice;
        var dialog = AchievementNoticeView.dialog(views, text.get(title), lines, text.get(R.string.close));
        achievementNoticeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (achievementNoticeDialog == dialog) {
                achievementNoticeDialog = null;
                clearAchievementProgress(current);
            }
        });
        dialog.show();
    }

    private void clearAchievementProgress(LocalHoldemGame current) {
        if (achievementProgressGame != current) return;
        clear();
    }
}
