package ro.atf.holdem.android;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
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
import android.widget.CheckBox;
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
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public final class HoldemActivity extends Activity {
    private static final String NAME = "player-name";
    private static final String LANGUAGE = "language";
    private static final String PLAYER_ID = "player-id";
    static final String DEBUG_DEAL_SEED = "ro.atf.holdem.android.DEBUG_DEAL_SEED";
    private SharedPreferences profile;
    private ScreenViews views;
    private HoldemTableView table;
    private HoldemSessionRepository sessions;
    private TournamentLeaderboardRepository leaderboard;
    private HoldemProgressStore progress;
    private HoldemAchievementNotice achievementNotice;
    private LocalHoldemGame game;
    private LocalHoldemGame resumableGame;
    private boolean romanian;
    private boolean configuring;
    private boolean tournamentSetup;
    private boolean showingResult;
    private final HandAutoPlay autoPlay = new HandAutoPlay();
    private boolean settlementPending;
    private HoldemSessionRepository.Resumed deferredContinuation;
    // The first screen is built in onCreate; its first-frame work runs after Android resumes the Activity.
    private boolean activityResumed = true;
    private boolean hasResumed;
    private long sceneRevision;
    private Runnable stopSettlement = () -> { };
    private int setupBlindLevelMinutes = 1;
    private android.widget.Spinner setupBlindIntervalPicker;
    private android.widget.Spinner setupDifficultyPicker;
    private CheckBox setupWinChance;
    private final ExecutorService winChanceExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HoldemTableTasks tableTasks = new HoldemTableTasks(aiExecutor, winChanceExecutor, mainHandler::post);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        profile = getSharedPreferences("holdem-profile", MODE_PRIVATE);
        sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(getFilesDir()));
        leaderboard = new TournamentLeaderboardRepository(new AtomicFileTournamentLeaderboardStorage(getFilesDir()));
        progress = new HoldemProgressStore(sessions, leaderboard,
                GameplayAchievementFile.local(this));
        romanian = profile.getBoolean(LANGUAGE, false);
        views = new ScreenViews(this, tag -> tag, HoldemActivity::protect);
        table = new HoldemTableView(views, this::text);
        achievementNotice = new HoldemAchievementNotice(views, this::text, () -> localizedContext(this, romanian));
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::navigateBack);
        }
        var saved = sessions.load();
        if (saved.hasCheckpoint()) {
            try {
                progress.recordCompleted(saved.resumed().game(), displayName());
            } catch (java.io.IOException | RuntimeException failure) {
                android.util.Log.e("HoldemLab", "Could not recover the completed tournament", failure);
                Toast.makeText(this, text(R.string.leaderboard_error), Toast.LENGTH_LONG).show();
            }
        }
        var legacy = state == null ? null : state.getBundle("training-hand");
        boolean restoreActive = state != null && (state.getBoolean("resume-active-session") || legacy != null);
        if (restoreActive && saved.hasCheckpoint()) showResumed(saved.resumed());
        else if (legacy != null && !saved.hasFailure()) restore(legacy);
        else {
            if (saved.resumed() != null) resumableGame = saved.resumed().game();
            if (state != null && state.getBoolean("configuring")) {
                setupBlindLevelMinutes = state.getInt("setup-blind-level-minutes", 1);
                if (!LocalHoldemGame.BLIND_LEVEL_MINUTES.contains(setupBlindLevelMinutes)) setupBlindLevelMinutes = 1;
                setup(state.getBoolean("tournament-setup"), state.getBoolean("setup-win-chance"),
                        state.getBoolean("setup-hard"));
            } else home();
            if (saved.hasFailure()) Toast.makeText(this, text(R.string.restore_error), Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("resume-active-session", game != null);
        state.putBoolean("configuring", configuring);
        state.putBoolean("tournament-setup", tournamentSetup);
        if (configuring && setupBlindIntervalPicker != null) {
            int selected = setupBlindIntervalPicker.getSelectedItemPosition();
            if (selected >= 0 && selected < LocalHoldemGame.BLIND_LEVEL_MINUTES.size())
                setupBlindLevelMinutes = LocalHoldemGame.BLIND_LEVEL_MINUTES.get(selected);
        }
        state.putInt("setup-blind-level-minutes", setupBlindLevelMinutes);
        state.putBoolean("setup-win-chance", configuring && setupWinChance != null && setupWinChance.isChecked());
        state.putBoolean("setup-hard", configuring && setupDifficultyPicker != null
                && setupDifficultyPicker.getSelectedItemPosition() == LocalHoldemGame.Difficulty.HARD.ordinal());
    }

    @Override protected void onDestroy() {
        stopSettlement.run();
        mainHandler.removeCallbacksAndMessages(null);
        achievementNotice.close();
        tableTasks.cancelTurn();
        aiExecutor.shutdownNow();
        winChanceExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        boolean returningFromBackground = hasResumed;
        hasResumed = true;
        activityResumed = true;
        if (deferredContinuation != null) {
            showResumed(deferredContinuation);
        } else if (game != null) {
            game.resumeClock();
            if (returningFromBackground) {
                if (showingResult) showResult(game);
                else showHand();
            }
        }
    }

    @Override protected void onPause() {
        activityResumed = false;
        autoPlay.setEnabled(false);
        achievementNotice.clear();
        if (game != null) {
            game.pauseClock();
            if (deferredContinuation == null) saveCheckpoint(game, showingResult);
        }
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        mainHandler.removeCallbacksAndMessages(null);
        tableTasks.cancelTurn();
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
        tableTasks.cancelTurn();
        autoPlay.setEnabled(false);
        if (deferredContinuation != null) {
            resumableGame = deferredContinuation.game();
            deferredContinuation = null;
        } else if (game != null) {
            resumableGame = game;
            saveCheckpoint(game, showingResult);
        }
        game = null;
        achievementNotice.clear();
        configuring = false;
        showingResult = false;
        setupBlindIntervalPicker = null;
        setupDifficultyPicker = null;
        setupWinChance = null;
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
        views.addButton(page, text(R.string.tournament_leaderboard), "tournament-leaderboard", true,
                this::showLeaderboard, false);
        views.addButton(page, text(R.string.play_hand), "play-hand", true, () -> setup(false), false);
        views.addButton(page, text(R.string.achievements), "achievements", true,
                () -> startActivity(new android.content.Intent(this, GameplayAchievementsActivity.class)
                        .putExtra(GameplayAchievementsActivity.PROFILE_ID, stablePlayerId())), false);
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
        showResumed(saved.resumed());
    }

    private void showResumed(HoldemSessionRepository.Resumed resumed) {
        deferredContinuation = null;
        autoPlay.setEnabled(false);
        game = resumed.game();
        resumableGame = game;
        game.resumeClock();
        showingResult = resumed.showingResult();
        if (showingResult) showResult(game);
        else showHand();
    }

    private void setup(boolean tournament) {
        setup(tournament, false, false);
    }

    private void setup(boolean tournament, boolean showWinChance, boolean hard) {
        configuring = true;
        tournamentSetup = tournament;
        var page = views.page(null, text(tournament ? R.string.tournament_setup : R.string.setup));
        var sizes = tournament ? LocalHoldemGame.TOURNAMENT_SIZES : LocalHoldemGame.TABLE_SIZES;
        var picker = views.spinner(sizes.stream().map(String::valueOf).collect(Collectors.toList()), "table-size");
        picker.setSelection(sizes.indexOf(4));
        views.addSetupSelector(page, text(R.string.players), picker);
        android.widget.Spinner blindInterval = null;
        setupDifficultyPicker = null;
        if (tournament) {
            setupDifficultyPicker = views.spinner(List.of("Normal", "Hard"), "ai-level");
            setupDifficultyPicker.setContentDescription(text(R.string.ai_level));
            setupDifficultyPicker.setSelection(hard ? LocalHoldemGame.Difficulty.HARD.ordinal()
                    : LocalHoldemGame.Difficulty.NORMAL.ordinal());
            views.addSetupSelector(page, text(R.string.ai_level), setupDifficultyPicker);
            var intervals = LocalHoldemGame.BLIND_LEVEL_MINUTES.stream()
                    .map(minutes -> text(R.string.blind_interval_option, minutes)).collect(Collectors.toList());
            blindInterval = views.spinner(intervals, "blind-level-interval");
            blindInterval.setSelection(LocalHoldemGame.BLIND_LEVEL_MINUTES.indexOf(setupBlindLevelMinutes));
            setupBlindIntervalPicker = blindInterval;
            views.addSetupSelector(page, text(R.string.blind_interval), blindInterval);
            setupWinChance = new CheckBox(this);
            setupWinChance.setText(text(R.string.show_win_chance));
            setupWinChance.setTextColor(ScreenViews.INK);
            setupWinChance.setChecked(showWinChance);
            setupWinChance.setMinimumHeight(views.dp(48));
            page.addView(setupWinChance, views.matchWrap());
        } else {
            setupBlindIntervalPicker = null;
            setupWinChance = null;
        }
        var selectedInterval = blindInterval;
        var selectedWinChance = setupWinChance;
        var selectedDifficulty = setupDifficultyPicker;
        views.addButton(page, text(R.string.deal_hand), "deal-hand", true,
                () -> {
                    setupBlindLevelMinutes = selectedInterval == null ? 1
                            : LocalHoldemGame.BLIND_LEVEL_MINUTES.get(selectedInterval.getSelectedItemPosition());
                    open(sizes.get(picker.getSelectedItemPosition()), tournament, setupBlindLevelMinutes,
                            selectedWinChance != null && selectedWinChance.isChecked(),
                            selectedDifficulty != null && selectedDifficulty.getSelectedItemPosition()
                                    == LocalHoldemGame.Difficulty.HARD.ordinal()
                                    ? LocalHoldemGame.Difficulty.HARD : LocalHoldemGame.Difficulty.NORMAL);
                }, true);
        views.addQuietButton(page, text(R.string.home), this::home);
    }

    private void open(int size, boolean tournament, int blindLevelMinutes, boolean showWinChance,
                      LocalHoldemGame.Difficulty difficulty) {
        long seed = newDealSeed();
        var next = tournament ? LocalHoldemGame.tournament(size, seed, blindLevelMinutes, stablePlayerId(), showWinChance,
                difficulty)
                : LocalHoldemGame.table(size, 0, seed, stablePlayerId());
        try {
            progress.start(resumableGame, next, displayName());
        } catch (java.io.IOException | RuntimeException failure) {
            reportCheckpointFailure(failure);
            return;
        }
        game = next;
        configuring = false;
        showingResult = false;
        autoPlay.setEnabled(false);
        setupBlindIntervalPicker = null;
        setupDifficultyPicker = null;
        setupWinChance = null;
        resumableGame = game;
        showHand();
    }

    private long newDealSeed() {
        long seed = new SecureRandom().nextLong();
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                ? getIntent().getLongExtra(DEBUG_DEAL_SEED, seed) : seed;
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
            else {
                saveCheckpoint(game, false);
                showHand();
            }
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

    private TournamentLeaderboard.State saveCheckpoint(LocalHoldemGame current, boolean result) {
        try {
            return progress.checkpoint(current, result, displayName(), changes -> {
                if (activityResumed) {
                    achievementNotice.record(current, changes);
                }
            });
        } catch (java.io.IOException | RuntimeException failure) {
            reportCheckpointFailure(failure);
            return null;
        }
    }

    private void reportCheckpointFailure(Exception failure) {
        android.util.Log.e("HoldemLab", "Could not persist game progress", failure);
        Toast.makeText(this, text(R.string.checkpoint_error), Toast.LENGTH_LONG).show();
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
        showingResult = false;
        var current = game;
        var frame = current.view();
        var turn = current.turn();
        boolean completed = frame.street() == Street.COMPLETE;
        if (!HoldemTablePresentation.canDelegate(frame)) autoPlay.setEnabled(false);
        long delegationRevision = autoPlay.revision();
        boolean automatedHumanTurn = autoPlay.isEnabled() && frame.actor() == frame.viewer() && !completed;
        settlementPending = completed;
        var names = displayNames(current);
        var labels = table.show(frame, names, current, this::home, () -> {
            if (!activityResumed || sceneRevision != renderedScene || game != current
                    || !current.turn().equals(turn) || deferredContinuation != null) return;
            try {
                progress.recordCompleted(current, displayName());
                var destination = sessions.continueAfterSettlement(current, turn, this::newDealSeed);
                if (settlementPending) deferredContinuation = destination;
                else showResumed(destination);
            } catch (java.io.IOException | RuntimeException failure) {
                android.util.Log.e("HoldemLab", "Could not commit the settled hand continuation", failure);
                Toast.makeText(this, text(R.string.checkpoint_error), Toast.LENGTH_LONG).show();
            }
        }, move -> {
            if (!activityResumed || sceneRevision != renderedScene || game != current || autoPlay.isEnabled()) return false;
            try {
                current.play(turn, move);
            } catch (IllegalArgumentException rejected) {
                return false;
            }
            saveCheckpoint(current, false);
            showHand();
            return true;
        }, autoPlay.isEnabled(), checked -> {
            if (!activityResumed || sceneRevision != renderedScene || game != current
                    || !current.turn().equals(turn) || settlementPending || !HoldemTablePresentation.canDelegate(frame)) return;
            autoPlay.change(checked, frame.actor() == frame.viewer(), () -> {
                tableTasks.cancelTurn();
                showHand();
            });
        });
        BooleanSupplier visibleScene = () -> activityResumed && sceneRevision == renderedScene
                && !isFinishing() && !isDestroyed();
        BooleanSupplier visibleTurn = () -> visibleScene.getAsBoolean()
                && game == current && current.turn().equals(turn);
        if (labels.winChance() != null) {
            tableTasks.estimate(HoldemWinChance.from(frame), visibleScene,
                    percent -> table.renderWinChance(labels.winChance(), percent));
        }
        if (labels.advice() != null) {
            tableTasks.suggest(() -> current.suggest(turn), visibleTurn,
                    suggestion -> table.renderAdvice(labels.advice(), suggestion));
        }
        TurnPacing.afterVisibleFrame(current.hasPassiveTurn() || automatedHumanTurn, tableTasks.isTurnPending(),
                this::afterFirstFrame,
                () -> visibleTurn.getAsBoolean()
                        && (current.hasPassiveTurn() || (automatedHumanTurn && autoPlay.accepts(delegationRevision))),
                (action, delay) -> mainHandler.postDelayed(action, delay),
                () -> playAutomatedTurn(current, turn, automatedHumanTurn, delegationRevision, visibleTurn));
        if (completed) afterFirstFrame(() -> {
            if (!activityResumed || sceneRevision != renderedScene || game != current
                    || !current.turn().equals(turn) || !settlementPending) return;
            Runnable finish = () -> {
                if (!activityResumed || sceneRevision != renderedScene || game != current
                        || !current.turn().equals(turn) || !settlementPending) return;
                settlementPending = false;
                stopSettlement = () -> { };
                if (deferredContinuation != null) showResumed(deferredContinuation);
                else achievementNotice.show(current);
            };
            var animation = table.animateSettlement(ro.cardtable.ui.animation.TrickCollectionMotion.DISPLAY_MILLIS, finish);
            stopSettlement = () -> {
                animation.cancel().run();
                mainHandler.removeCallbacks(finish);
            };
            if (!animation.started()) mainHandler.post(finish);
        });
    }

    private void playAutomatedTurn(LocalHoldemGame current, LocalHoldemGame.Turn turn,
                                   boolean automatedHumanTurn, long delegationRevision,
                                   BooleanSupplier visibleTurn) {
        boolean opponentTurn = current.hasPassiveTurn();
        if (opponentTurn && current.difficulty() == LocalHoldemGame.Difficulty.NORMAL) {
            try {
                current.advancePassiveAction(turn);
                saveCheckpoint(current, false);
                showHand();
            } catch (IllegalArgumentException stale) {
                // A newer turn invalidated this scheduled action.
            } catch (RuntimeException failure) {
                android.util.Log.e("HoldemLab", "Automated action failed; current checkpoint retained", failure);
                Toast.makeText(this, text(R.string.ai_action_error), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!opponentTurn && !(automatedHumanTurn && autoPlay.accepts(delegationRevision))) return;
        tableTasks.play(() -> opponentTurn ? current.suggestOpponent(turn) : current.suggest(turn).move(),
                () -> visibleTurn.getAsBoolean() && (opponentTurn || autoPlay.accepts(delegationRevision)),
                move -> {
                    try {
                        if (opponentTurn) current.playOpponent(turn, move);
                        else current.playDelegated(turn, move);
                    } catch (IllegalArgumentException stale) {
                        return;
                    }
                    saveCheckpoint(current, false);
                    showHand();
                }, () -> {
                    Toast.makeText(this, text(R.string.ai_action_error), Toast.LENGTH_LONG).show();
                    if (!opponentTurn) {
                        autoPlay.setEnabled(false);
                        showHand();
                    }
                });
    }

    private void showResult(LocalHoldemGame current) {
        sceneRevision++;
        stopSettlement.run();
        stopSettlement = () -> { };
        settlementPending = false;
        showingResult = true;
        resumableGame = current;
        var leaderboardState = saveCheckpoint(current, true);
        views.applyStandardWindowChrome();
        boolean won = current.outcome() == LocalHoldemGame.Outcome.WON;
        var page = views.page(null, text(won ? R.string.tournament_won : R.string.tournament_lost));
        var table = current.view();
        var names = displayNames(current);
        var physicalNames = HoldemTablePresentation.physicalSeatNames(table, names);
        views.addText(page, text(R.string.tournament_summary, current.handNumber(), table.seats().get(table.viewer()).stack()),
                16, ScreenViews.MUTED, false, 10, 0);
        if (leaderboardState != null) addTournamentStandings(page, current, leaderboardState, physicalNames);
        new HoldemTableView(views, this::text).addPostGameReview(page, table, names, current);
        views.addButton(page, text(R.string.play_again), "play-again", true, () -> {
            game = null;
            showingResult = false;
            setup(true);
        }, true);
        views.addQuietButton(page, text(R.string.home), this::home);
        if (activityResumed) achievementNotice.show(current);
    }

    private void showLeaderboard() {
        var page = views.page(null, text(R.string.tournament_leaderboard));
        try {
            var top = leaderboard.load().topForProfile(stablePlayerId(), displayName());
            if (top.isEmpty()) {
                views.addText(page, text(R.string.leaderboard_empty), 14, ScreenViews.MUTED, false, 10, 0);
            } else {
                views.addText(page, text(R.string.leaderboard_title), 14, ScreenViews.MUTED, true, 8, 2);
                for (int index = 0; index < top.size(); index++) {
                    var entry = top.get(index);
                    views.addText(page, text(R.string.leaderboard_entry, TournamentLeaderboard.rankAt(top, index), entry.displayName(),
                            entry.points(), entry.tournaments(), entry.bestRank()), 14, ScreenViews.INK, false, 4, 0);
                }
            }
        } catch (java.io.IOException | RuntimeException failure) {
            android.util.Log.e("HoldemLab", "Tournament leaderboard could not be loaded", failure);
            views.addText(page, text(R.string.leaderboard_error), 14, ScreenViews.MUTED, false, 10, 0);
        }
        views.addQuietButton(page, text(R.string.home), this::home);
    }

    private void addTournamentStandings(android.widget.LinearLayout page, LocalHoldemGame current,
                                        TournamentLeaderboard.State leaderboardState, List<String> physicalNames) {
        views.addText(page, text(R.string.tournament_points), 14, ScreenViews.MUTED, true, 8, 2);
        var ranks = current.finishingRanks();
        for (int seat = 0; seat < ranks.size(); seat++) {
            String id = current.playerId(seat);
            var entry = leaderboardState.entries().stream().filter(candidate -> candidate.playerId().equals(id))
                    .findFirst().orElse(null);
            if (entry != null) views.addText(page, text(R.string.tournament_standing, ranks.get(seat),
                    physicalNames.get(seat), entry.points()), 13, ScreenViews.INK, false, 3, 0);
        }
    }

    private List<String> displayNames(LocalHoldemGame current) {
        int seats = current.view().seats().size();
        return HoldemTablePresentation.playerNames(seats, displayName(), current.displayNameSeed());
    }

    private String stablePlayerId() {
        String id = profile.getString(PLAYER_ID, null);
        if (id != null && !id.isBlank()) return id;
        id = UUID.randomUUID().toString();
        if (!profile.edit().putString(PLAYER_ID, id).commit())
            throw new IllegalStateException("Could not persist the local player identity");
        return id;
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
        String name = profile.getString(NAME, null);
        if (name == null) return text(R.string.you);
        String trimmed = name.trim();
        return trimmed.isEmpty() ? text(R.string.you) : trimmed;
    }

    private String text(int id, Object... arguments) {
        return localizedContext(this, romanian).getString(id, arguments);
    }

    static Context localizedContext(Context base) {
        return localizedContext(base, base.getSharedPreferences("holdem-profile", MODE_PRIVATE).getBoolean(LANGUAGE, false));
    }

    // Both locales ship in the same APK.
    @SuppressLint("AppBundleLocaleChanges")
    static Context localizedContext(Context base, boolean romanian) {
        var configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(romanian ? Locale.forLanguageTag("ro") : Locale.ENGLISH);
        return base.createConfigurationContext(configuration);
    }

    static void protect(View root) {
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
