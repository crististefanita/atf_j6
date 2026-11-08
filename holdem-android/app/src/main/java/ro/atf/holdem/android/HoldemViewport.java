package ro.atf.holdem.android;

import android.view.View;
import android.widget.ScrollView;
import ro.cardtable.ui.cards.CardFaceMetrics;
import ro.cardtable.ui.screens.ScreenViews;

import java.util.ArrayList;
import java.util.List;

/** Fits the measured table by changing card sizes. The scroll fallback preserves reachability. */
final class HoldemViewport extends ScrollView {
    private record CardSize(View view, int preferredDp) { }

    private final ScreenViews views;
    private final List<CardSize> cards = new ArrayList<>();

    HoldemViewport(ScreenViews views) {
        super(views.activity());
        this.views = views;
        setFillViewport(true);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setTag("holdem-game-viewport");
    }

    void fitCard(View card, int preferredDp) {
        cards.add(new CardSize(card, preferredDp));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        View content = getChildAt(0);
        if (content != null && !cards.isEmpty() && MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY
                && MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            int width = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int available = MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
            if (width > 0 && available > 0) fit(content, width, available);
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    private void fit(View content, int width, int available) {
        int full = HoldemTablePresentation.FULL_CARD_SCALE;
        int minimum = HoldemTablePresentation.MIN_CARD_SCALE;
        resizeCards(full);
        int preferredHeight = naturalHeight(content, width);
        if (preferredHeight <= available) return;

        resizeCards(minimum);
        int smallestHeight = naturalHeight(content, width);
        int scale = HoldemTablePresentation.fitCardScale(available, preferredHeight, smallestHeight);
        if (scale == minimum) return;

        resizeCards(scale);
        // A different card width or pixel rounding can change the measured result.
        if (naturalHeight(content, width) > available) resizeCards(minimum);
    }

    private static int naturalHeight(View content, int width) {
        content.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return content.getMeasuredHeight();
    }

    private void resizeCards(int scale) {
        for (CardSize card : cards) {
            int height = Math.round(card.preferredDp() * scale / 100f);
            var metrics = CardFaceMetrics.atHeight(height);
            var view = card.view();
            var box = view.getLayoutParams();
            int widthPx = views.dp(metrics.widthDp());
            int heightPx = views.dp(metrics.heightDp());
            if (box.width == widthPx && box.height == heightPx) continue;
            box.width = widthPx;
            box.height = heightPx;
            if (view.getTranslationY() < 0)
                view.setTranslationY(-views.dp(Math.max(4, Math.round(height * .08f))));
            // Invalidate the measuring path within this pass, without requesting another layout.
            for (View changed = view; changed != this; changed = (View) changed.getParent())
                changed.forceLayout();
        }
    }
}
