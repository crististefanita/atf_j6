package ro.atf.holdem.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
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
            } else if (journey.equals("release-smoke")) {
                releaseEntryNavigation();
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
        var activity = openTournament();
        try {
            see("Opening hand", "Hand 1 · Preflop");
            tap("Call · 20");
            see("First hand reaches the flop", "Hand 1 · Flop");
            tap("Check");
            see("First hand reaches the turn", "Hand 1 · Turn");
            tap("Check");
            see("First hand reaches the river", "Hand 1 · River");
            tap("Check");
            see("First hand settles before offering another deal", "Hand 1 · Settled");
            see("First hand pot is paid", "Pot paid · 80");
            capture("01-hand-one-settled");
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
            capture("04-tournament-won");
            tap("Play again");
            see("Play again leads to tournament setup", "Tournament setup");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private void loseOnTheOpeningAllIn() throws Exception {
        var activity = openTournament();
        try {
            tap("All-in · 2000");
            countDescriptions("Human seat is eliminated after the opening all-in", "You, Stack 0, Received 0, Eliminated", 1);
            tap("Tournament result");
            see("Losing player sees the elimination result", "You were eliminated");
            see("Defeat summary reflects one hand and no chips", "Hands played · 1\nFinal stack · 0");
            capture("05-tournament-lost");
        } finally {
            runOnMainSync(activity::finish);
        }
    }

    private Activity openTournament() throws Exception {
        var intent = new Intent(getTargetContext(), HoldemActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(HoldemActivity.DEBUG_DEAL_SEED, 1L);
        var activity = startActivitySync(intent);
        tap("Play a tournament");
        see("Setup opens with four seats", "4");
        tap("Deal cards");
        return activity;
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
        boolean passed = expected.equals(actual);
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
