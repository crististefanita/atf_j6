package ro.atf.holdem.android;

import ro.cardtable.ui.screens.GameTitleBar;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import ro.atf.holdem.core.ActionType;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.atf.holdem.core.Street;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import ro.cardtable.ui.screens.GameTitleBar;

/** A small device suite that drives the same accessible controls as the player. */
public final class TournamentSanity extends Instrumentation {
    private static final String RENAMED_PROFILE = "Alex Poker";
    private static final String ORIGINAL_LEADERBOARD_ROW = "#1 · You · Points 100 · Tournaments 1 · Best finish 1";
    private static final String RENAMED_LEADERBOARD_ROW = "#1 · " + RENAMED_PROFILE + " · Points 100 · Tournaments 1 · Best finish 1";
    private final JSONArray actions = new JSONArray();
    private final JSONArray assertions = new JSONArray();
    private final JSONArray captures = new JSONArray();
    private UiAutomation ui;
    private boolean waitedForSystemUi;
    private Bundle arguments;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        var result = new Bundle();
        String failure = "";
        String apkHash = "";
        String journey = arguments.getString("mode", "tournament");
        try {
            apkHash = installedApkHash();
            ui = journey.equals("seat-metrics") ? getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                    : getUiAutomation();
            var service = ui.getServiceInfo();
            service.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(service);
            if (journey.equals("tournament")) {
                winAcrossThreeHands();
                loseOnTheOpeningAllIn();
            } else if (journey.equals("release-smoke")) {
                releaseEntryNavigation();
            } else if (journey.equals("hard-six")) {
                inspectHardSixTournament();
            } else if (journey.equals("settlement-award")) {
                inspectSettlementAward();
            } else if (journey.equals("settlement-multi-award")) {
                inspectMultipleAwards(SettlementAwardFixture.SPLIT, true);
                inspectMultipleAwards(SettlementAwardFixture.SIDE, true);
            } else if (journey.equals("settlement-static-award")) {
                inspectMultipleAwards(SettlementAwardFixture.SIDE, false);
            } else if (journey.equals("settlement-compact-award")) {
                inspectCompactAward();
            } else if (journey.equals("settlement-resume")) {
                resumeNextHandDuringSettlement();
                resumeResultDuringSettlement();
            } else if (journey.equals("leaderboard-settlement")) {
                recordBeforeOpeningResult();
            } else if (journey.equals("leaderboard-profile-restart")) {
                switch (arguments.getString("phase", "")) {
                    case "rename" -> renameRankedProfile();
                    case "restart" -> verifyRankedProfileAfterRestart();
                    default -> throw new IllegalArgumentException("Expected rename or restart phase");
                }
            } else if (journey.equals("seat-metrics")) {
                inspectCompactSeatMetrics();
            } else if (journey.equals("table-geometry")) {
                inspectTableGeometry();
            } else {
                throw new IllegalArgumentException("Unknown Android sanity mode: " + journey);
            }
        } catch (Exception | AssertionError problem) {
            failure = problem.toString();
        }
        try {
            var config = getTargetContext().getResources().getConfiguration();
            var receipt = new JSONObject().put("api", Build.VERSION.SDK_INT).put("journey", journey).put("apkSha256", apkHash)
                    .put("window", config.screenWidthDp + " × " + config.screenHeightDp + " dp")
                    .put("density", config.densityDpi).put("fontScale", config.fontScale).put("actions", actions)
                    .put("assertions", assertions).put("captures", captures).put("error", failure)
                    .put("status", failure.isEmpty() ? "PASS" : "FAIL");
            result.putString("receipt", receipt.toString());
        } catch (Exception problem) {
            failure += " Evidence write failed: " + problem;
        }
        result.putString("stream", failure.isEmpty() ? "PASS Holdem Android sanity (" + journey + ")\n" : "FAIL " + failure + "\n");
        finish(failure.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    private void inspectTableGeometry() throws Exception {
        var activity = launchHoldem();
        var results = new ArrayList<TableGeometryComponent.Probe>();
        try {
            runOnMainSync(() -> results.addAll(TableGeometryComponent.inspect(activity)));
            boolean passed = true;
            for (var result : results) {
                assertions.put(new JSONObject().put("name", result.name()).put("expected", result.expected())
                        .put("actual", result.actual()).put("passed", result.passed()));
                passed &= result.passed();
            }
            if (!passed) throw new AssertionError("Table geometry drifted; see individual component assertions.");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void inspectHardSixTournament() throws Exception {
        var activity = launchHoldem();
        try {
            tap("Play a tournament");
            selectTableSize("6");
            var setup = activity.getWindow().getDecorView();
            runOnMainSync(() -> {
                android.widget.Spinner difficulty = setup.findViewWithTag("ai-level");
                if (difficulty == null || !difficulty.performClick())
                    throw new AssertionError("Difficulty selector could not be opened");
            });
            actions.put("Open the difficulty selector");
            tap("Hard");
            capture("01-hard-six-setup");
            tap("Deal cards");
            var sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir()));
            var opening = sessions.load().resumed();
            check("The opening checkpoint is Hard with six seats", "HARD / 6",
                    opening == null ? "missing" : opening.game().difficulty() + " / " + opening.game().view().seats().size());
            capture("02-hard-six-table");

            int human = opening.game().view().viewer();
            int normalSeat = Math.floorMod(human + 5, 6);
            tap(revealTaggedControl(activity, "CALL"));
            List<LocalHoldemGame.Action> ledger = List.of();
            long deadline = SystemClock.uptimeMillis() + 25000;
            do {
                var resumed = sessions.load().resumed();
                if (resumed != null) ledger = resumed.game().save().rounds().get(0);
                if (ledger.stream().anyMatch(action -> action.actor() == normalSeat)) break;
                SystemClock.sleep(120);
            } while (SystemClock.uptimeMillis() < deadline);

            long opponents = ledger.stream().filter(action -> action.actor() != human)
                    .map(LocalHoldemGame.Action::actor).distinct().count();
            check("All five opponents act after the human calls", "5", Long.toString(opponents));
            var normalActions = ledger.stream().filter(action -> action.actor() == normalSeat)
                    .map(action -> action.move().action()).collect(java.util.stream.Collectors.toList());
            check("The Normal opponent checks or calls inside Hard", "one or more CHECK/CALL actions",
                    normalActions.toString(), !normalActions.isEmpty() && normalActions.stream()
                            .allMatch(action -> action == ActionType.CHECK || action == ActionType.CALL));
            capture("03-hard-six-action");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void releaseEntryNavigation() throws Exception {
        var intent = getTargetContext().getPackageManager().getLaunchIntentForPackage(getTargetContext().getPackageName());
        if (intent == null) throw new AssertionError("Release APK has no launcher activity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        var activity = startActivitySync(intent);
        try {
            see("Release APK opens the branded home menu", "Hold’em Lab");
            tap("Play a hand");
            see("Release APK opens hand setup", "Table setup");
            capture("01-release-hand-setup");
            tap("Main menu");
            see("Hand setup returns to the home menu", "Hold’em Lab");
            tap("Play a tournament");
            see("Release APK opens tournament setup", "Tournament setup");
            capture("02-release-tournament-setup");
            tap("Main menu");
            inspectNewAchievementCatalog();
            inspectRomanianAchievementLabels();
            inspectStableTableHand(activity);
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void inspectNewAchievementCatalog() throws Exception {
        var monitor = addMonitor(GameplayAchievementsActivity.class.getName(), null, false);
        Activity achievements;
        try {
            tap("Achievements");
            achievements = monitor.waitForActivityWithTimeout(5000);
        } finally {
            removeMonitor(monitor);
        }
        if (achievements == null) throw new AssertionError("Achievement Activity did not open");
        see("Achievements opens from the main menu", "Achievements");
        see("All active achievements start locked", "Unlocked 0/14");
        see("Achievement list has an English accessibility label", "Achievement list");
        scrollAchievementTo("Easy");
        achievementRow("Win a poker hand", "Progress · 0/1");
        achievementRow("Reach your first showdown", "Progress · 0/1");
        achievementRow("Win a pot from the big blind", "Progress · 0/1");
        achievementRow("Win 3 different hands", "Progress · 0/3");
        achievementRow("Finish your run in a tournament", "Progress · 0/1");
        capture("03-achievements-easy");

        scrollAchievementTo("Medium");
        achievementRow("Win a tournament", "Progress · 0/1");
        achievementRow("Share a pot at showdown", "Progress · 0/1");
        achievementRow("Win a contested side pot", "Progress · 0/1");
        achievementRow("Win 1 hand at showdown with at least three players still in the hand", "Progress · 0/1");
        achievementRow("Win 2 hands at showdown with a straight or better", "Progress · 0/2");
        achievementRow("Finish in the top three in 2 different tournaments", "Progress · 0/2");
        capture("04-achievements-medium");

        scrollAchievementTo("Hard");
        achievementRow("Win 3 different tournaments", "Progress · 0/3");
        achievementRow("Win 2 hands at showdown with a full house or better", "Progress · 0/2");
        achievementRow("Win 2 tournaments that started with six players", "Progress · 0/2");
        capture("05-achievements-hard");
        searchAchievements("contested side pot");
        see("Search narrows the catalog", "Showing 1 of 14");
        var row = scrollAchievementTo("Win a contested side pot");
        tap("Win a contested side pot", row.isClickable() ? row : row.getParent());
        see("The shared detail exposes the eligibility rule",
                "Receive chips from a side pot with at least two players eligible at settlement.");
        see("Achievement details explain tournament-only credit", "Only tournament play earns progress.");
        capture("05a-achievement-side-pot-detail");
        tapDialogAction("Close");
        inspectAchievementSearchAfterRecreation(achievements);
        searchAchievements("training exercises");
        see("Training has no entry in the gameplay catalog", "No achievements match these filters.");
        tap("Back");
    }

    private void inspectAchievementSearchAfterRecreation(Activity original) throws Exception {
        String query = "in 3 distinct tournament hands";
        String rule = "Receive pot chips in 3 distinct tournament hands. Each hand contributes once across all pot layers.";
        searchAchievements(query, "Easy", "Locked");
        see("Search finds a rendered YAML threshold with difficulty and status filters", "Showing 1 of 14");
        var result = scrollAchievementTo("Win 3 different hands");
        tap("Win 3 different hands", result.isClickable() ? result : result.getParent());
        see("Achievement detail renders the same threshold as search", rule);
        var monitor = addMonitor(GameplayAchievementsActivity.class.getName(), null, false);
        try {
            runOnMainSync(original::recreate);
            Activity restored = monitor.waitForActivityWithTimeout(5000);
            check("Achievement Activity is recreated", "new Activity",
                    restored != null && restored != original ? "new Activity" : "recreation missing");
        } finally {
            removeMonitor(monitor);
        }
        see("Achievement detail survives Activity recreation", rule);
        tapDialogAction("Close");
        see("Filtered achievement list survives Activity recreation", "Showing 1 of 14");
        tap("Search and filters");
        var field = awaitMatch(node -> node.isEditable() && query.equals(value(node.getText())));
        boolean restored = field != null && selectedAchievementFilter("Difficulty", "Easy")
                && selectedAchievementFilter("Status", "Locked");
        check("Achievement search restores all three filter selections", "text, difficulty and status restored",
                restored ? "text, difficulty and status restored" : "filter selection missing");
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
    }

    private void searchAchievements(String query) throws Exception {
        searchAchievements(query, null, null);
    }

    private void searchAchievements(String query, String difficulty, String status) throws Exception {
        tap("Search and filters");
        var field = await("Search by title or rule");
        if (field == null) {
            var editFields = collect(node -> node.isEditable() && "android.widget.EditText".contentEquals(node.getClassName()));
            field = editFields.size() == 1 ? editFields.get(0) : null;
        }
        var text = new Bundle();
        text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, query);
        check("Set achievement search", "text entered",
                field != null && field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)
                        ? "text entered" : "search unavailable");
        if (difficulty != null) selectAchievementFilter("Difficulty", difficulty);
        if (status != null) selectAchievementFilter("Status", status);
        tapDialogAction("Apply");
    }

    private AccessibilityNodeInfo achievementFilter(String name) {
        return awaitMatch(node -> "android.widget.Spinner".contentEquals(node.getClassName())
                && name.equals(value(node.getContentDescription())));
    }

    private void selectAchievementFilter(String name, String option) throws Exception {
        tap(name, achievementFilter(name));
        var item = await(option);
        while (item != null && !item.isClickable()) item = item.getParent();
        tap(option, item);
    }

    private boolean selectedAchievementFilter(String name, String option) {
        var selected = new ArrayList<AccessibilityNodeInfo>();
        collect(achievementFilter(name), node -> option.equals(value(node.getText())), selected);
        return !selected.isEmpty();
    }

    private void tapDialogAction(String label) throws Exception {
        var button = awaitMatch(node -> node.isClickable()
                && "android.widget.Button".contentEquals(node.getClassName())
                && label.equalsIgnoreCase(value(node.getText())));
        tap(label, button);
    }

    private void inspectRomanianAchievementLabels() throws Exception {
        tap("interface-language");
        tap("Română");
        tap("Realizări");
        see("Achievement list has a Romanian accessibility label", "Lista realizărilor");
        scrollAchievementTo("Ușoare");
        achievementRow("Ajungi prima dată la dezvăluirea cărților", "Progres · 0/1");
        achievementRow("Câștigă 3 mâini diferite", "Progres · 0/3");
        capture("05a-achievements-romanian-easy");
        scrollAchievementTo("Medii");
        achievementRow("Împarte un pot la dezvăluire", "Progres · 0/1");
        achievementRow("Câștigă un pot secundar disputat", "Progres · 0/1");
        capture("05b-achievements-romanian-medium");
        scrollAchievementTo("Grele");
        achievementRow("Câștigă 3 turnee diferite", "Progres · 0/3");
        capture("05c-achievements-romanian-hard");
        tap("Înapoi");
    }

    private void inspectStableTableHand(Activity activity) throws Exception {
        tap("Joacă un turneu");
        tap("Împarte cărțile");
        await("Mărește");
        assertActionLanguage(activity, "Romanian", "Mărește", "Automat");
        capture("05d-stable-table-romanian-preflop");

        var actors = new java.util.HashSet<String>();
        List<Rect> fixed = null;
        boolean opponentCaptured = false;
        int samples = 0;
        String drift = "";
        long deadline = SystemClock.uptimeMillis() + 35000;
        while (SystemClock.uptimeMillis() < deadline) {
            var current = tableSample(activity);
            if (current != null && current.settled()) break;
            if (current != null) {
                actors.add(current.actor());
                if (fixed == null) fixed = current.bounds();
                if (!fixed.equals(current.bounds()) && drift.isEmpty())
                    drift = current.actor() + ": " + fixed + " → " + current.bounds();
                samples++;
                if (current.action() != null) tap(current.action());
                else if (!opponentCaptured) {
                    capture("05e-stable-table-romanian-opponent");
                    opponentCaptured = true;
                }
            }
            SystemClock.sleep(90);
        }
        check("Pot, messages and seats keep the same bounds through live turns",
                "identical pixel bounds across the human and other actors",
                drift.isEmpty() ? samples + " frames, " + actors.size() + " actors" : drift,
                drift.isEmpty() && samples >= 6 && actors.size() >= 3 && opponentCaptured);
        var settled = awaitSeatSettlement();
        check("A complete hand started from setup reaches showdown", "complete, five board cards, paid pot",
                settled == null ? "no session" : settled.street() + ", " + settled.board().size() + ", "
                        + HoldemTablePresentation.displayedPot(settled),
                settled != null && settled.street() == ro.atf.holdem.core.Street.COMPLETE
                        && settled.board().size() == 5 && HoldemTablePresentation.displayedPot(settled) > 0);
        capture("05f-stable-table-romanian-settled");

        tap(HoldemActivity.localizedContext(activity).getString(R.string.home));
        tap("interface-language");
        tap("English");
        tap("Play a tournament");
        tap("Deal cards");
        await("Raise");
        assertActionLanguage(activity, "English", "Raise", "Auto play");
        capture("05g-stable-table-english-preflop");
    }

    private void assertActionLanguage(Activity activity, String locale, String raise, String autoplay) throws Exception {
        var labels = new ArrayList<String>();
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            for (String tag : List.of("FOLD", "RAISE", GameTitleBar.AUTO_PLAY_TAG)) {
                TextView button = root.findViewWithTag(tag);
                if (button != null && button.isShown()) labels.add(button.getText().toString());
            }
        });
        check(locale + " table uses localized compact actions", List.of("Fold", raise, autoplay).toString(),
                labels.toString());
    }

    private record TableSample(String actor, List<Rect> bounds, String action, boolean settled) {}

    private TableSample tableSample(Activity activity) {
        var sample = new TableSample[1];
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            TextView turn = root.findViewWithTag("turn-status");
            if (turn == null) return;
            if (!turn.isShown()) {
                sample[0] = new TableSample("", List.of(), null, true);
                return;
            }
            var bounds = new ArrayList<Rect>();
            for (String tag : List.of("turn-status", "best-hand-status", "settlement-target:pot",
                    "settlement-target:seat:0", "settlement-target:seat:1",
                    "settlement-target:seat:2", "settlement-target:seat:3")) {
                var view = root.findViewWithTag(tag);
                if (view == null || view.getWidth() <= 0 || view.isLayoutRequested()) return;
                int[] origin = new int[2];
                view.getLocationOnScreen(origin);
                bounds.add(new Rect(origin[0], origin[1], origin[0] + view.getWidth(), origin[1] + view.getHeight()));
            }
            String action = null;
            for (String tag : List.of("CALL", "CHECK")) {
                TextView button = root.findViewWithTag(tag);
                if (button != null && button.isShown() && button.isEnabled()) {
                    action = button.getText().toString();
                    break;
                }
            }
            sample[0] = new TableSample(turn.getText().toString(), bounds, action, false);
        });
        return sample[0];
    }

