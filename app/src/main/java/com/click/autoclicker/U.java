package com.click.autoclicker;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

/** 颜色、尺寸与小工具 */
public final class U {

    public static final int BG = 0xFF0A0E1A;
    public static final int CARD = 0xFF141B2E;
    public static final int CARD2 = 0xFF1E2740;
    public static final int TEXT = 0xFFECF1FF;
    public static final int SUB = 0xFF8B96B2;
    public static final int CYAN = 0xFF3ED8FF;
    public static final int VIOLET = 0xFF8B5CF6;
    public static final int GREEN = 0xFF22D3A6;
    public static final int RED = 0xFFFF5C7A;

    private U() {
    }

    public static int dpi(Context c, float dp) {
        return (int) (dp * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable solid(int color, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dpi(c, radiusDp));
        return d;
    }

    public static GradientDrawable grad(int a, int b, float radiusDp, Context c) {
        return grad(a, b, b, radiusDp, c);
    }

    public static GradientDrawable grad(int a, int b, int c2, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a, b, c2});
        d.setCornerRadius(dpi(c, radiusDp));
        return d;
    }

    public static GradientDrawable outline(int fill, int stroke, float strokeDp, float radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dpi(c, radiusDp));
        d.setStroke(dpi(c, strokeDp), stroke);
        return d;
    }

    public static GradientDrawable circle(int a, int b, Context c) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        d.setShape(GradientDrawable.OVAL);
        return d;
    }

    public static TextView tv(Context c, String text, float sizeSp, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        return t;
    }
}
