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

public final class AchievementsActivity extends Activity {
    static final String PROFILE_ID = "achievement-profile";
    private static final int SURFACE = 0xff18352e;
    private static final int SELECTED = 0xff306a58;
    private static final int TEXT = 0xfff1faf5;
    private static final int SECONDARY = 0xffbad2c7;
    private static final int ACCENT = 0xffa9e1c7;

    private final Button[] tabs = new Button[AchievementCatalog.Tier.values().length];
    private final int[] firstRows = new int[tabs.length];
    private final int[] offsets = new int[tabs.length];
    private AchievementCatalog catalog;
    private AchievementProgress progress;
    private AchievementCatalog.Tier tier = AchievementCatalog.Tier.EASY;
    private List<AchievementPresentation.Row> rows = Collections.emptyList();
    private String[] familyLabels;
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
        back.setAllCaps(false);
        root.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView heading = label(26, TEXT);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        heading.setText(R.string.achievements);
        root.addView(heading);
        summary = label(14, SECONDARY);
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

        familyLabels = getResources().getStringArray(R.array.achievement_families);
        var bar = new LinearLayout(this);
        root.addView(bar);
        int[] labels = {R.string.achievement_easy, R.string.achievement_medium, R.string.achievement_hard};
        for (var difficulty : AchievementCatalog.Tier.values()) {
            int index = difficulty.ordinal();
            var tab = button(labels[index]);
            tab.setAllCaps(false);
            tab.setTextSize(13);
            tab.setMinWidth(0);
            tab.setMinimumWidth(0);
            tab.setMaxLines(2);
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
        playable.setTextColor(TEXT);
        onlyPlayable = saved != null && saved.getBoolean("only-playable");
        playable.setChecked(onlyPlayable);
        root.addView(playable);
        var empty = label(14, SECONDARY);
        empty.setText(R.string.achievement_empty);
        root.addView(empty);
        list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0xff29463c));
        list.setDividerHeight(dp(1));
        list.setEmptyView(empty);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public AchievementPresentation.Row getItem(int position) { return rows.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override public View getView(int position, View recycled, ViewGroup parent) {
                var view = recycled instanceof AchievementRowView existing ? existing : new AchievementRowView();
                view.bind(getItem(position));
                return view;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showDetails(rows.get(position).entry()));
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
        var tabState = AchievementPresentation.tab(catalog, progress, tier, onlyPlayable);
        summary.setText(getString(R.string.achievement_summary, tabState.unlocked(), tabState.total()));
        rows = tabState.rows();
        for (var item : AchievementCatalog.Tier.values()) {
            Button tab = tabs[item.ordinal()];
            tab.setSelected(item == tier);
            tab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(item == tier ? SELECTED : SURFACE));
        }
        adapter.notifyDataSetChanged();
        int position = Math.min(firstRows[tier.ordinal()], rows.size() - 1);
        if (position >= 0) list.setSelectionFromTop(position, offsets[tier.ordinal()]);
    }

    private String status(AchievementPresentation.Row row) {
        return switch (row.status()) {
            case LOCKED -> getString(R.string.achievement_locked);
            case READY -> getString(R.string.achievement_ready);
            case COMPLETE -> getString(R.string.achievement_done);
            case IN_PROGRESS -> {
                var entry = row.entry();
                var state = row.progress();
                String main = entry.lots() > 1
                        ? getString(R.string.achievement_lots, state.passedLots(), entry.lots())
                        : getString(R.string.achievement_score, state.correct(), entry.required());
                if (row.showsCurrentLot()) main += "\n" + getString(R.string.achievement_lot_score,
                        state.answered(), entry.size(), state.correct());
                yield main;
            }
        };
    }

    private final class AchievementRowView extends LinearLayout {
        private final TextView family = label(13, ACCENT);
        private final TextView title = label(16, TEXT);
        private final TextView state = label(13, SECONDARY);

        AchievementRowView() {
            super(AchievementsActivity.this);
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            family.setTypeface(null, android.graphics.Typeface.BOLD);
            family.setPadding(0, dp(4), 0, dp(6));
            if (android.os.Build.VERSION.SDK_INT >= 28) family.setAccessibilityHeading(true);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            state.setPadding(0, dp(3), 0, 0);
            addView(family);
            addView(title);
            addView(state);
        }

        void bind(AchievementPresentation.Row row) {
            var entry = row.entry();
            family.setVisibility(row.startsFamily() ? VISIBLE : GONE);
            family.setText(row.startsFamily() ? familyLabels[catalog.familyIndex(entry)] : "");
            title.setText(getString(R.string.achievement_title, entry.context(), entry.stage()));
            state.setText(status(row));
            state.setTextColor(row.status() == AchievementPresentation.Status.COMPLETE
                    ? ACCENT : SECONDARY);
        }
    }
    private void showDetails(AchievementCatalog.Entry entry) {
        var row = AchievementPresentation.row(entry, progress, false);
        var prerequisite = catalog.find(entry.prerequisite());
        String detail = getString(R.string.achievement_criteria, entry.required(), entry.size(), entry.lots());
        if (prerequisite != null) detail += "\n" + getString(R.string.achievement_prerequisite,
                prerequisite.context(), prerequisite.stage());
        var dialog = new AlertDialog.Builder(this)
                .setTitle(entry.context())
                .setMessage(detail + "\n\n" + status(row))
                .setNegativeButton(android.R.string.cancel, null);
        if (row.canPractice())
            dialog.setPositiveButton(R.string.achievement_practice, (ignored, which) -> ask(entry));
        dialog.show();
    }

    private void ask(AchievementCatalog.Entry entry) {
        var state = progress.state(entry);
        var exercise = exercise(entry, state.attempts());
        var content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        var prompt = new TextView(this);
        prompt.setTextSize(16);
        prompt.setText(exercise.prompt());
        content.addView(prompt);
        var scroller = new android.widget.ScrollView(this);
        scroller.addView(content);
        var dialog = new AlertDialog.Builder(this).setTitle(entry.context()).setView(scroller)
                .setNegativeButton(android.R.string.cancel, null).create();
        for (int choice = 0; choice < exercise.options().size(); choice++) {
            int selected = choice;
            var option = new Button(this);
            option.setAllCaps(false);
            option.setText(exercise.options().get(choice));
            option.setOnClickListener(view -> {
                dialog.dismiss();
                answer(entry, state.attempts(), exercise, selected);
            });
            content.addView(option);
        }
        dialog.show();
    }

    private AchievementExercises.Question exercise(AchievementCatalog.Entry entry, int attempt) {
        if (!entry.usesPotOdds()) return AchievementExercises.question(entry, attempt);
        var odds = progress.question(entry, attempt);
        String prompt = getString(R.string.achievement_question, odds.pot(), odds.call(), odds.equity());
        var options = List.of(getString(R.string.achievement_profit), getString(R.string.achievement_even),
                getString(R.string.achievement_loss));
        int correct = odds.solution().ordinal();
        String explanation = getString(R.string.achievement_explanation, options.get(correct),
                100.0 * odds.call() / (odds.pot() + odds.call()));
        return new AchievementExercises.Question(prompt, options, correct, explanation);
    }

    private void answer(AchievementCatalog.Entry entry, int attempt,
                        AchievementExercises.Question exercise, int selected) {
        try {
            boolean correct = progress.submit(entry, attempt, selected);
            rememberScroll();
            render(tier);
            var state = progress.state(entry);
            String result = getString(correct ? R.string.achievement_correct : R.string.achievement_incorrect);
            String explanation = exercise.explanation();
            var dialog = new AlertDialog.Builder(this).setTitle(result)
                    .setMessage(explanation + "\n\n" + status(AchievementPresentation.row(entry, progress, false)))
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
        view.setTextColor(TEXT);
        view.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
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
