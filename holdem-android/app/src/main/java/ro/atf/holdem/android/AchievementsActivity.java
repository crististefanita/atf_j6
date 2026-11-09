package ro.atf.holdem.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ro.cardtable.ui.screens.ScreenViews;

public final class AchievementsActivity extends Activity {
    static final String PROFILE_ID = "achievement-profile";
    private static final int SURFACE = 0xff18352e;
    private static final int SELECTED = 0xff306a58;

    private final Button[] tabs = new Button[AchievementCatalog.Tier.values().length];
    private final int[] firstRows = new int[tabs.length];
    private final int[] offsets = new int[tabs.length];
    private AchievementCatalog catalog;
    private AchievementProgress progress;
    private AchievementCatalog.Tier tier = AchievementCatalog.Tier.EASY;
    private List<AchievementCatalog.Entry> entries = Collections.emptyList();
    private ListView list;
    private TextView summary;
    private BaseAdapter adapter;
    private boolean onlyPlayable;

    // Both locales are included in every build by verifyLanguageBundlePolicy.
    @android.annotation.SuppressLint("AppBundleLocaleChanges")
    @Override protected void attachBaseContext(Context base) {
        var profile = base.getSharedPreferences("holdem-profile", MODE_PRIVATE);
        var configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(profile.getBoolean("language", false) ? Locale.forLanguageTag("ro") : Locale.ENGLISH);
        super.attachBaseContext(base.createConfigurationContext(configuration));
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        String profileId = getIntent().getStringExtra(PROFILE_ID);
        if (profileId == null || profileId.trim().isEmpty()) {
            finish();
            return;
        }
        var root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xff0f2520);
        int padding = dp(16);
        root.setPadding(padding, padding, padding, padding);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(padding + insets.getSystemWindowInsetLeft(), padding + insets.getSystemWindowInsetTop(),
                    padding + insets.getSystemWindowInsetRight(), padding + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(root);
        var back = button(R.string.achievement_back);
        back.setOnClickListener(view -> finish());
        root.addView(back);
        TextView heading = label(26, ScreenViews.INK);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        heading.setText(R.string.achievements);
        root.addView(heading);
        summary = label(14, ScreenViews.MUTED);
        root.addView(summary);

        try (var asset = getAssets().open("J6-PROD-01-ACHIEVEMENT-CATALOG.csv");
             var reader = new InputStreamReader(asset, StandardCharsets.UTF_8)) {
            catalog = new AchievementCatalog(reader);
        } catch (IOException failure) {
            android.util.Log.e("HoldemLab", "Could not load achievement catalog", failure);
            summary.setText(R.string.achievement_error);
            return;
        }
        var preferences = getSharedPreferences("holdem-achievements", MODE_PRIVATE);
        progress = new AchievementProgress(catalog, new AchievementProgress.Storage() {
            @Override public String read(String key) { return preferences.getString(key, null); }

            @Override public void write(String key, String value) {
                if (!preferences.edit().putString(key, value).commit())
                    throw new IllegalStateException("Could not persist achievement progress");
            }
        }, profileId);

        var bar = new LinearLayout(this);
        root.addView(bar);
        int[] labels = {R.string.achievement_easy, R.string.achievement_medium, R.string.achievement_hard};
        for (var difficulty : AchievementCatalog.Tier.values()) {
            int index = difficulty.ordinal();
            var tab = button(labels[index]);
            tab.setAllCaps(false);
            tab.setPadding(dp(2), 0, dp(2), 0);
            tab.setOnClickListener(view -> {
                rememberScroll();
                render(difficulty);
            });
            tabs[index] = tab;
            bar.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        var playable = new CheckBox(this);
        playable.setText(R.string.achievement_playable);
        playable.setTextColor(ScreenViews.INK);
        onlyPlayable = saved != null && saved.getBoolean("only-playable");
        playable.setChecked(onlyPlayable);
        root.addView(playable);
        var empty = label(14, ScreenViews.MUTED);
        empty.setText(R.string.achievement_empty);
        root.addView(empty);
        list = new ListView(this);
        list.setEmptyView(empty);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return entries.size(); }
            @Override public AchievementCatalog.Entry getItem(int position) { return entries.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override public View getView(int position, View recycled, ViewGroup parent) {
                var row = recycled instanceof TextView existing ? existing : label(16, ScreenViews.INK);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                var entry = getItem(position);
                String name = getString(R.string.achievement_row, entry.context(), entry.stage(), status(entry));
                if (position == 0 || !entry.family().equals(getItem(position - 1).family()))
                    name = getResources().getStringArray(R.array.achievement_families)[catalog.familyIndex(entry)] + "\n" + name;
                row.setText(name);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetails(entries.get(position)));
        if (saved != null) {
            tier = AchievementCatalog.Tier.valueOf(saved.getString("tier", tier.name()));
            var savedRows = saved.getIntArray("first-rows");
            var savedOffsets = saved.getIntArray("offsets");
            if (savedRows != null && savedOffsets != null) {
                System.arraycopy(savedRows, 0, firstRows, 0, firstRows.length);
                System.arraycopy(savedOffsets, 0, offsets, 0, offsets.length);
            }
        }
        render(tier);
        playable.setOnCheckedChangeListener((view, checked) -> {
            onlyPlayable = checked;
            java.util.Arrays.fill(firstRows, 0);
            java.util.Arrays.fill(offsets, 0);
            render(tier);
        });
    }

    private void rememberScroll() {
        firstRows[tier.ordinal()] = list.getFirstVisiblePosition();
        View first = list.getChildAt(0);
        offsets[tier.ordinal()] = first == null ? 0 : first.getTop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (list == null) return;
        rememberScroll();
        state.putString("tier", tier.name());
        state.putBoolean("only-playable", onlyPlayable);
        state.putIntArray("first-rows", firstRows);
        state.putIntArray("offsets", offsets);
    }

    private void render(AchievementCatalog.Tier difficulty) {
        tier = difficulty;
        var all = catalog.entries(tier);
        long done = all.stream().filter(entry -> progress.completed(entry, progress.state(entry))).count();
        summary.setText(getString(R.string.achievement_summary, done, all.size()));
        entries = onlyPlayable ? all.stream().filter(entry -> progress.available(entry)
                && !progress.completed(entry, progress.state(entry))).collect(java.util.stream.Collectors.toList()) : all;
        for (var item : AchievementCatalog.Tier.values()) {
            Button tab = tabs[item.ordinal()];
            tab.setSelected(item == tier);
            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item == tier ? SELECTED : SURFACE));
        }
        adapter.notifyDataSetChanged();
        list.setSelectionFromTop(firstRows[tier.ordinal()], offsets[tier.ordinal()]);
    }

    private String status(AchievementCatalog.Entry entry) {
        var state = progress.state(entry);
        if (progress.completed(entry, state)) return getString(R.string.achievement_done);
        if (!entry.playable()) return getString(R.string.achievement_planned);
        if (!progress.available(entry)) return getString(R.string.achievement_locked);
        return entry.lots() > 1 ? getString(R.string.achievement_lots, state.passedLots(), entry.lots())
                : getString(R.string.achievement_score, state.correct(), entry.required());
    }

    private void showDetails(AchievementCatalog.Entry entry) {
        var prerequisite = catalog.find(entry.prerequisite());
        String detail = getString(R.string.achievement_criteria, entry.required(), entry.size(), entry.lots());
        if (prerequisite != null) detail += "\n" + getString(R.string.achievement_prerequisite,
                prerequisite.context(), prerequisite.stage());
        var dialog = new AlertDialog.Builder(this)
                .setTitle(entry.context())
                .setMessage(detail + "\n\n" + status(entry))
                .setNegativeButton(android.R.string.cancel, null);
        if (progress.available(entry) && !progress.completed(entry, progress.state(entry)))
            dialog.setPositiveButton(R.string.achievement_practice, (ignored, which) -> ask(entry));
        dialog.show();
    }

    private void ask(AchievementCatalog.Entry entry) {
        var state = progress.state(entry);
        var question = progress.question(entry, state.attempts());
        String prompt = getString(R.string.achievement_question, question.pot(), question.call(), question.equity());
        var options = new String[] {getString(R.string.achievement_profit), getString(R.string.achievement_even),
                getString(R.string.achievement_loss)};
        new AlertDialog.Builder(this).setTitle(entry.context()).setMessage(prompt)
                .setPositiveButton(options[0], (dialog, which) -> answer(entry, state, question, AchievementProgress.Answer.PROFIT))
                .setNeutralButton(options[1], (dialog, which) -> answer(entry, state, question, AchievementProgress.Answer.BREAK_EVEN))
                .setNegativeButton(options[2], (dialog, which) -> answer(entry, state, question, AchievementProgress.Answer.LOSS))
                .show();
    }

    private void answer(AchievementCatalog.Entry entry, AchievementProgress.State before,
                        AchievementProgress.Question question, AchievementProgress.Answer selected) {
        try {
            boolean correct = progress.submit(entry, before.attempts(), selected);
            rememberScroll();
            render(tier);
            var state = progress.state(entry);
            int solution = switch (question.solution()) {
                case PROFIT -> R.string.achievement_profit;
                case BREAK_EVEN -> R.string.achievement_even;
                case LOSS -> R.string.achievement_loss;
            };
            String result = getString(correct ? R.string.achievement_correct : R.string.achievement_incorrect);
            String explanation = getString(R.string.achievement_explanation, getString(solution),
                    100.0 * question.call() / (question.pot() + question.call()));
            String lot = entry.tier() == AchievementCatalog.Tier.EASY ? "" : "\n"
                    + getString(R.string.achievement_lot_score, state.answered(), entry.size(), state.correct());
            var dialog = new AlertDialog.Builder(this).setTitle(result)
                    .setMessage(explanation + "\n\n" + status(entry) + lot)
                    .setNegativeButton(R.string.achievement_back, null);
            if (!progress.completed(entry, state))
                dialog.setPositiveButton(R.string.achievement_next, (ignored, which) -> ask(entry));
            dialog.show();
        } catch (RuntimeException failure) {
            android.util.Log.e("HoldemLab", "Could not save achievement answer", failure);
            Toast.makeText(this, R.string.achievement_error, Toast.LENGTH_LONG).show();
        }
    }

    private Button button(int text) {
        var view = new Button(this);
        view.setText(text);
        view.setTextColor(ScreenViews.INK);
        return view;
    }

    private TextView label(int size, int color) {
        var view = new TextView(this);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