    private void inspectEarnedAchievementProgress() throws Exception {
        tap("Achievements");
        see("Earned achievement progress reloads from the saved profile", "Unlocked 6/14");
        scrollAchievementTo("Easy");
        achievementRow("Win a poker hand", "Earned · 1/1");
        achievementRow("Reach your first showdown", "Earned · 1/1");
        achievementRow("Win 3 different hands", "Progress · 2/3");
        achievementRow("Finish your run in a tournament", "Earned · 1/1");
        capture("04a-achievements-earned");

        scrollAchievementTo("Medium");
        achievementRow("Win a tournament", "Earned · 1/1");
        achievementRow("Finish in the top three in 2 different tournaments", "Progress · 1/2");
        capture("04b-achievements-parallel");

        scrollAchievementTo("Hard");
        achievementRow("Win 3 different tournaments", "Progress · 1/3");
        achievementRow("Win 2 tournaments that started with six players", "Progress · 0/2");
        capture("04c-achievements-hard-progress");
        tap("Back");
        see("Closing achievements returns to the main menu", "Hold’em Lab");
        tap("Achievements", awaitMatch(node -> node.isClickable()
                && ("Achievements".equals(value(node.getText()))
                || "achievements".equals(value(node.getContentDescription())))));
        see("Reopening achievements preserves the unlocked count", "Unlocked 6/14");
        scrollAchievementTo("Medium");
        achievementRow("Win a tournament", "Earned · 1/1");
        achievementRow("Finish in the top three in 2 different tournaments", "Progress · 1/2");
        tap("Back");
    }

    private AccessibilityNodeInfo scrollAchievementTo(String title) throws Exception {
        var item = findAchievement(title, false);
        if (item != null) return item;
        throw new AssertionError("Achievement item is not visible: " + title);
    }

    private AccessibilityNodeInfo findAchievement(String title, boolean withProgress) {
        Predicate<AccessibilityNodeInfo> visible = node -> title.equals(value(node.getText()))
                && (!withProgress || achievementProgress(node) != null);
        for (int action : new int[] {AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}) {
            for (int page = 0; page < 6; page++) {
                var item = awaitMatch(visible, 550);
                if (item != null) return item;
                var lists = collect(node -> node.isScrollable()
                        && "android.widget.ListView".contentEquals(node.getClassName()));
                if (lists.size() != 1 || !lists.get(0).performAction(action)) break;
                waitForIdleSync();
            }
        }
        return awaitMatch(visible);
    }

