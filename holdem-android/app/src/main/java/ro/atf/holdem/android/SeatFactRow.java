package ro.atf.holdem.android;

import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps both facts readable at the actual seat width and Android font scale. */
final class SeatFactRow extends LinearLayout {
    private final TextView label;
    private final TextView value;
    private final int gap;

    SeatFactRow(TextView label, TextView value, int gap) {
        super(label.getContext());
        this.label = label;
        this.value = value;
        this.gap = gap;
        setGravity(Gravity.CENTER_VERTICAL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        value.setGravity(Gravity.END);
        addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        boolean stacked = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                && HoldemTablePresentation.stackSeatFact(MeasureSpec.getSize(widthSpec),
                        textWidth(label), textWidth(value), gap);
        var labelParams = (LayoutParams) label.getLayoutParams();
        labelParams.width = stacked ? LayoutParams.MATCH_PARENT : 0;
        labelParams.weight = stacked ? 0 : 1;
        var valueParams = (LayoutParams) value.getLayoutParams();
        valueParams.width = stacked ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        valueParams.setMarginStart(stacked ? 0 : gap);
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
        super.onMeasure(widthSpec, heightSpec);
    }

    private static int textWidth(TextView text) {
        return (int) Math.ceil(android.text.Layout.getDesiredWidth(text.getText(), text.getPaint()))
                + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight();
    }
}
