package ro.atf.holdem.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Samples the laid-out Android views immediately before a frame is drawn. */
final class SettlementAwardObservation implements ViewTreeObserver.OnPreDrawListener, AutoCloseable {
    record Marker(int seat, double fromPotPx, double fromSeatPx, String text) { }
    record Frame(List<Marker> markers, int markerCount) {
        boolean atPot() { return markers.stream().allMatch(marker -> marker.fromPotPx() <= 2); }
        boolean atSeats() { return markers.stream().allMatch(marker -> marker.fromSeatPx() <= 2); }
        List<String> labels() { return markers.stream().map(marker -> marker.seat() + " " + marker.text())
                .collect(java.util.stream.Collectors.toList()); }
    }
    private final ViewGroup root;
    private final List<Integer> winners;
    private final CountDownLatch arrived = new CountDownLatch(1);
    private Frame departure;
    private Frame arrival;
    private Frame latest;
    private final LinkedHashSet<String> markerTags = new LinkedHashSet<>();
    private Bitmap departureImage;
    private Bitmap arrivalImage;

    SettlementAwardObservation(ViewGroup root, List<Integer> winners) {
        this.root = root;
        this.winners = List.copyOf(winners);
        root.getViewTreeObserver().addOnPreDrawListener(this);
    }

    @Override public boolean onPreDraw() {
        int count = 0;
        for (int child = 0; child < root.getChildCount(); child++) {
            Object tag = root.getChildAt(child).getTag();
            if (tag instanceof String name && name.startsWith("settlement-award:")) {
                markerTags.add(name);
                count++;
            }
        }
        if (count == 0 || arrival != null) return true;
        var pot = visibleCenter(root.findViewWithTag("settlement-target:pot"));
        if (pot == null) return true;
        var markers = new ArrayList<Marker>(winners.size());
        for (int winner : winners) {
            var marker = root.<TextView>findViewWithTag("settlement-award:" + winner);
            var seat = visibleCenter(root.findViewWithTag("settlement-target:seat:" + winner));
            var center = visibleCenter(marker);
            if (seat == null || center == null) return true;
            markers.add(new Marker(winner, distance(center, pot), distance(center, seat), marker.getText().toString()));
        }
        latest = new Frame(List.copyOf(markers), count);
        if (departure == null) {
            departure = latest;
            departureImage = drawWindow();
        } else if (latest.atSeats() && !departure.atSeats()) {
            arrival = latest;
            arrivalImage = drawWindow();
            arrived.countDown();
        }
        return true;
    }

    boolean awaitArrival() throws InterruptedException { return arrived.await(4, TimeUnit.SECONDS); }
    Frame departure() { return departure; }
    Frame arrival() { return arrival; }
    Frame latest() { return latest; }
    boolean sawMarker() { return !markerTags.isEmpty(); }
    List<String> markerTags() { return List.copyOf(markerTags); }

    Bitmap takeDepartureImage() {
        Bitmap image = departureImage;
        departureImage = null;
        return image;
    }

    Bitmap takeArrivalImage() {
        Bitmap image = arrivalImage;
        arrivalImage = null;
        return image;
    }

    void stop() { root.getViewTreeObserver().removeOnPreDrawListener(this); }

    @Override public void close() {
        stop();
        if (departureImage != null) departureImage.recycle();
        if (arrivalImage != null) arrivalImage.recycle();
    }

    Bitmap drawWindow() {
        var image = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        return image;
    }

    private static PointF visibleCenter(View view) {
        if (view == null || !view.isShown() || !view.getGlobalVisibleRect(new Rect())) return null;
        // Clipping changes the visible rectangle, not the center targeted by the animation.
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new PointF(location[0] + view.getWidth() * view.getScaleX() / 2f,
                location[1] + view.getHeight() * view.getScaleY() / 2f);
    }

    private static double distance(PointF a, PointF b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
}
