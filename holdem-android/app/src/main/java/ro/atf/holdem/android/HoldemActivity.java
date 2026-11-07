package ro.atf.holdem.android;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import ro.cardtable.names.PlayerNames;
import ro.cardtable.ui.menu.HomeMenu;
import ro.cardtable.ui.profile.PlayerNameDialog;
import ro.cardtable.ui.screens.ScreenViews;

import ro.atf.holdem.core.ActionType;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class HoldemActivity extends Activity {
    private static final String NAME = "player-name";
    private static final String LANGUAGE = "language";
    static final String DEBUG_DEAL_SEED = "ro.atf.holdem.android.DEBUG_DEAL_SEED";
    private SharedPreferences profile;
    private ScreenViews views;
    private LocalHoldemGame game;
    private boolean romanian;
    private boolean configuring;
    private boolean tournamentSetup;
    private boolean showingResult;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profile = getSharedPreferences("holdem-profile", MODE_PRIVATE);
        romanian = profile.getBoolean(LANGUAGE, false);
        views = new ScreenViews(this, tag -> tag, HoldemActivity::protect);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::navigateBack);
        }
        if (state != null && state.getBundle("training-hand") != null) restore(state.getBundle("training-hand"));
        else if (state != null && state.getBoolean("configuring")) setup(state.getBoolean("tournament-setup"));
        else home();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (game != null) state.putBundle("training-hand", bundle(game.save()));
        state.putBoolean("configuring", configuring);
        state.putBoolean("tournament-setup", tournamentSetup);
    }

    // API 33 and later dispatch through the registered platform callback.
    @Override @SuppressLint("GestureBackNavigation") @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) navigateBack();
        else super.onBackPressed();
    }

    private void navigateBack() {
        if (game != null || configuring) home();
        else finish();
    }

    private void home() {
        game = null;
        configuring = false;
        showingResult = false;
        views.applyStandardWindowChrome();
        var page = views.page(null, text(R.string.app_name));
        new HomeMenu(views).languagePicker(page, text(R.string.language), romanian ? 1 : 0, index -> {
            boolean selected = index == 1;
            if (selected == romanian) return;
            romanian = selected;
            profile.edit().putBoolean(LANGUAGE, selected).apply();
            home();
        });
        views.addQuietButton(page, text(R.string.profile, displayName()), this::editName);
        views.addButton(page, text(R.string.play_tournament), "play-tournament", true, () -> setup(true), true);
        views.addButton(page, text(R.string.play_hand), "play-hand", true, () -> setup(false), false);
        views.addText(page, text(R.string.training_summary), 14, ScreenViews.MUTED, false, 10, 0);
    }

    private void setup(boolean tournament) {
        configuring = true;
        tournamentSetup = tournament;
        var page = views.page(null, text(tournament ? R.string.tournament_setup : R.string.setup));
        var sizes = tournament ? LocalHoldemGame.TOURNAMENT_SIZES : LocalHoldemGame.TABLE_SIZES;
        var picker = views.spinner(sizes.stream().map(String::valueOf).collect(Collectors.toList()), "table-size");
        picker.setSelection(sizes.indexOf(4));
        views.addSetupSelector(page, text(R.string.players), picker);
        views.addButton(page, text(R.string.deal_hand), "deal-hand", true,
                () -> open(sizes.get(picker.getSelectedItemPosition()), 0, tournament), true);
        views.addQuietButton(page, text(R.string.home), this::home);
    }

    private void open(int size, int dealer, boolean tournament) {
        long seed = new SecureRandom().nextLong();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            seed = getIntent().getLongExtra(DEBUG_DEAL_SEED, seed);
        }
        game = tournament ? LocalHoldemGame.tournament(size, seed) : new LocalHoldemGame(size, dealer, seed);
        configuring = false;
        showingResult = false;
        showHand();
    }

    private void restore(Bundle state) {
        try {
            int count = state.getInt("round-count");
            var rounds = new ArrayList<List<LocalHoldemGame.Move>>();
            if (count == 0) {
                rounds.add(readMoves(state));
            } else {
                for (int i = 0; i < count; i++) rounds.add(readMoves(state.getBundle("round-" + i)));
            }
            game = LocalHoldemGame.restore(new LocalHoldemGame.Saved(
                    state.getInt("seats"), state.getInt("dealer"), state.getLong("seed"), state.getBoolean("tournament"), rounds));
            if (state.getBoolean("showing-result") && game.outcome() != null) showResult(game);
            else showHand();
        } catch (IllegalArgumentException invalid) {
            home();
            Toast.makeText(this, text(R.string.restore_error), Toast.LENGTH_LONG).show();
        }
    }

    private static List<LocalHoldemGame.Move> readMoves(Bundle state) {
        if (state == null) throw new IllegalArgumentException("Missing round");
        var actions = state.getStringArrayList("actions");
        var amounts = state.getIntArray("amounts");
        if (actions == null || amounts == null || actions.size() != amounts.length) {
            throw new IllegalArgumentException("Incomplete hand state");
        }
        var moves = new ArrayList<LocalHoldemGame.Move>(actions.size());
        for (int i = 0; i < amounts.length; i++) {
            moves.add(new LocalHoldemGame.Move(ActionType.valueOf(actions.get(i)), amounts[i]));
        }
        return moves;
    }

    private Bundle bundle(LocalHoldemGame.Saved saved) {
        var state = new Bundle();
        state.putInt("seats", saved.seats());
        state.putInt("dealer", saved.dealer());
        state.putLong("seed", saved.seed());
        state.putBoolean("tournament", saved.tournament());
        state.putBoolean("showing-result", showingResult);
        state.putInt("round-count", saved.rounds().size());
        for (int i = 0; i < saved.rounds().size(); i++) state.putBundle("round-" + i, writeMoves(saved.rounds().get(i)));
        return state;
    }

    private static Bundle writeMoves(List<LocalHoldemGame.Move> moves) {
        var state = new Bundle();
        var actions = new ArrayList<String>(moves.size());
        var amounts = new int[moves.size()];
        for (int i = 0; i < amounts.length; i++) {
            var move = moves.get(i);
            actions.add(move.action().name());
            amounts[i] = move.amount();
        }
        state.putStringArrayList("actions", actions);
        state.putIntArray("amounts", amounts);
        return state;
    }

    private void showHand() {
        var current = game;
        var frame = current.view();
        var turn = current.turn();
        int seats = frame.seats().size();
        var names = PlayerNames.forMatch(Math.max(3, seats), displayName(), seats).subList(0, seats);
        int nextDealer = seats == 2 ? frame.bigBlind() : frame.dealer();
        new HoldemTableView(views, this::text).show(frame, names, current, this::home, () -> {
            if (game != current || !current.turn().equals(turn)) return;
            if (current.outcome() != null) showResult(current);
            else if (current.isTournament()) {
                current.nextHand();
                showHand();
            } else open(seats, nextDealer, false);
        }, move -> {
            if (game != current) return false;
            try {
                current.play(turn, move);
            } catch (IllegalArgumentException rejected) {
                return false;
            }
            showHand();
            return true;
        });
    }

    private void showResult(LocalHoldemGame current) {
        showingResult = true;
        views.applyStandardWindowChrome();
        boolean won = current.outcome() == LocalHoldemGame.Outcome.WON;
        var page = views.page(null, text(won ? R.string.tournament_won : R.string.tournament_lost));
        var table = current.view();
        views.addText(page, text(R.string.tournament_summary, current.handNumber(), table.seats().get(table.viewer()).stack()),
                16, ScreenViews.MUTED, false, 10, 0);
        views.addButton(page, text(R.string.play_again), "play-again", true, () -> {
            game = null;
            showingResult = false;
            setup(true);
        }, true);
        views.addQuietButton(page, text(R.string.home), this::home);
    }

    private void editName() {
        var copy = new PlayerNameDialog.Text(text(R.string.name_settings), text(R.string.name_hint), text(R.string.save),
                text(R.string.reset), text(R.string.close), text(R.string.invalid_name), text(R.string.save_failed));
        PlayerNameDialog.show(this, copy, profile.getString(NAME, ""), PlayerNames.MAX_LENGTH, PlayerNames::normalize, name -> {
            var edit = profile.edit();
            if (name.isEmpty()) edit.remove(NAME);
            else edit.putString(NAME, name);
            if (!edit.commit()) throw new IllegalStateException("Profile write failed");
            home();
        });
    }

    private String displayName() {
        return profile.getString(NAME, text(R.string.you));
    }

    // The in-app language picker ships both locales in one APK; the bundle policy below keeps translations together.
    @SuppressLint("AppBundleLocaleChanges")
    private String text(int id, Object... arguments) {
        var configuration = new Configuration(getResources().getConfiguration());
        configuration.setLocale(romanian ? Locale.forLanguageTag("ro") : Locale.ENGLISH);
        return createConfigurationContext(configuration).getString(id, arguments);
    }

    private static void protect(View root) {
        if (Build.VERSION.SDK_INT < 35) return;
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                    | WindowInsets.Type.ime());
            view.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom);
            return WindowInsets.CONSUMED;
        });
        root.requestApplyInsets();
    }
}
