package com.click.autoclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Paint.FontMetrics;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹显示 / 编辑层。
 * 预览模式只负责画；编辑模式支持拖动点位、空白处新增、长按删除。
 */
public class TrajectoryView extends View {

    public interface OnChange {
        void onBeforeChange(List<Task.Pt> snapshot);

        void onChanged();
    }

    private final List<Task.Pt> points = new ArrayList<Task.Pt>();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private final float d;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean editable;
    private int highlight = -1;
    private int selected = -1;
    private int dragging = -1;
    private boolean moved;
    private boolean downHit;
    private float downX, downY;

    private OnChange changeListener;
    private Runnable longPressRunnable;

    public TrajectoryView(Context c) {
        super(c);
        d = U.density(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.2f));
        line.setColor(0xFF4FD9FF);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(9));
        glow.setColor(0x334FD9FF);
        dotFill.setStyle(Paint.Style.FILL);
        dotStroke.setStyle(Paint.Style.STROKE);
        dotStroke.setStrokeWidth(dp(1.6f));
        dotStroke.setColor(0xFF4FD9FF);
        numPaint.setColor(0xFFECF1FF);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTextSize(dp(11));
        numPaint.setFakeBoldText(true);
        hintPaint.setColor(0xFF9FB0D0);
        hintPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setTextSize(dp(11.5f));
        hiFill.setStyle(Paint.Style.FILL);
        hiFill.setColor(0xFFFFFFFF);
    }

    private float dp(float v) {
        return v * d;
    }

    public void setPoints(List<Task.Pt> pts) {
        points.clear();
        if (pts != null) {
            for (Task.Pt p : pts) {
                points.add(p.copy());
            }
        }
        selected = -1;
        invalidate();
    }

    public List<Task.Pt> points() {
        return points;
    }

    public List<Task.Pt> snapshot() {
        List<Task.Pt> l = new ArrayList<Task.Pt>();
        for (Task.Pt p : points) {
            l.add(p.copy());
        }
        return l;
    }

    public void setEditable(boolean e) {
        editable = e;
        invalidate();
    }

    public boolean isEditable() {
        return editable;
    }

    public void setHighlight(int index) {
        if (highlight != index) {
            highlight = index;
            invalidate();
        }
    }

    public void setOnChange(OnChange l) {
        changeListener = l;
    }

    private void notifyBefore() {
        if (changeListener != null) {
            changeListener.onBeforeChange(snapshot());
        }
    }

    private void notifyChanged() {
        if (changeListener != null) {
            changeListener.onChanged();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (editable) {
            canvas.drawColor(0x7A070B14);
            FontMetrics fm = hintPaint.getFontMetrics();
            float y = dp(48) - (fm.ascent + fm.descent) / 2f;
            canvas.drawText("拖动圆点改位置 · 点空白处新增 · 长按圆点删除", getWidth() / 2f, y, hintPaint);
        }

        if (points.isEmpty()) {
            return;
        }

        path.reset();
        path.moveTo(points.get(0).x, points.get(0).y);
        for (int i = 1; i < points.size(); i++) {
            path.lineTo(points.get(i).x, points.get(i).y);
        }
        canvas.drawPath(path, glow);
        canvas.drawPath(path, line);

        for (int i = 0; i < points.size(); i++) {
            Task.Pt p = points.get(i);
            boolean isHi = i == highlight;
            float r = dp(isHi ? 17 : 13);

            if (isHi) {
                hiFill.setAlpha(70);
                canvas.drawCircle(p.x, p.y, r + dp(6), hiFill);
                dotFill.setColor(0xFFFFFFFF);
            } else {
                dotFill.setColor(0xFF12203A);
            }
            if (i == selected) {
                dotFill.setColor(0xFF8B5CF6);
            }
            canvas.drawCircle(p.x, p.y, r, dotFill);
            canvas.drawCircle(p.x, p.y, r, dotStroke);

            FontMetrics fm = numPaint.getFontMetrics();
            float ty = p.y - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(String.valueOf(i + 1), p.x, ty, numPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!editable) {
            return false;
        }
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                moveDownX = x;
                moveDownY = y;
                moved = false;
                selected = hitTest(x, y);
                downHit = selected >= 0;
                if (downHit) {
                    dragging = selected;
                    scheduleLongPress(selected);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (dragging >= 0) {
                    if (!moved && (Math.abs(x - moveDownX) > dp(9) || Math.abs(y - moveDownY) > dp(9))) {
                        moved = true;
                        cancelMyLongPress();
                        notifyBefore();
                    }
                    if (moved) {
                        Task.Pt p = points.get(dragging);
                        p.x = Math.round(x);
                        p.y = Math.round(y);
                        invalidate();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                cancelMyLongPress();
                if (dragging >= 0) {
                    if (moved) {
                        notifyChanged();
                    }
                    dragging = -1;
                    invalidate();
                } else if (!downHit) {
                    notifyBefore();
                    points.add(new Task.Pt(Math.round(x), Math.round(y)));
                    selected = points.size() - 1;
                    notifyChanged();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelMyLongPress();
                dragging = -1;
                moved = false;
                invalidate();
                return true;

            default:
                return true;
        }
    }

    private float moveDownX, moveDownY;

    private int hitTest(float x, float y) {
        int best = -1;
        float bestDist = dp(30);
        for (int i = 0; i < points.size(); i++) {
            Task.Pt p = points.get(i);
            float dx = p.x - x;
            float dy = p.y - y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    private void scheduleLongPress(final int idx) {
        cancelMyLongPress();
        longPressRunnable = new Runnable() {
            @Override
            public void run() {
                if (idx < points.size() && !moved) {
                    notifyBefore();
                    points.remove(idx);
                    selected = -1;
                    dragging = -1;
                    notifyChanged();
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    invalidate();
                }
            }
        };
        handler.postDelayed(longPressRunnable, 620);
    }

    private void cancelMyLongPress() {
        if (longPressRunnable != null) {
            handler.removeCallbacks(longPressRunnable);
            longPressRunnable = null;
        }
    }
}
