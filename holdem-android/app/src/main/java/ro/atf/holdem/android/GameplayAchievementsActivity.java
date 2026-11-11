package ro.atf.holdem.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import ro.atf.holdem.android.GameplayAchievementBrowser.Entry;
import ro.atf.holdem.android.GameplayAchievementBrowser.Query;
import ro.atf.holdem.android.GameplayAchievementBrowser.Status;
import ro.atf.holdem.android.GameplayAchievementCatalog.Tier;
import ro.cardtable.ui.achievements.AchievementScreen;
import ro.cardtable.ui.screens.ScreenViews;

public final class GameplayAchievementsActivity extends Activity {
    static final String PROFILE_ID = "achievement-profile";
    private final AchievementScreen.State state = new AchievementScreen.State();
    private Query query = new Query("", null, null);
    private GameplayAchievements achievements;
    private String profile;
    private AchievementScreen screen;

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
        if (saved != null) {
            state.restore(saved);
            query = new Query(saved.getString("achievementQuery"),
                    enumValue(Tier.class, saved.getString("achievementTier")),
                    enumValue(Status.class, saved.getString("achievementStatus")));
        }
        achievements = GameplayAchievementFile.local(this);
    }

    @Override protected void onResume() {
        super.onResume();
        if (achievements != null && screen == null) render();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        state.save(out);
        out.putString("achievementQuery", query.text());
        if (query.tier() != null) out.putString("achievementTier", query.tier().name());
        if (query.status() != null) out.putString("achievementStatus", query.status().name());
    }

    private void render() {
        var views = new ScreenViews(this, label -> label, HoldemActivity::protect);
        var back = new AchievementScreen.Action(getString(R.string.achievement_back), this::finish);
        Map<GameplayAchievements.Metric, Integer> counts;
        try {
            counts = achievements.counts(profile);
        } catch (IllegalStateException failure) {
            android.util.Log.e("HoldemLab", "Could not read gameplay achievements", failure);
            screen = new AchievementScreen(views, state, labels("", getString(R.string.achievement_error)),
                    0, 0, List.of(), back);
            screen.update(List.of(), "");
            return;
        }
        var entries = new ArrayList<Entry>();
        Context alternate = HoldemActivity.localizedContext(this, !romanian());
        int earned = 0;
        for (var tier : Tier.values()) {
            for (var milestone : GameplayAchievementCatalog.forTier(tier)) {
                int current = milestone.progress(counts);
                if (current == milestone.target()) earned++;
                entries.add(new Entry(tier, milestone, current, title(this, milestone), title(alternate, milestone)));
            }
        }
        var filters = new AchievementScreen.Action(getString(R.string.gameplay_achievement_filter_open),
                () -> showFilters(views, entries));
        screen = new AchievementScreen(views, state, labels(
                getString(R.string.gameplay_achievement_summary, earned, entries.size()),
                getString(R.string.gameplay_achievement_filter_empty)), earned, entries.size(), List.of(filters), back);
        updateRows(entries);
        screen.restoreDetail();
    }

    private void updateRows(List<Entry> entries) {
        var visible = GameplayAchievementBrowser.visible(entries, query);
        var rows = new ArrayList<AchievementScreen.Row>();
        Tier lastTier = null;
        for (var entry : visible) {
            if (entry.tier() != lastTier) {
                lastTier = entry.tier();
                rows.add(AchievementScreen.Row.heading(-1L - lastTier.ordinal(), tierLabel(lastTier)));
            }
            var milestone = entry.milestone();
            boolean complete = entry.status() == Status.EARNED;
            String progress = getString(complete ? R.string.gameplay_achievement_earned
                    : R.string.gameplay_achievement_progress, entry.current(), milestone.target());
            rows.add(new AchievementScreen.Row(milestone.rowId(), milestone.id(), entry.title(), progress, complete,
                    () -> new AchievementScreen.Detail(progress, List.of(
                            new AchievementScreen.Section(getString(R.string.gameplay_achievement_eligibility),
                                    milestone.rule(romanian())),
                            new AchievementScreen.Section("", getString(R.string.gameplay_achievement_scope))))));
        }
        String summary = query.active()
                ? getString(R.string.gameplay_achievement_filter_summary, visible.size(), entries.size()) : "";
        screen.update(rows, summary);
    }

    private void showFilters(ScreenViews views, List<Entry> entries) {
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(views.dp(20), views.dp(8), views.dp(20), views.dp(8));
        var search = new EditText(this);
        search.setSingleLine();
        search.setFilters(new InputFilter[]{new InputFilter.LengthFilter(96)});
        search.setHint(R.string.gameplay_achievement_filter_search);
        search.setText(query.text());
        content.addView(search, views.matchWrap());

        var tier = filter(views, content, R.string.gameplay_achievement_filter_tier,
                List.of(Tier.values()), query.tier(), this::tierLabel);
        var status = filter(views, content, R.string.gameplay_achievement_filter_status,
                List.of(Status.values()), query.status(), this::statusLabel);
        var scroll = new ScrollView(this);
        scroll.addView(content);
        var dialog = new AlertDialog.Builder(this).setTitle(R.string.gameplay_achievement_filter_open).setView(scroll)
                .setPositiveButton(R.string.gameplay_achievement_filter_apply, (ignored, which) ->
                        applyQuery(entries, new Query(search.getText().toString(), tier.value(), status.value())))
                .setNegativeButton(R.string.reset, (ignored, which) -> applyQuery(entries, new Query("", null, null)))
                .create();
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.show();
    }

    private void applyQuery(List<Entry> entries, Query updated) {
        if (query.equals(updated)) return;
        query = updated;
        state.resetPosition();
        updateRows(entries);
    }

    private <E> Filter<E> filter(ScreenViews views, LinearLayout content, int label, List<E> values,
                                 E selected, Function<E, String> title) {
        var labels = new ArrayList<String>();
        labels.add(getString(R.string.gameplay_achievement_filter_any));
        for (E value : values) labels.add(title.apply(value));
        String description = getString(label);
        views.addText(content, description, 13, ScreenViews.PINE, true, 6, 0);
        var spinner = views.spinner(labels, description);
        spinner.setContentDescription(description);
        spinner.setSelection(selected == null ? 0 : values.indexOf(selected) + 1);
        content.addView(spinner, views.matchWrap());
        return new Filter<>(spinner, values);
    }

    private record Filter<E>(Spinner spinner, List<E> values) {
        E value() {
            int position = spinner.getSelectedItemPosition();
            return position > 0 ? values.get(position - 1) : null;
        }
    }

    private boolean romanian() {
        return "ro".equals(getResources().getConfiguration().getLocales().get(0).getLanguage());
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null) return null;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException removed) {
            return null;
        }
    }

    private String statusLabel(Status status) {
        return getString(switch (status) {
            case LOCKED -> R.string.gameplay_achievement_filter_locked;
            case IN_PROGRESS -> R.string.gameplay_achievement_filter_in_progress;
            case EARNED -> R.string.gameplay_achievement_filter_earned;
        });
    }

    private AchievementScreen.Copy labels(String summary, String empty) {
        return new AchievementScreen.Copy(getString(R.string.achievements), summary, empty,
                getString(R.string.gameplay_achievement_list), getString(R.string.close));
    }

    static String title(Context context, GameplayAchievementCatalog.Milestone milestone) {
        return milestone.plural()
                ? context.getResources().getQuantityString(milestone.label(), milestone.target(), milestone.target())
                : context.getString(milestone.label());
    }

    private String tierLabel(Tier tier) {
        return getString(switch (tier) {
            case EASY -> R.string.achievement_easy;
            case MEDIUM -> R.string.achievement_medium;
            case HARD -> R.string.achievement_hard;
        });
    }
}
