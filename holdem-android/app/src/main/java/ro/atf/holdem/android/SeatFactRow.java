package ro.atf.holdem.android;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps both facts readable at the actual seat width and Android font scale. */
final class SeatFactRow extends LinearLayout {
    private final int gap;
    private int reservedDigitsWidth;

    SeatFactRow(Context context) {
        super(context);
        gap = Math.round(4 * context.getResources().getDisplayMetrics().density);
        setGravity(Gravity.CENTER_VERTICAL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    static SeatFactRow of(TextView label, TextView value, int reservedDigits) {
        float widestDigit = 0;
        for (char digit = '0'; digit <= '9'; digit++)
            widestDigit = Math.max(widestDigit, value.getPaint().measureText(String.valueOf(digit)));
        var row = new SeatFactRow(label.getContext());
        row.reservedDigitsWidth = (int) Math.ceil(reservedDigits * widestDigit);
        value.setGravity(Gravity.END);
        row.addView(label, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        row.addView(value, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        if (getChildCount() == 2) arrangeFacts((TextView) getChildAt(0), (TextView) getChildAt(1), widthSpec);
        super.onMeasure(widthSpec, heightSpec);
    }

    private void arrangeFacts(TextView label, TextView value, int widthSpec) {
        boolean stacked = MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED
                && HoldemTablePresentation.stackSeatFact(MeasureSpec.getSize(widthSpec),
                        textWidth(label), Math.max(reservedDigitsWidth, textWidth(value)), gap);
        var labelParams = (LayoutParams) label.getLayoutParams();
        labelParams.width = stacked ? LayoutParams.MATCH_PARENT : 0;
        labelParams.weight = stacked ? 0 : 1;
        var valueParams = (LayoutParams) value.getLayoutParams();
        valueParams.width = stacked ? LayoutParams.MATCH_PARENT : LayoutParams.WRAP_CONTENT;
        valueParams.setMarginStart(stacked ? 0 : gap);
        setOrientation(stacked ? VERTICAL : HORIZONTAL);
    }

    private static int textWidth(TextView text) {
        return (int) Math.ceil(android.text.Layout.getDesiredWidth(text.getText(), text.getPaint()))
                + text.getCompoundPaddingLeft() + text.getCompoundPaddingRight();
    }
}