    private static String achievementProgress(AccessibilityNodeInfo title) {
        var row = title.getParent();
        if (row == null) return null;
        for (int index = 0; index < row.getChildCount(); index++) {
            var node = row.getChild(index);
            if (node == null || !node.isVisibleToUser()) continue;
            String text = value(node.getText());
            if (text.startsWith("Earned · ") || text.startsWith("Progress · ")
                    || text.startsWith("Obținută · ") || text.startsWith("Progres · ")) return text;
        }
        return null;
    }

    private void achievementRow(String title, String expected) throws Exception {
        var item = findAchievement(title, true);
        check("Achievement progress: " + title, expected, item == null ? "not visible" : achievementProgress(item));
    }

    private String installedApkHash() throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = new FileInputStream(getTargetContext().getApplicationInfo().sourceDir)) {
            var bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) digest.update(bytes, 0, count);
        }
        var hash = new StringBuilder();
        for (byte value : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return hash.toString();
    }

    private void winAcrossThreeHands() throws Exception {
        Activity activity = openTournament(true);
        try {
            see("Opening hand", "Hand 1 · Preflop");
            see("The first preflop decision is available", "Call · 20");
            awaitWinChance("Opt-in shows the human win chance", true);
            capture("01b-opening-preflop");
            countDescriptions("The opening shows neither opponent card backs nor opponent faces",
                    "Face-down player card", 0);
            countDescriptions("Opponent faces are hidden before settlement", "Player cards ·", 0);
            countDescriptions("The player sees exactly their two hole cards", "Your hand ·", 2);
            checkCollapsedViewport(activity, "The opening table fits without page scrolling");
            checkOpeningControls(activity);
            inspectRaiseEditorAndCall(activity);
            see("First hand reaches the flop", "Hand 1 · Flop");
            awaitWinChance("The win chance remains visible on the flop", true);
            var flopCheck = awaitMatch(node -> node.isEnabled() && node.isClickable()
                    && "Check".equals(value(node.getText())));
            check("Flop actions have returned to the player", "Check available",
                    flopCheck == null ? "Check unavailable" : "Check available");
            capture("01-call-ledger-visible");
            checkSeatActions(activity, 4);
            seeActionForPlayer("You", "Call", 20);
            seeLatestAction("Latest seat action names the most recent player, move and running hand total",
                    "Check", 0, "contribution 20");
            tap("Hand history");
            scrollBackUntilVisible("#3 · You · Call +20");
            capture("01-betting-ledger-history");
            tap("Check");
            seeLatestAction("Check iteration identifies a player and its zero chip delta",
                    "Check", 0, "contribution 20");
            see("First hand reaches the turn", "Hand 1 · Turn");
            tap("Check");
            see("First hand reaches the river", "Hand 1 · River");
            tap("Check");
            see("First hand settles before offering another deal", "Hand 1 · Settled");
            see("First hand pot is paid", "Pot paid · 80");
            capture("01-hand-one-settled");
            checkCollapsedViewport(activity, "The settled table fits without page scrolling");
            SystemClock.sleep(1800);
            countDescriptions("All three opponents' cards remain after the payout effect", "Player cards ·", 6);
            checkTerminalCardSizes();
            seeContains("Concise best-hand label remains on the table", "Best ·");
            capture("01a-settlement-retained");
            tap("New hand");
            see("Stakes carry into hand two", "Hand 2 · Preflop");
            tap("All-in · 1960");
            see("Second hand reaches settlement", "Hand 2 · Settled");
            countDescriptions("First all-in eliminates two seats", ", Eliminated", 2);
            capture("02-two-seats-eliminated");
            tap("New hand");
            see("Third hand starts heads-up", "Hand 3 · Preflop");
            countDescriptions("Eliminated seats stay out of the third hand", ", Eliminated", 2);
            countDescriptions("Human seat holds the heads-up dealer and small blind", "You, Dealer, Small blind", 1);
            countDescriptions("Only the human hole cards are announced", "Your hand ·", 2);
            capture("03-heads-up");
            tap("All-in · 7900");
            see("Heads-up hand reaches settlement", "Hand 3 · Settled");
            countDescriptions("Last all-in eliminates the final opponent", ", Eliminated", 3);
            check("No next hand after the win", "absent", find("New hand") == null ? "absent" : "present");
            tap("Tournament result");
            see("Winner sees congratulations", "Congratulations, you won!");
            see("Winner summary reflects all three hands and 8000 chips", "Hands played · 3\nFinal stack · 8000");
            seeContains("Tournament result includes cumulative standings", "Tournament leaderboard points");
            seeContains("Human winner receives the first-place tournament points", "#1 · You · Points 100");
            capture("04-tournament-won");
            tap("Play again");
            see("Play again leads to tournament setup", "Tournament setup");
            tap("Main menu");
            tap("Tournament leaderboard");
            see("Leaderboard screen explains cumulative tournament points", "Cumulative tournament points");
            seeContains("Leaderboard retains the completed tournament result", "#1 · You · Points 100");
            seeContains("Completed tournament is counted once", "Points 100 · Tournaments 1");
            runOnMainSync(activity::finish);
            activity = startActivitySync(new Intent(getTargetContext(), HoldemActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            see("Fresh activity returns to the home menu", "Hold’em Lab");
            tap("Tournament leaderboard");
            see("Leaderboard reloads from app-private storage", "Cumulative tournament points");
            seeContains("Reopening the activity does not duplicate the tournament", "Points 100 · Tournaments 1");
            tap("Main menu");
            inspectEarnedAchievementProgress();
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void inspectRaiseEditorAndCall(Activity activity) throws Exception {
        tap("Raise");
        var ranges = collect(node -> value(node.getText()).startsWith("Add now ·")
                || value(node.getText()).startsWith("Chips to add now ·"));
        check("Raise opens the amount editor", "visible amount range",
                ranges.isEmpty() ? "range absent" : value(ranges.get(0).getText()), !ranges.isEmpty());
        closeAmountEditor();
        long deadline = SystemClock.uptimeMillis() + 4000;
        AccessibilityNodeInfo call = null;
        do {
            var candidates = collect(node -> "Call · 20".equals(value(node.getText()))
                    && node.isClickable() && node.isEnabled());
            if (activity.hasWindowFocus() && candidates.size() == 1) {
                call = candidates.get(0);
                break;
            }
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        boolean clicked = call != null && call.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        String dispatched = "one enabled Call with accessibility click dispatched";
        check("Call remains accessible after closing Raise", dispatched,
                clicked ? dispatched : callDiagnostic(activity, call));
        actions.put("Call · 20 via Android accessibility after closing Raise");
        waitForIdleSync();
    }

    private String callDiagnostic(Activity activity, AccessibilityNodeInfo candidate) {
        var root = accessibilityRoot();
        String active = root == null ? "none" : root.getClassName() + " / " + root.getPackageName();
        var visibleCall = find("Call · 20");
        var state = new String[1];
        runOnMainSync(() -> {
            var call = activity.getWindow().getDecorView().findViewWithTag("CALL");
            var bounds = new Rect();
            state[0] = call == null ? "missing" : "shown=" + call.isShown() + ", attached=" + call.isAttachedToWindow()
                    + ", window focus=" + call.hasWindowFocus() + ", visible=" + call.getGlobalVisibleRect(bounds) + " " + bounds;
        });
        return "Call " + (candidate == null ? "not matched" : "click rejected") + " · window=" + active
                + " · accessible=" + (visibleCall == null ? "missing"
                : visibleCall.getClassName() + " clickable=" + visibleCall.isClickable() + " enabled=" + visibleCall.isEnabled())
                + " · view=" + state[0];
    }

    private void awaitWinChance(String name, boolean enabled) throws Exception {
        String actual = "no estimate";
        long deadline = SystemClock.uptimeMillis() + 4000;
        do {
            var matches = collect(node -> value(node.getContentDescription())
                    .startsWith("Estimated chance to win this hand outright:")
                    || List.of("Estimating…", "Win chance —").contains(value(node.getText())));
            if (matches.size() == 1) {
                actual = value(matches.get(0).getText());
                if (!enabled || actual.startsWith("~")) break;
            } else if (matches.size() > 1) {
                actual = "duplicate estimates";
                break;
            }
            if (!enabled) break;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        check(name, enabled ? "one visible percentage with an accessible explanation" : "no estimate",
                actual, enabled ? actual.matches("~(?:[0-9]{1,2}|100)% win") : actual.equals("no estimate"));
    }

    private void closeAmountEditor() throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4000;
        do {
            var candidates = collect(node -> node.isClickable() && "Close".equalsIgnoreCase(value(node.getText())));
            if (!candidates.isEmpty() && candidates.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                check("Dismiss the amount editor", "click dispatched", "click dispatched");
                actions.put("Close amount editor");
                waitForIdleSync();
                return;
            }
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        check("Dismiss the amount editor", "click dispatched", "control unavailable");
    }

    private void checkOpeningControls(Activity activity) throws Exception {
        var sizes = new ArrayList<String>();
        var invalid = new ArrayList<String>();
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            int minimum = Math.round(48 * activity.getResources().getDisplayMetrics().density);
            for (String tag : List.of("FOLD", "CALL", "RAISE", "ALL_IN")) {
                var view = root.findViewWithTag(tag);
                if (view == null || !(view.getBackground() instanceof InsetDrawable inset)) {
                    invalid.add(tag + " missing compact background");
                    continue;
                }
                int height = view.getHeight();
                int visible = inset.getDrawable().getBounds().height();
                sizes.add(tag + " " + visible + "/" + height + " px");
                var visibleBounds = new Rect();
                if (!view.isClickable() || !view.getGlobalVisibleRect(visibleBounds)
                        || visibleBounds.width() < minimum || visibleBounds.height() < minimum
                        || Math.abs(visible - height * .8125) > 2) invalid.add(tag);
            }
            int expectedGap = Math.round(.75f * activity.getResources().getDisplayMetrics().density);
            for (var pair : List.of(List.of("FOLD", "CALL"), List.of("RAISE", "ALL_IN"))) {
                var first = root.findViewWithTag(pair.get(0));
                var second = root.findViewWithTag(pair.get(1));
                if (first == null || second == null || first.getParent() != second.getParent()) {
                    invalid.add(pair + " missing shared row");
                    continue;
                }
                int gap = Math.max(first.getLeft(), second.getLeft()) - Math.min(first.getRight(), second.getRight());
                sizes.add(pair + " gap " + gap + " px");
                if (gap != expectedGap) invalid.add(pair + " expected " + expectedGap + " px");
            }
        });
        check("Opening action buttons retain their tap bounds with shorter backgrounds",
                "Fold, Call, Raise and All-in have 48 dp touch bounds, 81.25% visual height and 0.75 dp horizontal gap",
                String.join(", ", sizes) + (invalid.isEmpty() ? "" : " · invalid " + invalid), invalid.isEmpty());
    }

    private void checkCollapsedViewport(Activity activity, String name) throws Exception {
        boolean[] fits = {false};
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            var viewport = root.findViewWithTag("holdem-game-viewport");
            var history = root.findViewWithTag("betting-history-list");
            fits[0] = viewport != null && history != null && !history.isShown()
                    && !viewport.canScrollVertically(-1) && !viewport.canScrollVertically(1);
        });
        check(name, "collapsed history and no vertical scroll range",
                fits[0] ? "collapsed history and no vertical scroll range" : "table has overflow or history is open");
    }

    private void loseOnTheOpeningAllIn() throws Exception {
        var activity = openTournament();
        try {
            awaitWinChance("Unchecked setup leaves the win chance hidden", false);
            tap("All-in · 2000");
            see("Opening all-in settles before elimination is asserted", "Hand 1 · Settled");
            countDescriptions("Human seat is eliminated after the opening all-in", "You, Stack 0, Won 0, Eliminated", 1);
            tap("Tournament result");
            see("Losing player sees the elimination result", "You were eliminated");
            see("Defeat summary reflects one hand and no chips", "Hands played · 1\nFinal stack · 0");
            assertTournamentStandingsHavePlayerNames();
            capture("05-tournament-lost");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void assertTournamentStandingsHavePlayerNames() throws Exception {
        var rows = collect(node -> value(node.getText()).startsWith("#")
                && value(node.getText()).contains(" · Points "));
        var observed = new ArrayList<String>(rows.size());
        for (var row : rows) observed.add(value(row.getText()));
        boolean named = observed.size() == 4 && observed.stream().allMatch(row -> {
            int first = row.indexOf(" · ");
            int second = first < 0 ? -1 : row.indexOf(" · ", first + 3);
            return second > first && !row.substring(first + 3, second).trim().isEmpty();
        });
        check("Defeat standings keep a visible name for every physical player",
                "four placement rows with nonblank player names", String.join("\n", observed), named);
    }

    private Activity openTournament() throws Exception {
        return openTournament(false);
    }

    private Activity openTournament(boolean winChance) throws Exception {
        var activity = launchHoldem();
        dealTournament(winChance);
        return activity;
    }

    private void dealTournament() throws Exception {
        dealTournament(false);
    }

    private void dealTournament(boolean winChance) throws Exception {
        tap("Play a tournament");
        see("Setup opens with four seats", "4");
        if (winChance) tap("Show my chance to win");
        tap("Deal cards");
    }

    private void recordBeforeOpeningResult() throws Exception {
        var activity = openTournament();
        String finishedId;
        var files = getTargetContext().getFilesDir();
        var sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(files));
        try {
            tap("All-in · 2000");
            see("Terminal table is visible before opening a result", "Hand 1 · Settled");
            var settled = sessions.load().resumed();
            check("Result screen has not opened", "terminal table",
                    settled != null && settled.game().outcome() != null && !settled.showingResult() ? "terminal table" : "unexpected checkpoint");
            finishedId = settled.game().tournamentId();
            var board = new TournamentLeaderboardRepository(new AtomicFileTournamentLeaderboardStorage(files)).load();
            check("Settled tournament is already recorded", "one completed tournament",
                    board.completedTournamentIds().size() == 1 && board.completedTournamentIds().contains(finishedId)
                            ? "one completed tournament" : "missing or duplicate tournament");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            actions.put("Back from terminal table without opening result");
            see("Back leaves the terminal table at the home menu", "Hold’em Lab");
            dealTournament();
            see("A new tournament starts at hand one", "Hand 1 · Preflop");
            var replacement = sessions.load().resumed();
            check("New tournament replaced the settled checkpoint", "different tournament ID",
                    replacement != null && !finishedId.equals(replacement.game().tournamentId()) ? "different tournament ID" : "checkpoint unchanged");
        } finally {
            runOnMainSync(activity::finish);
            waitForIdleSync();
        }
        var fresh = launchHoldem();
        try {
            tap("Tournament leaderboard");
            seeContains("Leaderboard keeps the unopened result once", "Points 100 · Tournaments 1");
            var board = new TournamentLeaderboardRepository(new AtomicFileTournamentLeaderboardStorage(files)).load();
            check("Reopened app keeps the original tournament ID once", "one original completed tournament",
                    board.completedTournamentIds().size() == 1 && board.completedTournamentIds().contains(finishedId)
                            ? "one original completed tournament" : "missing or duplicate tournament");
            capture("01-leaderboard-after-new-game");
        } finally {
            runOnMainSync(fresh::finish);
        }
    }

    private Activity launchHoldem() {
        return launchHoldem(1L);
    }

    private Activity launchHoldem(long seed) {
        var intent = new Intent(getTargetContext(), HoldemActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HoldemActivity.DEBUG_DEAL_SEED, seed);
        return startActivitySync(intent);
    }

    private void renameRankedProfile() throws Exception {
        // Seed 10 settles a four-seat tournament in one opening all-in.
        var activity = launchHoldem(10L);
        try {
            dealTournament();
            tap("All-in · 2000");
            see("Profile fixture tournament settles on the first hand", "Hand 1 · Settled");
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            actions.put("Return from the settled tournament without opening the result");
            see("Back opens the home menu", "Hold’em Lab");
            tap("Tournament leaderboard");
            see("Original profile has one completed tournament and 100 points", ORIGINAL_LEADERBOARD_ROW);
            var before = loadLeaderboard();
            String id = profileId();
            check("Tournament belongs to the stable profile", "one completed tournament and one matching profile row",
                    before.completedTournamentIds().size() == 1 && before.entries().stream().anyMatch(
                            entry -> entry.playerId().equals(id) && entry.points() == 100 && entry.tournaments() == 1)
                            ? "one completed tournament and one matching profile row" : "unexpected leaderboard");
            tap("Main menu");
            tap("Profile · You");
            var edit = collect(AccessibilityNodeInfo::isEditable);
            var input = new Bundle();
            input.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, RENAMED_PROFILE);
            check("Profile name can be edited", "name entered",
                    edit.size() == 1 && edit.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input)
                            ? "name entered" : "profile editor unavailable");
            actions.put("Enter " + RENAMED_PROFILE + " in the profile dialog");
            waitForIdleSync();
            var save = collect(node -> ("android:id/button1".equals(node.getViewIdResourceName())
                    || "android.widget.Button".equals(value(node.getClassName())))
                    && "Save".equalsIgnoreCase(value(node.getText())));
            check("Tap Save in the profile dialog", "click dispatched",
                    save.size() == 1 && save.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            ? "click dispatched" : "control unavailable");
            actions.put("Save the profile name");
            see("Home displays the saved profile", "Profile · " + RENAMED_PROFILE);
            capture("01-profile-renamed");
            tap("Tournament leaderboard");
            checkRankedProfile("Renamed profile is visible exactly once");
            check("Rename leaves the recorded tournament unchanged", "same profile ID and leaderboard",
                    id.equals(profileId()) && before.equals(loadLeaderboard()) ? "same profile ID and leaderboard" : "ledger changed");
            check("Restart fixture saved", "saved", restartFixture().edit()
                    .putString("profileId", id).putString("leaderboardDigest", leaderboardDigest(before))
                    .putInt("pid", android.os.Process.myPid()).commit() ? "saved" : "write failed");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void verifyRankedProfileAfterRestart() throws Exception {
        var fixture = restartFixture();
        int previousPid = fixture.getInt("pid", -1);
        int currentPid = android.os.Process.myPid();
        check("Android process restarted", "different positive process IDs", previousPid + " → " + currentPid,
                previousPid > 0 && currentPid > 0 && previousPid != currentPid);
        var activity = launchHoldem();
        try {
            see("Fresh process loads the renamed profile", "Profile · " + RENAMED_PROFILE);
            tap("Tournament leaderboard");
            checkRankedProfile("Restart shows the renamed profile exactly once");
            check("Restart preserves the stable profile ID", "same profile ID",
                    profileId().equals(fixture.getString("profileId", "")) ? "same profile ID" : "profile ID changed");
            var board = loadLeaderboard();
            check("Restart preserves the recorded points and tournament IDs", "same leaderboard digest",
                    leaderboardDigest(board).equals(fixture.getString("leaderboardDigest", ""))
                            ? "same leaderboard digest" : "ledger changed, completed tournaments: " + board.completedTournamentIds().size());
            capture("02-profile-after-restart");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void checkRankedProfile(String assertion) throws Exception {
        var rows = collect(node -> value(node.getText()).contains(" · " + RENAMED_PROFILE + " · Points "));
        check(assertion, RENAMED_LEADERBOARD_ROW, rows.size() == 1 ? value(rows.get(0).getText()) : "visible rows: " + rows.size());
        check("Old profile name absent from the leaderboard", "absent", find(ORIGINAL_LEADERBOARD_ROW) == null ? "absent" : "present");
    }

    private void inspectCompactSeatMetrics() throws Exception {
        var config = getTargetContext().getResources().getConfiguration();
        check("Compact viewport uses enlarged text", "width ≤ 320 dp and font scale 2.0",
                config.screenWidthDp + " dp / " + config.fontScale, config.screenWidthDp <= 320 && config.fontScale == 2f);
        var manager = getTargetContext().getSystemService(AccessibilityManager.class);
        boolean talkBack = awaitTalkBackTouchExploration(manager);
        check("Google TalkBack service is enabled", "enabled",
                talkBack ? "enabled" : "enabled service with touch exploration was not ready");
        actions.put("Inspect the Android accessibility nodes with Google TalkBack enabled");
        var profile = getTargetContext().getSharedPreferences("holdem-profile", Context.MODE_PRIVATE);
        if (!profile.edit().putBoolean("language", true).commit()) throw new AssertionError("Cannot select the Romanian test locale");
        var activity = launchHoldem();
        try {
            openRomanianSeatTournament();
            selectTableSize("6");
            tap("Împarte cărțile");
            see("Six-seat hand reaches preflop", "Mâna 1 · Preflop");
            inspectSeatFacts(activity, false);
            focusSeatMetrics(activity);
            capture("01-compact-seat-metrics");
            tap(revealTaggedControl(activity, "ALL_IN"));
            var settled = awaitSeatSettlement();
            check("Hand settles after the opening all-in", "COMPLETE", settled == null ? "checkpoint unavailable" : settled.street().name());
            inspectSeatFacts(activity, true);
            inspectSettledHandAlignment(activity, settled);
            int winner = 0;
            for (int seat = 1; seat < settled.seats().size(); seat++)
                if (settled.seats().get(seat).potWon() > settled.seats().get(winner).potWon()) winner = seat;
            int winningSeat = winner;
            runOnMainSync(() -> reveal(seatFrame(activity.getWindow().getDecorView(), winningSeat)));
            actions.put("Reveal the winning seat after settlement");
            capture("02-settled-seat-metrics");
        } finally {
            runOnMainSync(activity::finish);
        }
        inspectFiveSeatShowdown();
    }

    private static boolean awaitTalkBackTouchExploration(AccessibilityManager manager) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            if (manager.isTouchExplorationEnabled() && manager
                    .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).stream()
                    .anyMatch(service -> service.getId().toLowerCase(Locale.ROOT).contains("talkback"))) {
                return true;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private void openRomanianSeatTournament() throws Exception {
        String label = "Joacă un turneu";
        long deadline = SystemClock.uptimeMillis() + 15000;
        AccessibilityNodeInfo control;
        do {
            if (!waitedForSystemUi && waitForSystemUiDialog()) {
                waitedForSystemUi = true;
                actions.put("Wait for Android System UI to recover from its ANR dialog");
            }
            control = find(label);
            if (control != null) break;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        if (control == null) capture("01a-seat-start-failure");
        tap(label, control);
    }

    private boolean waitForSystemUiDialog() {
        var root = ui.getRootInActiveWindow();
        if (root == null || !List.of("android", "com.android.systemui").contains(value(root.getPackageName()))) {
            return false;
        }
        var titles = new ArrayList<AccessibilityNodeInfo>();
        collect(root, node -> "System UI isn't responding".equals(value(node.getText())), titles);
        if (titles.size() != 1) return false;
        var buttons = new ArrayList<AccessibilityNodeInfo>();
        collect(root, node -> node.isClickable() && "Wait".equals(value(node.getText())), buttons);
        return buttons.size() == 1 && buttons.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    private void inspectFiveSeatShowdown() throws Exception {
        var activity = launchHoldem();
        try {
            openRomanianSeatTournament();
            selectTableSize("5");
            var difficultyOptions = new ArrayList<String>();
            runOnMainSync(() -> {
                android.widget.Spinner difficulty = activity.getWindow().getDecorView().findViewWithTag("ai-level");
                if (difficulty == null) return;
                for (int index = 0; index < difficulty.getCount(); index++) {
                    difficultyOptions.add(difficulty.getItemAtPosition(index).toString());
                }
                difficultyOptions.add(String.valueOf(difficulty.getSelectedItem()));
            });
            check("Tournament setup offers Normal and Hard and defaults to Normal",
                    "[Normal, Hard, Normal]", difficultyOptions.toString());
            tap("Împarte cărțile");
            see("Five-seat hand reaches preflop", "Mâna 1 · Preflop");
            tap(revealTaggedControl(activity, "ALL_IN"));
            var settled = awaitSeatSettlement();
            check("Five-seat hand settles", "COMPLETE", settled == null ? "checkpoint unavailable" : settled.street().name());
            inspectSettledHandAlignment(activity, settled);
            capture("03-five-seat-showdown-alignment");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private HoldemTableViewState awaitSeatSettlement() {
        var sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir()));
        long deadline = SystemClock.uptimeMillis() + 8000;
        HoldemTableViewState frame = null;
        do {
            var saved = sessions.load().resumed();
            if (saved != null) {
                frame = saved.game().view();
                if (frame.street() == Street.COMPLETE) break;
            }
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        waitForIdleSync();
        return frame;
    }

    private void selectTableSize(String size) throws Exception {
        var current = await("4");
        while (current != null && !current.isClickable()) current = current.getParent();
        check("Open the table size selector", "click dispatched",
                current != null && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ? "click dispatched" : "selector unavailable");
        actions.put("Open the table size selector");
        var option = await(size);
        while (option != null && !option.isClickable()) option = option.getParent();
        check("Select " + size + " seats", "click dispatched",
                option != null && option.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        ? "click dispatched" : "option unavailable");
        actions.put("Select " + size + " seats");
        waitForIdleSync();
    }

    private void inspectSeatFacts(Activity activity, boolean settled) throws Exception {
        var files = getTargetContext().getFilesDir();
        var frame = new HoldemSessionRepository(new FileHoldemSessionStorage(files)).load().resumed().game().view();
        var invalid = new ArrayList<String>();
        int[] stacked = {0};
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            for (int seat = 0; seat < 6; seat++) {
                var box = seatFrame(root, seat);
                var player = frame.seats().get(seat);
                var labels = new ArrayList<>(List.of("Jetoane", settled ? "Câștig" : "În pot"));
                var amounts = new ArrayList<>(List.of(player.stack(), settled ? player.potWon() : player.contributed()));
                if (settled && player.returned() > 0) {
                    labels.add("Restituit");
                    amounts.add(player.returned());
                }
                if (box == null) {
                    invalid.add("seat " + seat + " missing");
                    continue;
                }
                int fact = 0;
                for (int child = 0; child < box.getChildCount(); child++) {
                    if (!(box.getChildAt(child) instanceof SeatFactRow row)) continue;
                    var label = (TextView) row.getChildAt(0);
                    var value = (TextView) row.getChildAt(1);
                    boolean vertical = row.getOrientation() == LinearLayout.VERTICAL;
                    if (vertical) stacked[0]++;
                    boolean separate = vertical ? label.getBottom() <= value.getTop()
                            : label.getRight() <= value.getLeft();
                    if (fact >= labels.size() || !labels.get(fact).contentEquals(label.getText())
                            || !Integer.toString(amounts.get(fact)).contentEquals(value.getText())
                            || !value(box.getContentDescription()).contains(", " + label.getText() + " " + value.getText())
                            || !fits(label) || !fits(value) || value.getLineCount() != 1 || !separate
                            || row.getBottom() > box.getHeight()) invalid.add("seat " + seat + " fact " + fact);
                    fact++;
                }
                if (fact != labels.size() || !box.isFocusable()
                        || (seat == frame.actor()) != value(box.getContentDescription()).contains(", La rând"))
                    invalid.add("seat " + seat + " fact count or accessibility");
            }
        });
        if (!invalid.isEmpty()) capture("01a-seat-layout-failure");
        check(settled ? "Settled seat amounts match the engine and fit" : "Live seat amounts match the engine and fit",
                "six readable seat frames", invalid.isEmpty() ? "six readable seat frames" : String.join(", ", invalid));
        if (!settled) check("Narrow seat facts use the stacked layout", "at least one stacked fact", stacked[0] + " stacked facts", stacked[0] > 0);
    }

    private static boolean fits(TextView view) {
        var layout = view.getLayout();
        if (layout == null || layout.getLineCount() == 0
                || layout.getLineEnd(layout.getLineCount() - 1) != view.getText().length()
                || layout.getHeight() + view.getTotalPaddingTop() + view.getTotalPaddingBottom() > view.getHeight()) return false;
        for (int line = 0; line < layout.getLineCount(); line++)
            if (layout.getEllipsisCount(line) != 0 || layout.getLineMax(line) > layout.getWidth() + 1f) return false;
        return true;
    }

    private void inspectSettledHandAlignment(Activity activity, HoldemTableViewState frame) throws Exception {
        var errors = new ArrayList<String>();
        runOnMainSync(() -> {
            View root = activity.getWindow().getDecorView();
            var rows = CompactSeatRows.forSeats(frame.seats().size());
            for (int[] row : List.of(rows.top(), rows.bottom())) {
                Integer rowTop = null;
                for (int displaySeat : row) {
                    if (displaySeat == CompactSeatRows.EMPTY) continue;
                    int seat = frame.physicalSeat(displaySeat);
                    View nameFrame = root.findViewWithTag("settlement-target:seat:" + seat);
                    LinearLayout hand = root.findViewWithTag("settlement-hand:seat:" + seat);
                    if (nameFrame == null || hand == null || hand.getChildCount() != 2) {
                        errors.add("seat " + seat + " missing a measured pair");
                        continue;
                    }
                    int[] nameOrigin = new int[2];
                    int[] handOrigin = new int[2];
                    nameFrame.getLocationOnScreen(nameOrigin);
                    hand.getLocationOnScreen(handOrigin);
                    boolean expectedVisible = seat == frame.viewer() ? !frame.ownCards().isEmpty()
                            : frame.seats().get(seat).dealtCards() && frame.seats().get(seat).shownCards().size() == 2;
                    if (handOrigin[1] != nameOrigin[1] + nameFrame.getHeight()
                            || (rowTop != null && handOrigin[1] != rowTop)
                            || (hand.getVisibility() == View.VISIBLE) != expectedVisible) {
                        errors.add("seat " + seat + " frame bottom " + (nameOrigin[1] + nameFrame.getHeight())
                                + ", pair top " + handOrigin[1] + ", row top " + rowTop);
                    }
                    rowTop = handOrigin[1];
                }
            }
        });
        check(frame.seats().size() + " settled hands align below their seat frames, including the viewer",
                "aligned pairs", errors.isEmpty() ? "aligned pairs" : String.join("; ", errors));
    }

    private void focusSeatMetrics(Activity activity) throws Exception {
        var frame = new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir()))
                .load().resumed().game().view();
        int actor = frame.actor();
        runOnMainSync(() -> reveal(seatFrame(activity.getWindow().getDecorView(), actor)));
        waitForIdleSync();
        var seats = collect(node -> node.isFocusable() && value(node.getContentDescription()).contains(", La rând")
                && value(node.getContentDescription()).contains(", Jetoane ") && value(node.getContentDescription()).contains(", În pot "));
        check("Active seat exposes one full accessibility description", "one node with name, chips, contribution and turn",
                seats.size() == 1 ? "one node with name, chips, contribution and turn" : "matching nodes: " + seats.size());
        var target = seats.get(0);
        String description = value(target.getContentDescription());
        var bounds = new Rect();
        target.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) throw new AssertionError("Active seat has no visible bounds");
        if (Build.VERSION.SDK_INT >= 28) {
            check("Active seat groups its facts for the screen reader", "screen-reader focusable",
                    target.isScreenReaderFocusable() ? "screen-reader focusable" : "ungrouped");
        }
        boolean dispatched = target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        boolean focused = awaitSeatFocus(description, 4000);
        if (!focused) capture("01a-seat-focus-failure");
        var actualFocus = ui.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        actions.put("Request native accessibility focus with TalkBack touch exploration enabled");
        check("Active seat accepts Android accessibility focus", description,
                focused ? description : "request=" + dispatched + ", focus="
                        + (actualFocus == null ? "none" : value(actualFocus.getContentDescription())));
        actions.put("Confirm accessibility focus on the active seat");
    }

    private boolean hasAccessibilityFocus(String description) {
        var focused = ui.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
        if (focused != null && description.equals(value(focused.getContentDescription()))) return true;
        return collect(node -> node.isAccessibilityFocused() && description.equals(value(node.getContentDescription()))).size() == 1;
    }

    private boolean awaitSeatFocus(String description, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        long since = -1;
        do {
            if (hasAccessibilityFocus(description)) {
                if (since < 0) since = SystemClock.uptimeMillis();
                if (SystemClock.uptimeMillis() - since >= 350) return true;
            } else {
                since = -1;
            }
            SystemClock.sleep(75);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private static LinearLayout seatFrame(View root, int seat) {
        var name = root.findViewWithTag("seat-name:" + seat);
        return name != null && name.getParent() instanceof LinearLayout frame ? frame : null;
    }

    private static void reveal(View control) {
        if (control != null) control.requestRectangleOnScreen(new Rect(0, 0, control.getWidth(), control.getHeight()), true);
    }

    private String revealTaggedControl(Activity activity, String tag) throws Exception {
        String[] label = {null};
        runOnMainSync(() -> {
            var control = activity.getWindow().getDecorView().findViewWithTag(tag);
            if (control == null) return;
            reveal(control);
            label[0] = control instanceof TextView text ? text.getText().toString() : "";
        });
        waitForIdleSync();
        check("Reach " + tag + " in the compact viewport", "control present", label[0] == null ? "missing" : "control present");
        return label[0];
    }

    private TournamentLeaderboard.State loadLeaderboard() throws Exception {
        return new TournamentLeaderboardRepository(new AtomicFileTournamentLeaderboardStorage(getTargetContext().getFilesDir())).load();
    }

    private static String leaderboardDigest(TournamentLeaderboard.State state) throws Exception {
        var payload = TournamentLeaderboardCodec.encode(state).getBytes(StandardCharsets.UTF_8);
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(payload));
    }

    private String profileId() {
        return getTargetContext().getSharedPreferences("holdem-profile", 0).getString("player-id", "");
    }

    private SharedPreferences restartFixture() {
        return getTargetContext().getSharedPreferences("leaderboard-profile-sanity", 0);
    }

    private void inspectSettlementAward() throws Exception {
        var sessions = new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir()));
        var game = LocalHoldemGame.tournament(4, 1);
        playFixtureAction(game, ActionType.CALL);
        for (int street = 0; street < 3; street++) playFixtureAction(game, ActionType.CHECK);
        game.nextHandAtElapsedSeconds(0);
        playFixtureAction(game, ActionType.ALL_IN);
        game.nextHandAtElapsedSeconds(0);
        sessions.save(game, false);
        actions.put("Restore seed 1 after two legal prior hands, just before the heads-up all-in");

        var activity = launchHoldem();
        var probe = new SettlementAwardObservation[1];
        try {
            tap("Continue saved game");
            see("The seeded fixture resumes at the heads-up decision", "Hand 3 · Preflop");
            runOnMainSync(() -> probe[0] = new SettlementAwardObservation((ViewGroup) activity.getWindow().getDecorView(), List.of(3)));
            tap("All-in · 7900");
            see("The all-in settles the resumed hand", "Hand 3 · Settled");
            see("The paid pot excludes the unmatched return", "Pot paid · 160");
            boolean arrived = probe[0].awaitArrival();
            // Preserve frames already observed even if a subsequent assertion fails.
            runOnMainSync(probe[0]::stop);
            var departure = probe[0].departure();
            var arrival = probe[0].arrival();
            Bitmap departureImage = probe[0].takeDepartureImage();
            Bitmap arrivalImage = probe[0].takeArrivalImage();
            if (departureImage != null) emitCapture("01-award-departure", departureImage);
            if (arrivalImage != null) emitCapture("02-award-arrival", arrivalImage);
            check("The pot marker starts at the pot center", "within 2 px of the pot center", String.valueOf(departure),
                    departure != null && departure.atPot());
            check("The pot marker reaches the winning seat frame", "within 2 px of seat 3 center", String.valueOf(probe[0].latest()),
                    arrived && arrival != null && arrival.atSeats());
            check("The sole moving marker carries only the pot award", "one +160 marker",
                    departure.markerCount() + " " + departure.labels() + " → " + arrival.markerCount() + " " + arrival.labels(),
                    departure.markerCount() == 1 && arrival.markerCount() == 1
                            && departure.labels().equals(List.of("3 +160")) && arrival.labels().equals(List.of("3 +160")));

            var saved = sessions.load().resumed().game().view();
            var winner = saved.seats().get(3);
            check("The winning seat receives a pot award and an unmatched return", "160 won + 7840 returned = 8000",
                    winner.potWon() + " won + " + winner.returned() + " returned = " + winner.stack());
            var observed = new ArrayList<String>();
            runOnMainSync(() -> {
                var box = seatFrame(activity.getWindow().getDecorView(), 3);
                if (box == null) return;
                for (int child = 0; child < box.getChildCount(); child++) {
                    if (!(box.getChildAt(child) instanceof SeatFactRow row)) continue;
                    var label = (TextView) row.getChildAt(0);
                    var amount = (TextView) row.getChildAt(1);
                    String fact = label.getText() + " " + amount.getText();
                    if (fits(label) && fits(amount) && value(box.getContentDescription()).contains(fact))
                        observed.add(fact);
                }
            });
            check("The winner shows separate readable and accessible payout rows", "Stack 8000, Won 160, Returned 7840",
                    String.join(", ", observed));
            capture("03-separate-payout");
        } finally {
            runOnMainSync(() -> {
                if (probe[0] != null) probe[0].close();
                activity.finish();
            });
        }
    }

    private static void playFixtureAction(LocalHoldemGame game, ActionType kind) {
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
        var legal = game.view().legalActions().stream().filter(action -> action.type() == kind).findFirst().orElseThrow();
        game.play(game.turn(), new LocalHoldemGame.Move(kind, legal.amount()));
        while (game.hasPassiveTurn()) game.advancePassiveAction(game.turn());
    }


    private void checkAnimationPolicy(boolean expected) throws Exception {
        float scale = Settings.Global.getFloat(getTargetContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        var enabled = new boolean[1];
        long deadline = SystemClock.uptimeMillis() + 2000;
        do {
            runOnMainSync(() -> enabled[0] = ValueAnimator.areAnimatorsEnabled());
            if (enabled[0] == expected) break;
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        check("Android animator policy matches the journey", "scale " + (expected ? 1f : 0f) + ", enabled " + expected,
                "scale " + scale + ", enabled " + enabled[0]);
    }

    private void inspectMultipleAwards(SettlementAwardFixture fixture, boolean animated) throws Exception {
        var game = fixture.beforeAllIn();
        new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir())).save(game, false);
        String name = fixture == SettlementAwardFixture.SPLIT ? "Split pot" : "Side pot";
        actions.put("Restore " + name.toLowerCase(Locale.ROOT) + " fixture before the decisive all-in");
        var activity = launchHoldem();
        var probe = new SettlementAwardObservation[1];
        var winners = fixture.awards.stream().map(HoldemTablePresentation.PotAward::physicalSeat)
                .collect(java.util.stream.Collectors.toList());
        try {
            tap("Continue saved game");
            if (fixture == SettlementAwardFixture.SPLIT || !animated) checkAnimationPolicy(animated);
            runOnMainSync(() -> probe[0] = new SettlementAwardObservation((ViewGroup) activity.getWindow().getDecorView(), winners));
            tap("All-in · " + fixture.allInChips);
            see(name + " settles after the all-in", "Hand " + game.handNumber() + " · Settled");
            see(name + " shows the total distributed pot", "Pot paid · 8000");
            if (animated) checkMultipleFlights(probe[0], fixture, name);
            else {
                capture("01-static-awards");
                runOnMainSync(probe[0]::stop);
                check("Disabled animation creates no award markers", "no markers", probe[0].sawMarker() ? "marker created" : "no markers");
            }
            checkAwardText(activity, fixture, name);
            if (animated && fixture == SettlementAwardFixture.SIDE) capture("05-side-payout");
            if (!animated) {
                tap("New hand");
                see("Disabled animation leaves continuation usable", "Hand " + (game.handNumber() + 1) + " · Preflop");
            }
        } finally {
            runOnMainSync(() -> {
                if (probe[0] != null) probe[0].close();
                activity.finish();
            });
            waitForIdleSync();
        }
    }

    private void inspectCompactAward() throws Exception {
        var fixture = SettlementAwardFixture.SIDE;
        var game = fixture.beforeAllIn();
        new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir())).save(game, false);
        actions.put("Restore side pot fixture before all-in on the short display");
        var activity = launchHoldem();
        var probe = new SettlementAwardObservation[1];
        try {
            var config = activity.getResources().getConfiguration();
            check("The compact award journey uses a short window with enlarged text", "at most 320 x 340 dp, font 2.0",
                    config.screenWidthDp + " x " + config.screenHeightDp + " dp, font " + config.fontScale,
                    config.screenWidthDp <= 320 && config.screenHeightDp <= 340 && config.fontScale == 2f);
            tap(revealTaggedControl(activity, "continue-game"));
            checkAnimationPolicy(true);
            runOnMainSync(() -> probe[0] = new SettlementAwardObservation(
                    (ViewGroup) activity.getWindow().getDecorView(), List.of(2)));
            tap(revealTaggedControl(activity, "ALL_IN"));
            see("Compact side pot settles after the all-in", "Hand 3 · Settled");

            boolean arrived = probe[0].awaitArrival();
            runOnMainSync(probe[0]::stop);
            var departure = probe[0].departure();
            var arrival = probe[0].arrival();
            Bitmap departureImage = probe[0].takeDepartureImage();
            Bitmap arrivalImage = probe[0].takeArrivalImage();
            if (departureImage != null) emitCapture("01-compact-departure", departureImage);
            if (arrivalImage != null) emitCapture("02-compact-arrival", arrivalImage);
            awaitRenderedFrame("03-compact-summary");
            var summaryImage = new Bitmap[1];
            runOnMainSync(() -> summaryImage[0] = probe[0].drawWindow());
            emitCapture("03-compact-summary", summaryImage[0]);

            checkCompactSeatVisibility(activity);
            check("The compact award starts at the pot center", "within 2 px of the pot center",
                    String.valueOf(departure), departure != null && departure.atPot());
            check("The compact award reaches only the visible recipient", "seat 2 within 2 px of its center",
                    String.valueOf(probe[0].latest()), arrived && arrival != null && arrival.atSeats());
            check("The hidden recipient never creates a marker", "[settlement-award:2]", probe[0].markerTags().toString());
            check("The visible marker carries the exact award", "[2 +7840]",
                    arrival.labels().toString());
            checkAwardText(activity, fixture, "Compact side pot");
        } finally {
            runOnMainSync(() -> {
                if (probe[0] != null) probe[0].close();
                activity.finish();
            });
        }
    }

    private void checkCompactSeatVisibility(Activity activity) throws Exception {
        var facts = new String[1];
        var correct = new boolean[1];
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            var viewport = root.<View>findViewWithTag("holdem-game-viewport");
            var visible = root.<View>findViewWithTag("settlement-target:seat:2");
            var hidden = root.<View>findViewWithTag("settlement-target:seat:3");
            var clipped = new Rect();
            boolean centerVisible = visible != null && visible.getLocalVisibleRect(clipped)
                    && clipped.contains(visible.getWidth() / 2, visible.getHeight() / 2);
            boolean fullyHidden = hidden != null && hidden.getHeight() > 0 && !hidden.getGlobalVisibleRect(new Rect());
            boolean scrollable = viewport != null && viewport.canScrollVertically(1);
            facts[0] = "seat 2 center visible=" + centerVisible + ", seat 3 fully outside=" + fullyHidden + ", scrollable=" + scrollable;
            correct[0] = centerVisible && fullyHidden && scrollable;
        });
        check("The compact viewport keeps one recipient fully outside", "seat 2 visible, seat 3 outside, scrollable", facts[0], correct[0]);
    }

    private void checkMultipleFlights(SettlementAwardObservation probe, SettlementAwardFixture fixture, String name) throws Exception {
        boolean arrived = probe.awaitArrival();
        runOnMainSync(probe::stop);
        var departure = probe.departure();
        var arrival = probe.arrival();
        Bitmap departureImage = probe.takeDepartureImage();
        Bitmap arrivalImage = probe.takeArrivalImage();
        boolean split = fixture == SettlementAwardFixture.SPLIT;
        if (departureImage != null) emitCapture(split ? "01-split-departure" : "03-side-departure", departureImage);
        if (arrivalImage != null) emitCapture(split ? "02-split-arrival" : "04-side-arrival", arrivalImage);
        check(name + " markers start together at the pot", "every marker within 2 px of the pot center",
                String.valueOf(departure), departure != null && departure.atPot());
        check(name + " markers reach their own seat frames", "every marker within 2 px of its recipient",
                String.valueOf(probe.latest()), arrived && arrival != null && arrival.atSeats());
        var expected = fixture.awards.stream().map(award -> award.physicalSeat() + " +" + award.chips())
                .collect(java.util.stream.Collectors.toList());
        check(name + " markers carry exact awards once per recipient", expected.toString(),
                departure.labels() + " → " + arrival.labels(),
                departure.markerCount() == expected.size() && arrival.markerCount() == expected.size()
                        && departure.labels().equals(expected) && arrival.labels().equals(expected));
    }

    private void checkAwardText(Activity activity, SettlementAwardFixture fixture, String name) throws Exception {
        var expected = new ArrayList<String>();
        var readableRows = new ArrayList<Integer>();
        var observed = new String[]{"missing or clipped summary"};
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            for (var award : fixture.awards) {
                var seat = seatFrame(root, award.physicalSeat());
                if (seat == null) continue;
                String description = value(seat.getContentDescription());
                expected.add(description.split(", ", 2)[0] + " +" + award.chips());
                for (int index = 0; index < seat.getChildCount(); index++) {
                    if (!(seat.getChildAt(index) instanceof SeatFactRow row)) continue;
                    var label = (TextView) row.getChildAt(0);
                    var amount = (TextView) row.getChildAt(1);
                    if ("Won".contentEquals(label.getText()) && Integer.toString(award.chips()).contentEquals(amount.getText())
                            && fits(label) && fits(amount) && description.contains(", Won " + award.chips())) {
                        readableRows.add(award.physicalSeat());
                    }
                }
            }
            TextView summary = root.findViewWithTag("settlement-award-summary");
            var visible = new Rect();
            if (summary != null && summary.getGlobalVisibleRect(visible) && visible.width() == summary.getWidth()
                    && visible.height() == summary.getHeight() && fits(summary))
                observed[0] = value(summary.getText());
        });
        check(name + " names and amounts remain in visible text", "Pot awards · " + String.join(" · ", expected), observed[0],
                expected.size() == fixture.awards.size() && observed[0].equals("Pot awards · " + String.join(" · ", expected)));
        check(name + " seat rows expose every exact award",
                fixture.awards.stream().map(award -> Integer.toString(award.physicalSeat())).collect(java.util.stream.Collectors.joining(", ")),
                readableRows.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
    }

    private void resumeNextHandDuringSettlement() throws Exception {
        var activity = openTournament();
        Activity recreated = null;
        try {
            tap("Call · 20");
            for (int street = 0; street < 3; street++) tap("Check");
            var monitor = addMonitor(HoldemActivity.class.getName(), null, false);
            try {
                tapTwiceAndInterrupt(activity, "new-hand", "Next hand tap accepted during settlement", activity::recreate);
                recreated = monitor.waitForActivityWithTimeout(5000);
                check("The settled Activity was recreated", "new Activity",
                        recreated != null && recreated != activity ? "new Activity" : "recreation missing");
            } finally {
                removeMonitor(monitor);
            }
            see("Activity recreation opens exactly hand two", "Hand 2 · Preflop");
            checkCommittedDestination("Next hand persisted once", 2, false);
            capture("01-next-hand-restored");
        } finally {
            Activity active = recreated == null ? activity : recreated;
            runOnMainSync(active::finish);
            waitForIdleSync();
        }
    }

    private void resumeResultDuringSettlement() throws Exception {
        var activity = openTournament();
        try {
            tap("All-in · 2000");
            tapTwiceAndInterrupt(activity, "tournament-result", "Result tap accepted during settlement", activity::finish);
            waitForIdleSync();
            checkCommittedDestination("Result persisted before reopening", 1, true);
        } finally {
            runOnMainSync(activity::finish);
            waitForIdleSync();
        }
        var fresh = launchHoldem();
        try {
            tap("Continue saved game");
            see("Fresh Activity opens the saved result directly", "You were eliminated");
            see("Result still shows one hand and the settled stack", "Hands played · 1\nFinal stack · 0");
            capture("02-result-restored");
            backFromResult();
            tap("Continue saved game");
            see("Reopening the result still shows elimination", "You were eliminated");
            backFromResult();
            tap("Tournament leaderboard");
            seeContains("Repeated result display records one tournament", "Points 100 · Tournaments 1");
        } finally {
            runOnMainSync(fresh::finish);
        }
    }

    private void backFromResult() throws Exception {
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        actions.put("Back from tournament result");
        see("Back from the result returns to the home menu", "Hold’em Lab");
    }

    private void tapTwiceAndInterrupt(Activity activity, String tag, String assertion, Runnable interrupt) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 5000;
        var observed = new String[]{"terminal control unavailable"};
        do {
            runOnMainSync(() -> {
                var root = activity.getWindow().getDecorView();
                var control = root.findViewWithTag(tag);
                if (control == null || !control.isShown() || control.getWidth() == 0) return;
                boolean clicked = control.performClick() && control.performClick();
                boolean stillSettled = root.findViewWithTag(tag) == control;
                observed[0] = clicked && stillSettled ? "two taps while settlement is still visible" : "transition already rendered";
                interrupt.run();
            });
            if (!observed[0].equals("terminal control unavailable")) break;
            SystemClock.sleep(16);
        } while (SystemClock.uptimeMillis() < deadline);
        actions.put("Tap " + tag + " twice, then interrupt Activity");
        check(assertion, "two taps while settlement is still visible", observed[0]);
    }

    private void checkCommittedDestination(String name, int hand, boolean result) throws Exception {
        var loaded = new HoldemSessionRepository(new FileHoldemSessionStorage(getTargetContext().getFilesDir())).load();
        var saved = loaded.resumed();
        String expected = "hand=" + hand + ", result=" + result;
        String actual = saved == null ? "checkpoint unavailable"
                : "hand=" + saved.game().handNumber() + ", result=" + saved.showingResult();
        check(name, expected, actual);
    }

    private void tap(String label) throws Exception {
        tap(label, await(label));
    }

    private void tap(String label, AccessibilityNodeInfo node) throws Exception {
        check("Tap " + label, "click dispatched", node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                ? "click dispatched" : "control unavailable");
        actions.put(label);
        waitForIdleSync();
    }

    private void see(String name, String label) throws Exception {
        check(name, label, await(label) == null ? "not visible" : label);
    }

    private void countDescriptions(String name, String fragment, int expected) throws Exception {
        var matches = collect(node -> value(node.getContentDescription()).contains(fragment));
        check(name, Integer.toString(expected), Integer.toString(matches.size()));
    }

    private void seeContains(String name, String fragment) throws Exception {
        var matches = collect(node -> value(node.getText()).contains(fragment)
                || value(node.getContentDescription()).contains(fragment));
        String actual = matches.isEmpty() ? "not visible"
                : value(matches.get(0).getContentDescription()).isBlank()
                ? value(matches.get(0).getText()) : value(matches.get(0).getContentDescription());
        check(name, "visible text containing " + fragment, actual, !matches.isEmpty());
    }

    private void checkSeatActions(Activity activity, int seatCount) throws Exception {
        var visibleBadges = new ArrayList<String>();
        runOnMainSync(() -> {
            var root = activity.getWindow().getDecorView();
            for (int seat = 0; seat < seatCount; seat++) {
                TextView badge = root.findViewWithTag("latest-seat-action:" + seat);
                if (badge != null && badge.getGlobalVisibleRect(new Rect())) {
                    String label = value(badge.getText());
                    if (label.matches("^#\\d+ · .+ \\+\\d+$")) visibleBadges.add(label);
                }
            }
        });
        int accessibleSeats = collect(node -> value(node.getContentDescription()).contains(", Action #")).size();
        check("Each player keeps a visible latest action under the seat", seatCount + " visible badges",
                visibleBadges.toString(), visibleBadges.size() == seatCount);
        check("Each player exposes its latest action in the seat description",
                Integer.toString(seatCount), Integer.toString(accessibleSeats));
    }

    private void seeActionForPlayer(String player, String action, int amount) throws Exception {
        String marker = " · " + player + " · " + action + " +" + amount + " · ";
        long deadline = SystemClock.uptimeMillis() + 2500;
        List<AccessibilityNodeInfo> matches;
        do {
            matches = collect(node -> value(node.getContentDescription()).contains(", Action #")
                    && value(node.getContentDescription()).contains(marker));
            if (matches.size() == 1) break;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        check("Seat retains " + player + " · " + action + " +" + amount,
                "one visible action badge for " + player, Integer.toString(matches.size()), matches.size() == 1);
    }

    private void seeLatestAction(String name, String action, int amount, String total) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 2500;
        List<AccessibilityNodeInfo> matches;
        do {
            matches = collect(node -> value(node.getContentDescription()).contains(", Action #")
                    || value(node.getText()).contains("Action #"));
            if (!matches.isEmpty()) break;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        if (matches.isEmpty()) {
            var candidates = collect(node -> value(node.getContentDescription()).contains("Action")
                    || value(node.getText()).contains("Action")
                    || value(node.getContentDescription()).contains("hand total")
                    || value(node.getText()).contains("hand total"));
            String observed = candidates.stream().limit(8)
                    .map(node -> value(node.getContentDescription()).isBlank()
                            ? value(node.getText()) : value(node.getContentDescription()))
                    .filter(value -> !value.isBlank()).toList().toString();
            check(name, "accessible latest action with player, move and total",
                    observed.isBlank() ? "latest action node absent; no matching visible accessibility text" : observed,
                    false);
            return;
        }
        AccessibilityNodeInfo node = null;
        int latestOrdinal = -1;
        for (var candidate : matches) {
            String description = value(candidate.getContentDescription());
            String candidateText = description.contains("Action #") ? description : value(candidate.getText());
            int hash = candidateText.indexOf('#');
            int separator = candidateText.indexOf(" · ", hash);
            if (hash < 0 || separator < 0) continue;
            try {
                int ordinal = Integer.parseInt(candidateText.substring(hash + 1, separator));
                if (ordinal > latestOrdinal) {
                    latestOrdinal = ordinal;
                    node = candidate;
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-action labels that happen to use the same prefix.
            }
        }
        if (node == null) throw new AssertionError("Could not identify the latest per-seat action ordinal");
        String description = value(node.getContentDescription());
        String actual = description.contains("Action #") ? description : value(node.getText());
        int detailStart = actual.indexOf("Action #");
        String actionMarker = " · " + action + " +" + amount;
        int playerStart = detailStart < 0 ? -1 : actual.indexOf(" · ", detailStart);
        int actionOffset = playerStart < 0 ? -1 : actual.indexOf(actionMarker, playerStart + 3);
        String player = playerStart >= 0 && actionOffset > playerStart
                ? actual.substring(playerStart + 3, actionOffset).trim() : "";
        boolean hasPlayer = detailStart >= 0 && !player.isBlank();
        boolean hasTotal = actual.contains(total);
        check(name, "accessible latest action includes player, " + action + " +" + amount + " and " + total,
                actual, hasPlayer && hasTotal);
    }

    private void scrollBackUntilVisible(String fragment) throws Exception {
        for (int attempt = 0; attempt < 8; attempt++) {
            if (!collect(node -> value(node.getText()).contains(fragment)
                    || value(node.getContentDescription()).contains(fragment)).isEmpty()) return;
            var history = collect(node -> node.isScrollable()
                    && value(node.getContentDescription()).equals("Hand history list"));
            if (history.isEmpty() || !history.get(0).performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break;
            waitForIdleSync();
        }
        seeContains("Scrollable betting ledger retains earlier player actions", fragment);
    }

    private void checkTerminalCardSizes() throws Exception {
        var cards = collect(node -> value(node.getContentDescription()).contains("Your hand ·")
                || value(node.getContentDescription()).contains("Player cards ·"));
        var sizes = new ArrayList<String>();
        for (var card : cards) {
            var bounds = new android.graphics.Rect();
            card.getBoundsInScreen(bounds);
            sizes.add(bounds.width() + "x" + bounds.height());
        }
        var distinct = new java.util.HashSet<>(sizes);
        check("Hero and exposed opponent cards use equal terminal dimensions",
                "one shared size across at least four cards", distinct.size() == 1 && sizes.size() >= 4
                        ? "one shared size across " + sizes.size() + " cards" : distinct + " across " + sizes.size() + " cards",
                distinct.size() == 1 && sizes.size() >= 4);
    }

    private AccessibilityNodeInfo await(String label) {
        return awaitMatch(node -> label.equals(value(node.getText()))
                || label.equals(value(node.getContentDescription())));
    }

    private AccessibilityNodeInfo awaitMatch(Predicate<AccessibilityNodeInfo> match) {
        return awaitMatch(match, 4000);
    }

    private AccessibilityNodeInfo awaitMatch(Predicate<AccessibilityNodeInfo> match, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            var matches = collect(match);
            if (!matches.isEmpty()) return matches.get(0);
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private AccessibilityNodeInfo find(String label) {
        var matches = collect(node -> label.equals(value(node.getText()))
                || label.equals(value(node.getContentDescription())));
        return matches.isEmpty() ? null : matches.get(0);
    }

    private List<AccessibilityNodeInfo> collect(Predicate<AccessibilityNodeInfo> matches) {
        var found = new ArrayList<AccessibilityNodeInfo>();
        collect(accessibilityRoot(), matches, found);
        return found;
    }

    private AccessibilityNodeInfo accessibilityRoot() {
        var active = ui.getRootInActiveWindow();
        boolean requireApplication = "seat-metrics".equals(arguments.getString("mode"));
        if (active != null && (!requireApplication || isApplicationRoot(active))) return active;
        // TalkBack can expose an overlay as the active root while the app still owns input focus.
        for (var window : ui.getWindows()) {
            if (window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION && window.isFocused()) {
                var root = window.getRoot();
                if (root != null && (!requireApplication || isApplicationRoot(root))) return root;
            }
        }
        return null;
    }

    private boolean isApplicationRoot(AccessibilityNodeInfo root) {
        return root != null && getTargetContext().getPackageName().contentEquals(value(root.getPackageName()));
    }

    private static void collect(AccessibilityNodeInfo node, Predicate<AccessibilityNodeInfo> matches, List<AccessibilityNodeInfo> found) {
        if (node == null) return;
        if (node.isVisibleToUser() && matches.test(node)) found.add(node);
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), matches, found);
    }

    private static String value(CharSequence text) {
        return text == null ? "" : text.toString();
    }

    private void check(String name, String expected, String actual) throws Exception {
        check(name, expected, actual, expected.equals(actual));
    }

    private void check(String name, String expected, String actual, boolean passed) throws Exception {
        assertions.put(new JSONObject().put("name", name).put("expected", expected).put("actual", actual).put("passed", passed));
        if (!passed) throw new AssertionError(name + " expected " + expected + ", observed " + actual);
    }

    private void capture(String name) throws Exception {
        awaitRenderedFrame(name);
        var image = ui.takeScreenshot();
        if (image == null) throw new AssertionError("Could not capture " + name);
        emitCapture(name, image);
    }

    private void awaitRenderedFrame(String name) throws Exception {
        // Accessibility can expose the new tree before the compositor presents the new screen.
        var nextDraw = new CountDownLatch(1);
        runOnMainSync(() -> {
            var choreographer = Choreographer.getInstance();
            choreographer.postFrameCallback(first -> choreographer.postFrameCallback(second -> nextDraw.countDown()));
        });
        if (!nextDraw.await(3, TimeUnit.SECONDS)) throw new AssertionError("No rendered frame for " + name);
        ui.waitForIdle(200, 4000);
    }

    private void emitCapture(String name, Bitmap image) throws Exception {
        byte[] png;
        try (var stream = new ByteArrayOutputStream()) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new AssertionError("Could not write " + name);
            png = stream.toByteArray();
        } finally {
            image.recycle();
        }
        String fileName = name + ".png";
        var status = new Bundle();
        status.putString("capture", fileName);
        status.putString("pngBase64", Base64.getEncoder().encodeToString(png));
        sendStatus(2, status);
        captures.put(fileName);
    }
}
