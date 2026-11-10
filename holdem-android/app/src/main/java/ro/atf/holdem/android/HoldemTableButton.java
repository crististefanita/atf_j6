package ro.atf.holdem.android;

import android.content.res.ColorStateList;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import ro.cardtable.ui.screens.ScreenViews;

final class HoldemTableButton {
    static final int PRIMARY_FILL = 0xff30694e;
    static final int SECONDARY_FILL = 0xff205443;
    static final int PRIMARY_TEXT = 0xffffffff;
    static final int SECONDARY_TEXT = 0xffddf4e9;
    static final int PRESS_OVERLAY = 0x14ffffff;

    private HoldemTableButton() {}

    static void apply(ScreenViews views, Button button, boolean primary) {
        apply(views, button, primary, HoldemTablePresentation.ACTION_FACE_INSET);
    }

    static void applyTableAction(ScreenViews views, Button button, boolean primary) {
        apply(views, button, primary, HoldemTablePresentation.TABLE_ACTION_FACE_INSET);
    }

    private static void apply(ScreenViews views, Button button, boolean primary, float inset) {
        int left = button.getPaddingLeft();
        int top = button.getPaddingTop();
        int right = button.getPaddingRight();
        int bottom = button.getPaddingBottom();
        var face = views.roundedBackground(primary ? PRIMARY_FILL : SECONDARY_FILL,
                primary ? 0xff77af8d : 0xff609180, 12);
        var pressed = new RippleDrawable(ColorStateList.valueOf(PRESS_OVERLAY), face, null);
        button.setBackgroundTintList(null);
        button.setBackground(new InsetDrawable(pressed, 0f, inset, 0f, inset));
        button.setTextColor(primary ? PRIMARY_TEXT : SECONDARY_TEXT);
        button.setStateListAnimator(null);
        button.setPadding(left, top, right, bottom);
        button.setMinimumHeight(views.dp(HoldemTablePresentation.ACTION_TARGET_DP));
    }
}
