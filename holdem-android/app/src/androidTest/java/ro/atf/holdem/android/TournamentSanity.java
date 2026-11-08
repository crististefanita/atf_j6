package ro.atf.holdem.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

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

/** A small device suite that drives the same accessible controls as the player. */
public final class TournamentSanity extends Instrumentation {
    private static final String RENAMED_PROFILE = "Alex Poker";
    private static final String ORIGINAL_LEADERBOARD_ROW = "#1 · You · Points 100 · Tournaments 1 · Best finish 1";
    private static final String RENAMED_LEADERBOARD_ROW = "#1 · " + RENAMED_PROFILE + " · Points 100 · Tournaments 1 · Best finish 1";
    private final JSONArray actions = new JSONArray();
    private final JSONArray assertions = new JSONArray();
    private final JSONArray captures = new JSONArray();
    private UiAutomation ui;
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
            ui = getUiAutomation();
            if (journey.equals("tournament")) {
                winAcrossThreeHands();
                loseOnTheOpeningAllIn();
                inspectRaiseEditor();
            } else if (journey.equals("release-smoke")) {
                releaseEntryNavigation();
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
        } finally {
            runOnMainSync(activity::finish);
        }
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
        Activity activity = openTournament();
        try {
            see("Opening hand", "Hand 1 · Preflop");
            see("The first preflop decision is available", "Call · 20");
            capture("01b-opening-preflop");
            countDescriptions("The opening shows neither opponent card backs nor opponent faces",
                    "Face-down player card", 0);
            countDescriptions("Opponent faces are hidden before settlement", "Player cards ·", 0);
            countDescriptions("The player sees exactly their two hole cards", "Your hand ·", 2);
            checkCollapsedViewport(activity, "The opening table fits without page scrolling");
            checkOpeningControls(activity);
            tap("Call · 20");
            see("First hand reaches the flop", "Hand 1 · Flop");
            capture("01-call-ledger-visible");
            checkSeatActionCount("Each player keeps a visible latest action under the seat", 4);
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
            see("Second hand all-in excludes the big blind already posted", "All-in · 1960");
            tap("All-in · 1960");
            see("Second hand pot is paid", "Pot paid · 7920");
            countDescriptions("First all-in eliminates two seats", ", Eliminated", 2);
            capture("02-two-seats-eliminated");
            tap("New hand");
            see("Third hand starts heads-up", "Hand 3 · Preflop");
            countDescriptions("Eliminated seats stay out of the third hand", ", Eliminated", 2);
            countDescriptions("Human seat holds the heads-up dealer and small blind", "You, Dealer, Small blind", 1);
            countDescriptions("Only the human hole cards are announced", "Your hand ·", 2);
            capture("03-heads-up");
            tap("All-in · 7900");
            see("Last all-in pays all tournament chips", "Pot paid · 8000");
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
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void inspectRaiseEditor() throws Exception {
        var activity = openTournament();
        try {
            tap("Raise");
            var ranges = collect(node -> value(node.getText()).startsWith("Add now ·")
                    || value(node.getText()).startsWith("Chips to add now ·"));
            check("Raise opens the amount editor", "visible amount range",
                    ranges.isEmpty() ? "range absent" : value(ranges.get(0).getText()), !ranges.isEmpty());
            closeAmountEditor();
        } finally {
            runOnMainSync(activity::finish);
        }
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
                        || Math.abs(visible - height * .75) > 2) invalid.add(tag);
            }
        });
        check("Opening action buttons retain their tap bounds with shorter backgrounds",
                "Fold, Call, Raise and All-in have at least 48 dp touch bounds and 75% visual height",
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
        var activity = launchHoldem();
        dealTournament();
        return activity;
    }

    private void dealTournament() throws Exception {
        tap("Play a tournament");
        see("Setup opens with four seats", "4");
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
        var node = await(label);
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

    private void checkSeatActionCount(String name, int expected) throws Exception {
        int visibleBadges = collect(node -> value(node.getText()).matches("^#\\d+ · .+ \\+\\d+$")).size();
        int accessibleSeats = collect(node -> value(node.getContentDescription()).contains(", Action #")).size();
        check(name, Integer.toString(expected), Integer.toString(visibleBadges));
        check("Each player exposes its latest action in the seat description",
                Integer.toString(expected), Integer.toString(accessibleSeats));
    }

    private void seeActionForPlayer(String player, String action, int amount) throws Exception {
        String marker = " · " + player + " · " + action + " +" + amount + " · ";
        var matches = collect(node -> value(node.getContentDescription()).contains(", Action #")
                && value(node.getContentDescription()).contains(marker));
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
        long deadline = SystemClock.uptimeMillis() + 4000;
        do {
            var node = find(label);
            if (node != null) return node;
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
        collect(ui.getRootInActiveWindow(), matches, found);
        return found;
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
        // Accessibility can expose the new tree before the compositor presents the new screen.
        var nextDraw = new CountDownLatch(1);
        runOnMainSync(() -> {
            var choreographer = Choreographer.getInstance();
            choreographer.postFrameCallback(first -> choreographer.postFrameCallback(second -> nextDraw.countDown()));
        });
        if (!nextDraw.await(3, TimeUnit.SECONDS)) throw new AssertionError("No rendered frame for " + name);
        ui.waitForIdle(200, 4000);
        var image = ui.takeScreenshot();
        if (image == null) throw new AssertionError("Could not capture " + name);
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
