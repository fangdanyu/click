package com.click.autoclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 录制层：全屏捕获你点下的位置与节奏。
 * 同一点位快速点两下会被识别为「双击」，并自动记录双击间隔。
 */
public class RecordView extends View {

    public interface Listener {
        void onChange(int count, int intervalMs, int doubleGapMs);
    }

    private final List<Task.Pt> points = new ArrayList<Task.Pt>();
    private final List<Integer> intervals = new ArrayList<Integer>();
    private int doubleGapHint = -1;
    private long lastTime;
    private boolean hasLast;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    private Listener listener;

    public RecordView(Context c) {
        super(c);
        d = U.density(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(dp(2.2f));
        line.setColor(0xFF4FD9FF);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(dp(9));
        glow.setColor(0x334FD9FF);
        dotFill.setStyle(Paint.Style.FILL);
        dotFill.setColor(0xFF12203A);
        dotStroke.setStyle(Paint.Style.STROKE);
        dotStroke.setStrokeWidth(dp(1.6f));
        dotStroke.setColor(0xFF4FD9FF);
        numPaint.setColor(0xFFECF1FF);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTextSize(dp(11));
        numPaint.setFakeBoldText(true);
        titlePaint.setColor(0xFFB8C6E8);
        titlePaint.setTextAlign(Paint.Align.CENTER);
        titlePaint.setTextSize(dp(12));
        recDot.setStyle(Paint.Style.FILL);
        recDot.setColor(0xFFFF5C7A);
    }

    private float dp(float v) {
        return v * d;
    }

    public void setListener(Listener l) {
        listener = l;
        notifyChange();
    }

    public List<Task.Pt> points() {
        return points;
    }

    public int medianInterval() {
        if (intervals.isEmpty()) {
            return 600;
        }
        Integer[] arr = intervals.toArray(new Integer[0]);
        Arrays.sort(arr);
        int v = arr[arr.length / 2];
        return Math.max(80, Math.min(5000, v));
    }

    public int doubleGap() {
        if (doubleGapHint > 0) {
            return Math.max(20, Math.min(600, doubleGapHint));
        }
        return 90;
    }

    public void undo() {
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            if (!intervals.isEmpty()) {
                intervals.remove(intervals.size() - 1);
            }
            hasLast = false;
            notifyChange();
            invalidate();
        }
    }

    public void clear() {
        points.clear();
        intervals.clear();
        hasLast = false;
        notifyChange();
        invalidate();
    }

    private void notifyChange() {
        if (listener != null) {
            listener.onChange(points.size(), medianInterval(), doubleGap());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            float x = e.getX();
            float y = e.getY();
            long now = SystemClock.elapsedRealtime();
            int delta = hasLast ? (int) (now - lastTime) : -1;

            boolean nearLast = false;
            if (!points.isEmpty()) {
                Task.Pt last = points.get(points.size() - 1);
                float dx = last.x - x;
                float dy = last.y - y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                // 又快又近的重复点击（典型就是「双击」）只记成一个路径点，避免轨迹过于密集
                boolean fast = delta > 0 && delta < 700;
                nearLast = dist < dp(42) && (fast || dist < dp(10));
            }

            if (nearLast) {
                // 认为是同一位置的双击：不新增点位，只记录双击间隔
                if (delta > 0) {
                    doubleGapHint = delta;
                }
            } else {
                points.add(new Task.Pt(Math.round(x), Math.round(y)));
                intervals.add(delta > 0 ? delta : 0);
            }
            lastTime = now;
            hasLast = true;
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            notifyChange();
            invalidate();
            return true;
        }
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(0x59070B14);

        // 顶部录制状态
        float cx = getWidth() / 2f;
        canvas.drawCircle(cx - dp(58), dp(46), dp(5), recDot);
        canvas.drawText("录制中 · 轻点屏幕记录要点的位置", cx + dp(6), dp(50), titlePaint);

        if (points.isEmpty()) {
            return;
        }

        for (int i = 1; i < points.size(); i++) {
            canvas.drawLine(points.get(i - 1).x, points.get(i - 1).y, points.get(i).x, points.get(i).y, glow);
            canvas.drawLine(points.get(i - 1).x, points.get(i - 1).y, points.get(i).x, points.get(i).y, line);
        }

        for (int i = 0; i < points.size(); i++) {
            Task.Pt p = points.get(i);
            canvas.drawCircle(p.x, p.y, dp(13), dotFill);
            canvas.drawCircle(p.x, p.y, dp(13), dotStroke);
            Paint.FontMetrics fm = numPaint.getFontMetrics();
            canvas.drawText(String.valueOf(i + 1), p.x, p.y - (fm.ascent + fm.descent) / 2f, numPaint);
        }
    }
}
