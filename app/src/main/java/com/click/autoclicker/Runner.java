package com.click.autoclicker;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** 执行引擎：按顺序连续双击各点位 */
public class Runner {

    private static final String TAG = "Runner";

    public interface Listener {
        /** 每次点击前回调，index 为当前点位序号（从 0 开始） */
        void onTick(int round, int totalRounds, int index);

        /** 结束回调，completed 表示是否按轮次跑完 */
        void onFinish(boolean completed);
    }

    private final ClickAccessibilityService svc;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private List<Task.Pt> points = new ArrayList<Task.Pt>();
    private int loopMs = 600;
    private int gapMs = 90;
    private int tapMs = 60;
    private int totalRounds = 1;

    private int round = 1;
    private int index = 0;
    private boolean running;
    private boolean paused;
    private Listener listener;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            stepInternal();
        }
    };

    public Runner(ClickAccessibilityService svc) {
        this.svc = svc;
    }

    public void start(Task task, int rounds, Listener l) {
        points = task.copyPoints();
        loopMs = task.loopIntervalMs;
        gapMs = task.doubleGapMs;
        tapMs = task.tapDurationMs;
        totalRounds = rounds;
        listener = l;
        round = 1;
        index = 0;
        running = true;
        paused = false;
        if (points.isEmpty()) {
            running = false;
            if (l != null) {
                l.onFinish(false);
            }
            return;
        }
        handler.removeCallbacks(step);
        handler.post(step);
    }

    private void stepInternal() {
        if (!running || paused || points.isEmpty()) {
            return;
        }
        Task.Pt p = points.get(index);
        boolean ok = svc.dispatchDoubleTap(p.x, p.y, gapMs, tapMs);
        if (!ok) {
            Log.w(TAG, "dispatch failed at " + p.x + "," + p.y);
        }
        if (listener != null) {
            listener.onTick(round, totalRounds, index);
        }

        index++;
        if (index >= points.size()) {
            index = 0;
            round++;
            if (totalRounds > 0 && round > totalRounds) {
                running = false;
                if (listener != null) {
                    listener.onFinish(true);
                }
                return;
            }
        }
        int delay = 2 * tapMs + gapMs + Math.max(0, loopMs);
        handler.postDelayed(step, delay);
    }

    public void pause() {
        if (!running) {
            return;
        }
        paused = true;
        handler.removeCallbacks(step);
    }

    public void resume() {
        if (!running || !paused) {
            return;
        }
        paused = false;
        handler.postDelayed(step, 150);
    }

    public void stop() {
        running = false;
        paused = false;
        handler.removeCallbacks(step);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isPaused() {
        return paused;
    }

    public int round() {
        return round;
    }

    public int total() {
        return totalRounds;
    }

    public int index() {
        return index;
    }
}
