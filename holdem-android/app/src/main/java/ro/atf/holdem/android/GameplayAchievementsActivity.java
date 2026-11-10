package ro.atf.holdem.android;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ro.cardtable.ui.screens.ScreenViews;
import ro.cardtable.ui.theme.TablePalette;

public final class GameplayAchievementsActivity extends Activity {
    static final String PROFILE_ID = "achievement-profile";
    private final Button[] tabs = new Button[GameplayAchievementCatalog.Tier.values().length];
    private GameplayAchievementCatalog.Tier tier = GameplayAchievementCatalog.Tier.EASY;
    private LinearLayout rows;
    private ScrollView scroll;
    private ScreenViews views;
    private GameplayAchievements achievements;
    private String profile;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(HoldemActivity.localizedContext(base));
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        profile = getIntent().getStringExtra(PROFILE_ID);
        if (profile == null || profile.trim().isEmpty()) {
            finish();
            return;
        }
        if (saved != null) tier = GameplayAchievementCatalog.Tier.valueOf(saved.getString("tier", tier.name()));
        achievements = GameplayAchievementFile.local(this, profile);
        views = new ScreenViews(this, label -> label, ignored -> {});
        views.applyTableWindowChrome();
        var root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(TablePalette.TABLE_FELT);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        HoldemActivity.protect(root);
        setContentView(root);

        var back = button(R.string.achievement_back);
        back.setOnClickListener(view -> finish());
        root.addView(back, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        var heading = label(26, AchievementPresentation.TEXT);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        heading.setText(R.string.achievements);
        root.addView(heading);

        var bar = new LinearLayout(this);
        root.addView(bar);
        int[] labels = {R.string.achievement_easy, R.string.achievement_medium, R.string.achievement_hard};
        for (var group : GameplayAchievementCatalog.Tier.values()) {
            var tab = button(labels[group.ordinal()]);
            tab.setTextSize(13);
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setPadding(dp(2), 0, dp(2), 0);
            tab.setMaxLines(2);
            tab.setOnClickListener(view -> {
                tier = group;
                render();
                scroll.scrollTo(0, 0);
            });
            tabs[group.ordinal()] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1));
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);

    }

    @Override protected void onResume() {
        super.onResume();
        if (rows != null) render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("tier", tier.name());
    }

    private void render() {
        for (var group : GameplayAchievementCatalog.Tier.values()) {
            var tab = tabs[group.ordinal()];
            tab.setSelected(group == tier);
            HoldemTableButton.apply(views, tab, group == tier);
        }
        rows.removeAllViews();
        java.util.Map<GameplayAchievements.Metric, Integer> counts;
        try {
            counts = achievements.counts(profile);
        } catch (IllegalStateException failure) {
            android.util.Log.e("HoldemLab", "Could not read gameplay achievements", failure);
            var error = label(14, AchievementPresentation.SECONDARY_TEXT);
            error.setText(R.string.achievement_error);
            rows.addView(error);
            return;
        }
        for (var milestone : GameplayAchievementCatalog.forTier(tier)) {
            var row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(4), dp(8), dp(4), dp(8));
            var title = label(16, AchievementPresentation.TEXT);
            title.setText(milestone.plural()
                    ? getResources().getQuantityString(milestone.label(), milestone.target(), milestone.target())
                    : getString(milestone.label()));
            row.addView(title);
            int current = milestone.progress(counts);
            boolean earned = current == milestone.target();
            var progress = label(14, earned ? AchievementPresentation.ACCENT_TEXT : AchievementPresentation.SECONDARY_TEXT);
            progress.setText(getString(earned ? R.string.gameplay_achievement_earned : R.string.gameplay_achievement_progress,
                    current, milestone.target()));
            row.addView(progress);
            rows.addView(row);
        }
    }

    private Button button(int resource) {
        var button = new Button(this);
        button.setText(resource);
        button.setTextColor(AchievementPresentation.TEXT);
        button.setAllCaps(false);
        HoldemTableButton.apply(views, button, false);
        return button;
    }

    private TextView label(int size, int color) {
        var label = new TextView(this);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
