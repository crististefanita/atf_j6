package ro.atf.holdem.android;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;
import ro.cardtable.names.PlayerNames;
import ro.cardtable.ui.menu.HomeMenu;
import ro.cardtable.ui.profile.PlayerNameDialog;
import ro.cardtable.ui.screens.ScreenViews;
import ro.cardtable.ui.turn.TurnPacing;

import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.Street;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public final class HoldemActivity extends Activity {
    private static final String NAME = "player-name";
    private static final String LANGUAGE = "language";
    static final String DEBUG_DEAL_SEED = "ro.atf.holdem.android.DEBUG_DEAL_SEED";
    private SharedPreferences profile;
    private ScreenViews views;
    private HoldemSessionRepository sessions;
    private LocalHoldemGame game;
    private LocalHoldemGame resumableGame;
    private boolean romanian;
    private boolean configuring;
    private boolean tournamentSetup;
    private boolean showingResult;
    private boolean aiBusy;
    private boolean autoFinishingHand;
    private boolean settlementPending;
    private Runnable deferredSettlementAction;
    // The first screen is built in onCreate; its first-frame work runs after Android resumes the Activity.
    private boolean activityResumed = true;
    private boolean hasResumed;
    private long sceneRevision;
    private Runnable stopSettlement = () -> { };
    private Future<?> aiTask;
    private int setupBlindLevelMinutes = 1;
    private android.widget.Spinner setupBlindIntervalPicker;
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profile = getSharedPreferences("holdem-profile", MODE_PRIVATE);
        sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(getFilesDir()));
        romanian = profile.getBoolean(LANGUAGE, false);
        views = new ScreenViews(this, tag -> tag, HoldemActivity::protect);
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::navigateBack);
        }
        if (state != null && state.getBundle("training-hand") != null) restore(state.getBundle("training-hand"));
        else {
            var saved = sessions.load();
            if (saved.resumed() != null) resumableGame = saved.resumed().game();
            if (state != null && state.getBoolean("configuring")) {
                setupBlindLevelMinutes = state.getInt("setup-blind-level-minutes", 1);
                if (!LocalHoldemGame.BLIND_LEVEL_MINUTES.contains(setupBlindLevelMinutes)) setupBlindLevelMinutes = 1;
                setup(state.getBoolean("tournament-setup"));
            } else home();
            if (saved.hasFailure()) Toast.makeText(this, text(R.string.restore_error), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (game != null) state.putBundle("training-hand", bundle(game.save()));
        state.putBoolean("configuring", configuring);
        state.putBoolean("tournament-setup", tournamentSetup);
        if (configuring && setupBlindIntervalPicker != null) {
            int selected = setupBlindIntervalPicker.getSelectedItemPosition();
            if (selected >= 0 && selected < LocalHoldemGame.BLIND_LEVEL_MINUTES.size())
                setupBlindLevelMinutes = LocalHoldemGame.BLIND_LEVEL_MINUTES.get(selected);
        }
        state.putInt("setup-blind-level-minutes", setupBlindLevelMinutes);
    }

    @Override protected void onDestroy() {
        stopSettlement.run();
        mainHandler.removeCallbacksAndMessages(null);
        aiExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean returningFromBackground = hasResumed;
        hasResumed = true;
        activityResumed = true;
        if (game != null) {
            game.resumeClock();
            if (returningFromBackground) {
                if (showingResult) showResult(game);
                else showHand();
            }
        }
    }

    @Override protected void onPause() {
        activityResumed = false;
        if (game != null) {
            game.pauseClock();
            saveCheckpoint(game, showingResult);
        }
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        deferredSettlementAction = null;
        mainHandler.removeCallbacksAndMessages(null);
        if (aiTask != null) aiTask.cancel(true);
        aiTask = null;
        aiBusy = false;
        super.onPause();
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
        sceneRevision++;
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        if (aiTask != null) aiTask.cancel(true);
        aiTask = null;
        aiBusy = false;
        autoFinishingHand = false;
        if (game != null) {
            resumableGame = game;
            saveCheckpoint(game, showingResult);
        }
        game = null;
        configuring = false;
        showingResult = false;
        setupBlindIntervalPicker = null;
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
        if (resumableGame != null)
            views.addButton(page, text(R.string.continue_game), "continue-game", true, this::resumeGame, true);
        views.addButton(page, text(R.string.play_tournament), "play-tournament", true, () -> setup(true), true);
        views.addButton(page, text(R.string.play_hand), "play-hand", true, () -> setup(false), false);
        views.addText(page, text(R.string.training_summary), 14, ScreenViews.MUTED, false, 10, 0);
    }

    private void resumeGame() {
        var saved = sessions.load();
        if (saved.hasFailure()) {
            Toast.makeText(this, text(R.string.restore_error), Toast.LENGTH_LONG).show();
            return;
        }
        if (saved.resumed() == null) {
            resumableGame = null;
            home();
            return;
        }
        game = saved.resumed().game();
        resumableGame = game;
        showingResult = saved.resumed().showingResult();
        if (showingResult) showResult(game);
        else showHand();
    }

    private void setup(boolean tournament) {
        configuring = true;
        tournamentSetup = tournament;
        var page = views.page(null, text(tournament ? R.string.tournament_setup : R.string.setup));
        var sizes = tournament ? LocalHoldemGame.TOURNAMENT_SIZES : LocalHoldemGame.TABLE_SIZES;
        var picker = views.spinner(sizes.stream().map(String::valueOf).collect(Collectors.toList()), "table-size");
        picker.setSelection(sizes.indexOf(4));
        views.addSetupSelector(page, text(R.string.players), picker);
        android.widget.Spinner blindInterval = null;
        if (tournament) {
            var intervals = LocalHoldemGame.BLIND_LEVEL_MINUTES.stream()
                    .map(minutes -> text(R.string.blind_interval_option, minutes)).collect(Collectors.toList());
            blindInterval = views.spinner(intervals, "blind-level-interval");
            blindInterval.setSelection(LocalHoldemGame.BLIND_LEVEL_MINUTES.indexOf(setupBlindLevelMinutes));
            setupBlindIntervalPicker = blindInterval;
            views.addSetupSelector(page, text(R.string.blind_interval), blindInterval);
        } else {
            setupBlindIntervalPicker = null;
        }
        var selectedInterval = blindInterval;
        views.addButton(page, text(R.string.deal_hand), "deal-hand", true,
                () -> {
                    setupBlindLevelMinutes = selectedInterval == null ? 1
                            : LocalHoldemGame.BLIND_LEVEL_MINUTES.get(selectedInterval.getSelectedItemPosition());
                    open(sizes.get(picker.getSelectedItemPosition()), 0, tournament, setupBlindLevelMinutes);
                }, true);
        views.addQuietButton(page, text(R.string.home), this::home);
    }

    private void open(int size, int dealer, boolean tournament) {
        open(size, dealer, tournament, 1);
    }

    private void open(int size, int dealer, boolean tournament, int blindLevelMinutes) {
        long seed = new SecureRandom().nextLong();
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            seed = getIntent().getLongExtra(DEBUG_DEAL_SEED, seed);
        }
        game = tournament ? LocalHoldemGame.tournament(size, seed, blindLevelMinutes)
                : new LocalHoldemGame(size, dealer, seed);
        configuring = false;
        showingResult = false;
        aiBusy = false;
        autoFinishingHand = false;
        setupBlindIntervalPicker = null;
        resumableGame = game;
        saveCheckpoint(game, false);
        showHand();
    }

    private void restore(Bundle state) {
        try {
            int count = state.getInt("round-count");
            var rounds = new ArrayList<List<LocalHoldemGame.Action>>();
            var roundTimes = new ArrayList<Long>();
            if (count == 0) {
                rounds.add(readActions(state));
            } else {
                for (int i = 0; i < count; i++) rounds.add(readActions(state.getBundle("round-" + i)));
            }
            long[] savedTimes = state.getLongArray("round-start-times");
            for (int i = 0; i < rounds.size(); i++)
                roundTimes.add(savedTimes != null && savedTimes.length == rounds.size() ? savedTimes[i] : 0L);
            int interval = state.containsKey("blind-level-minutes") ? state.getInt("blind-level-minutes") : 1;
            game = LocalHoldemGame.restore(new LocalHoldemGame.Saved(
                    state.getInt("seats"), state.getInt("dealer"), state.getLong("seed"), state.getBoolean("tournament"),
                    interval, state.getLong("elapsed-seconds"), roundTimes, rounds));
            resumableGame = game;
            showingResult = state.getBoolean("showing-result");
            if (showingResult && game.outcome() != null) showResult(game);
            else showHand();
        } catch (IllegalArgumentException invalid) {
            home();
            Toast.makeText(this, text(R.string.restore_error), Toast.LENGTH_LONG).show();
        }
    }

    private static List<LocalHoldemGame.Action> readActions(Bundle state) {
        if (state == null) throw new IllegalArgumentException("Missing round");
        var actions = state.getStringArrayList("actions");
        var amounts = state.getIntArray("amounts");
        if (actions == null || amounts == null || actions.size() != amounts.length) {
            throw new IllegalArgumentException("Incomplete hand state");
        }
        int[] actors = state.getIntArray("actors");
        if (actors != null && actors.length != actions.size()) throw new IllegalArgumentException("Incomplete saved actors");
        var moves = new ArrayList<LocalHoldemGame.Action>(actions.size());
        for (int i = 0; i < amounts.length; i++) {
            int actor = actors == null ? -1 : actors[i];
            moves.add(new LocalHoldemGame.Action(actor,
                    new LocalHoldemGame.Move(ActionType.valueOf(actions.get(i)), amounts[i])));
        }
        return moves;
    }

    private Bundle bundle(LocalHoldemGame.Saved saved) {
        var state = new Bundle();
        state.putInt("seats", saved.seats());
        state.putInt("dealer", saved.dealer());
        state.putLong("seed", saved.seed());
        state.putBoolean("tournament", saved.tournament());
        state.putInt("blind-level-minutes", saved.blindLevelMinutes());
        state.putLong("elapsed-seconds", saved.elapsedSeconds());
        state.putLongArray("round-start-times", saved.roundStartElapsedSeconds().stream().mapToLong(Long::longValue).toArray());
        state.putBoolean("showing-result", showingResult);
        state.putInt("round-count", saved.rounds().size());
        for (int i = 0; i < saved.rounds().size(); i++) state.putBundle("round-" + i, writeActions(saved.rounds().get(i)));
        return state;
    }

    private static Bundle writeActions(List<LocalHoldemGame.Action> moves) {
        var state = new Bundle();
        var actions = new ArrayList<String>(moves.size());
        var amounts = new int[moves.size()];
        var actors = new int[moves.size()];
        for (int i = 0; i < amounts.length; i++) {
            var action = moves.get(i);
            actions.add(action.move().action().name());
            amounts[i] = action.move().amount();
            actors[i] = action.actor();
        }
        state.putStringArrayList("actions", actions);
        state.putIntArray("amounts", amounts);
        state.putIntArray("actors", actors);
        return state;
    }

    private void saveCheckpoint(LocalHoldemGame current, boolean result) {
        try {
            sessions.save(current, result);
        } catch (java.io.IOException | RuntimeException failure) {
            android.util.Log.e("HoldemLab", "Could not persist the current game checkpoint", failure);
            Toast.makeText(this, text(R.string.checkpoint_error), Toast.LENGTH_LONG).show();
        }
    }

    private void afterFirstFrame(Runnable action) {
        View root = getWindow().getDecorView();
        var observer = root.getViewTreeObserver();
        if (!observer.isAlive()) { root.post(action); return; }
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            private boolean complete;
            @Override public boolean onPreDraw() {
                if (!complete) {
                    complete = true;
                    var active = observer.isAlive() ? observer : root.getViewTreeObserver();
                    if (active.isAlive()) active.removeOnPreDrawListener(this);
                    action.run();
                }
                return true;
            }
        });
        root.invalidate();
    }

    private void showHand() {
        long renderedScene = ++sceneRevision;
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        deferredSettlementAction = null;
        showingResult = false;
        var current = game;
        var frame = current.view();
        var turn = current.turn();
        boolean completed = frame.street() == Street.COMPLETE;
        if (completed) autoFinishingHand = false;
        boolean automatedHumanTurn = autoFinishingHand && frame.actor() == frame.viewer() && !completed;
        settlementPending = completed;
        int seats = frame.seats().size();
        var names = PlayerNames.forMatch(Math.max(3, seats), displayName(), seats).subList(0, seats);
        int nextDealer = seats == 2 ? frame.bigBlind() : frame.dealer();
        var table = new HoldemTableView(views, this::text);
        TextView advice = table.show(frame, names, current, this::home, () -> {
            if (game != current || !current.turn().equals(turn)) return;
            Runnable advance = () -> {
                if (game != current || !current.turn().equals(turn)) return;
                if (current.outcome() != null) {
                    deferredSettlementAction = null;
                    showResult(current);
                }
                else if (current.isTournament()) {
                    current.nextHand();
                    saveCheckpoint(current, false);
                    showHand();
                } else open(seats, nextDealer, false);
            };
            if (settlementPending) {
                if (deferredSettlementAction == null) deferredSettlementAction = advance;
                return;
            }
            advance.run();
        }, move -> {
            if (game != current) return false;
            try {
                current.play(turn, move);
            } catch (IllegalArgumentException rejected) {
                return false;
            }
            saveCheckpoint(current, false);
            showHand();
            return true;
        }, automatedHumanTurn, () -> {
            if (aiBusy || game != current || !current.turn().equals(turn) || settlementPending) return;
            autoFinishingHand = true;
            showHand();
        });
        if (advice != null) aiExecutor.execute(() -> {
            LocalHoldemGame.AiSuggestion suggestion = null;
            try {
                suggestion = current.suggest(turn);
            } catch (IllegalArgumentException stale) {
                // The actor changed before the queued hint started.
            } catch (RuntimeException failed) {
                // The view presents an explicit unavailable state; no action is applied.
            }
            var result = suggestion;
            mainHandler.post(() -> {
                if (activityResumed && sceneRevision == renderedScene
                        && !isFinishing() && !isDestroyed() && !aiBusy
                        && game == current && current.turn().equals(turn))
                    table.renderAdvice(advice, result);
            });
        });
        TurnPacing.afterVisibleFrame(current.hasPassiveTurn() || automatedHumanTurn, aiBusy,
                this::afterFirstFrame,
                () -> activityResumed && sceneRevision == renderedScene
                        && !isFinishing() && !isDestroyed() && game == current
                        && current.turn().equals(turn)
                        && (current.hasPassiveTurn() || (autoFinishingHand && current.view().actor() == current.view().viewer())),
                (action, delay) -> mainHandler.postDelayed(action, delay),
                () -> {
                    if (current.hasPassiveTurn()) {
                        try {
                            current.advancePassiveAction(turn);
                            saveCheckpoint(current, false);
                            showHand();
                        } catch (IllegalArgumentException stale) {
                            // A human action or a replacement table invalidated this scheduled turn.
                        } catch (RuntimeException failure) {
                            android.util.Log.e("HoldemLab", "Automated action failed; current checkpoint retained", failure);
                            Toast.makeText(this, text(R.string.ai_action_error), Toast.LENGTH_LONG).show();
                        }
                    } else if (autoFinishingHand && automatedHumanTurn && !aiBusy) {
                        aiBusy = true;
                        aiTask = aiExecutor.submit(() -> {
                            LocalHoldemGame.AiSuggestion suggestion = null;
                            RuntimeException failure = null;
                            try { suggestion = current.suggest(turn); }
                            catch (RuntimeException error) { failure = error; }
                            LocalHoldemGame.AiSuggestion result = suggestion;
                            RuntimeException error = failure;
                            mainHandler.post(() -> {
                                aiBusy = false;
                                aiTask = null;
                                if (!activityResumed || sceneRevision != renderedScene
                                        || isFinishing() || isDestroyed() || game != current
                                        || !current.turn().equals(turn)) return;
                                if (error != null || result == null) {
                                    autoFinishingHand = false;
                                    Toast.makeText(this, text(R.string.ai_action_error), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                try {
                                    current.play(turn, result.move());
                                } catch (IllegalArgumentException stale) {
                                    return;
                                }
                                saveCheckpoint(current, false);
                                showHand();
                            });
                        });
                    }
                });
        if (completed) afterFirstFrame(() -> {
            if (!activityResumed || sceneRevision != renderedScene || game != current
                    || !current.turn().equals(turn) || !settlementPending) return;
            Runnable finish = () -> {
                if (!activityResumed || sceneRevision != renderedScene || game != current
                        || !current.turn().equals(turn) || !settlementPending) return;
                settlementPending = false;
                stopSettlement = () -> { };
                if (current.outcome() != null) showResult(current);
                else {
                    Runnable deferred = deferredSettlementAction;
                    deferredSettlementAction = null;
                    if (deferred != null) deferred.run();
                }
            };
            var animation = table.animateSettlement(ro.cardtable.ui.animation.TrickCollectionMotion.DISPLAY_MILLIS, finish);
            stopSettlement = () -> {
                animation.cancel().run();
                mainHandler.removeCallbacks(finish);
            };
            if (!animation.started())
                mainHandler.postDelayed(finish, ro.cardtable.ui.animation.TrickCollectionMotion.DISPLAY_MILLIS);
        });
    }

    private void showResult(LocalHoldemGame current) {
        sceneRevision++;
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        showingResult = true;
        resumableGame = current;
        saveCheckpoint(current, true);
        views.applyStandardWindowChrome();
        boolean won = current.outcome() == LocalHoldemGame.Outcome.WON;
        var page = views.page(null, text(won ? R.string.tournament_won : R.string.tournament_lost));
        var table = current.view();
        views.addText(page, text(R.string.tournament_summary, current.handNumber(), table.seats().get(table.viewer()).stack()),
                16, ScreenViews.MUTED, false, 10, 0);
        var names = PlayerNames.forMatch(Math.max(3, table.seats().size()), displayName(), table.seats().size())
                .subList(0, table.seats().size());
        new HoldemTableView(views, this::text).addPostGameReview(page, table, names, current);
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
