package com.click.autoclicker;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** 执行中的小圆点，点一下 = 暂停 */
public class DotView extends View {

    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar1 = new RectF();
    private final RectF bar2 = new RectF();

    private final float d;
    private float phase;
    private ValueAnimator anim;
    private Shader shader;

    public DotView(Context c) {
        super(c);
        d = U.density(c);
        halo.setStyle(Paint.Style.STROKE);
        halo.setColor(Color.argb(120, 62, 216, 255));
        halo.setStrokeWidth(dp(2));
        glyph.setColor(0xFFFFFFFF);
        glyph.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    private float dp(float v) {
        return v * d;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        shader = new LinearGradient(0, 0, w, h, U.CYAN, U.VIOLET, Shader.TileMode.CLAMP);
        core.setShader(shader);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(1800);
        anim.setRepeatCount(ValueAnimator.INFINITE);
        anim.setInterpolator(new LinearInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                phase = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = dp(18);

        // 呼吸光晕
        halo.setAlpha((int) ((1f - phase) * 140));
        canvas.drawCircle(cx, cy, r + dp(6) + dp(8) * phase, halo);

        // 渐变实心圆点
        canvas.drawCircle(cx, cy, r, core);

        // 暂停符号
        float bw = dp(4.5f);
        float bh = dp(15);
        float gap = dp(4);
        bar1.set(cx - gap / 2 - bw, cy - bh / 2, cx - gap / 2, cy + bh / 2);
        bar2.set(cx + gap / 2, cy - bh / 2, cx + gap / 2 + bw, cy + bh / 2);
        float rr = dp(2);
        canvas.drawRoundRect(bar1, rr, rr, glyph);
        canvas.drawRoundRect(bar2, rr, rr, glyph);
    }
}
