package ro.atf.holdem.android;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import ro.atf.holdem.core.HoldemTableViewState;
import ro.cardtable.ui.screens.ScreenViews;

import java.util.ArrayList;

/** Owns the settlement overlay and the lifetime of its Android animator. */
final class SettlementAwardAnimator {
    static final String POT_TAG = "settlement-target:pot";
    static final String SEAT_TAG = "settlement-target:seat:";

    record Animation(boolean started, Runnable cancel) { }
    private record AwardMotion(View marker, float startX, float startY, float endX, float endY) { }

    private final ScreenViews views;
    private final HoldemTableView.Text text;
    private ValueAnimator activeAnimator;

    SettlementAwardAnimator(ScreenViews views, HoldemTableView.Text text) {
        this.views = views;
        this.text = text;
    }

    Animation start(ViewGroup contentRoot, HoldemTableViewState frame, long remainingMillis, Runnable completed) {
        if (contentRoot == null || frame == null || remainingMillis <= 0
                || !ValueAnimator.areAnimatorsEnabled()) return new Animation(false, () -> { });
        View target = contentRoot.findViewWithTag(POT_TAG);
        if (!hasVisibleCenter(target)) return new Animation(false, () -> { });
        var awards = HoldemTablePresentation.potAwards(frame);
        if (awards.isEmpty()) return new Animation(false, () -> { });
        View decor = views.activity().getWindow().getDecorView();
        if (!(decor instanceof ViewGroup overlay)) return new Animation(false, () -> { });
        int[] origin = new int[2];
        target.getLocationOnScreen(origin);
        int[] overlayOrigin = new int[2];
        overlay.getLocationOnScreen(overlayOrigin);
        float startX = origin[0] - overlayOrigin[0] + target.getWidth() / 2f;
        float startY = origin[1] - overlayOrigin[1] + target.getHeight() / 2f;
        var motions = new ArrayList<AwardMotion>(awards.size());
        for (var award : awards) {
            View destination = contentRoot.findViewWithTag(SEAT_TAG + award.physicalSeat());
            if (!hasVisibleCenter(destination)) continue;
            var marker = views.text(text.get(R.string.chip_award, award.chips()),
                    15, Color.WHITE, true);
            marker.setGravity(Gravity.CENTER);
            marker.setTag("settlement-award:" + award.physicalSeat());
            marker.setVisibility(View.INVISIBLE);
            marker.setPadding(views.dp(8), views.dp(4), views.dp(8), views.dp(4));
            marker.setBackground(views.roundedBackground(0xff99702b, 0xffefcc72, 18));
            marker.setElevation(views.dp(8));
            marker.setContentDescription(text.get(R.string.chip_award_description,
                    award.chips()));
            overlay.addView(marker, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int[] destinationLocation = new int[2];
            destination.getLocationOnScreen(destinationLocation);
            motions.add(new AwardMotion(marker, startX, startY,
                    destinationLocation[0] - overlayOrigin[0] + destination.getWidth() / 2f,
                    destinationLocation[1] - overlayOrigin[1] + destination.getHeight() / 2f));
        }
        if (motions.isEmpty()) return new Animation(false, () -> { });
        var animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(Math.max(1, Math.min(650, remainingMillis)));
        animator.setInterpolator(input -> input);
        animator.addUpdateListener(value -> {
            float progress = (float) value.getAnimatedValue();
            for (var motion : motions) {
                var marker = motion.marker();
                marker.setX(motion.startX() + (motion.endX() - motion.startX()) * progress - marker.getWidth() / 2f);
                marker.setY(motion.startY() + (motion.endY() - motion.startY()) * progress - marker.getHeight() / 2f);
                marker.setScaleX(1f - .35f * progress);
                marker.setScaleY(1f - .35f * progress);
                marker.setAlpha(1f - .15f * progress);
            }
        });
        Runnable clear = () -> motions.forEach(motion -> overlay.removeView(motion.marker()));
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                // Keep the terminal position through a draw before clearing the overlay.
                overlay.postOnAnimation(() -> {
                    clear.run();
                    if (activeAnimator != animator) return;
                    activeAnimator = null;
                    completed.run();
                });
            }
        });
        activeAnimator = animator;
        var firstDraw = new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                overlay.getViewTreeObserver().removeOnPreDrawListener(this);
                if (activeAnimator != animator) return true;
                for (var motion : motions) {
                    var marker = motion.marker();
                    marker.setX(motion.startX() - marker.getWidth() / 2f);
                    marker.setY(motion.startY() - marker.getHeight() / 2f);
                    marker.setVisibility(View.VISIBLE);
                }
                overlay.postOnAnimation(() -> { if (activeAnimator == animator) animator.start(); });
                return true;
            }
        };
        overlay.getViewTreeObserver().addOnPreDrawListener(firstDraw);
        return new Animation(true, () -> {
            if (activeAnimator != animator) return;
            activeAnimator = null;
            overlay.getViewTreeObserver().removeOnPreDrawListener(firstDraw);
            animator.cancel();
            clear.run();
        });
    }

    private static boolean hasVisibleCenter(View view) {
        if (view == null || !view.isShown()) return false;
        var visible = new Rect();
        return view.getLocalVisibleRect(visible) && HoldemTablePresentation.containsVisibleCenter(
                view.getWidth(), view.getHeight(), visible.left, visible.top, visible.right, visible.bottom);
    }
}
