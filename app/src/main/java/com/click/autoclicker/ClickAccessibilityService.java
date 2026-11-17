package com.click.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：负责派发点击手势，并托管所有悬浮窗。
 * 不依赖 ROOT，也不需要开启「调试模式 / USB 调试」。
 */
public class ClickAccessibilityService extends AccessibilityService {

    private static final String TAG = "ClickService";
    private static ClickAccessibilityService instance;

    private OverlayManager overlay;

    public static ClickAccessibilityService get() {
        return instance;
    }

    public static boolean isReady() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try {
            overlay = new OverlayManager(this);
            overlay.onServiceReady();
        } catch (Throwable t) {
            Log.e(TAG, "init overlay failed", t);
        }
        try {
            if (TaskStore.get(this).keepAlive()) {
                KeepAliveService.start(this);
            }
        } catch (Throwable t) {
            Log.w(TAG, "keep alive failed", t);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要读取窗口内容
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        instance = null;
        if (overlay != null) {
            overlay.destroy();
            overlay = null;
        }
        // 无障碍被关掉后就没必要再占着前台服务，释放掉省电
        KeepAliveService.stop(this);
    }

    public OverlayManager overlay() {
        return overlay;
    }

    // ---------------------------------------------------------------- 手势

    public boolean dispatchTap(int x, int y, int tapDurationMs) {
        try {
            int[] p = clamp(x, y);
            Path path = new Path();
            path.moveTo(p[0], p[1]);
            int tap = Math.max(20, tapDurationMs);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(path, 0, tap));
            return dispatchGesture(b.build(), null, null);
        } catch (Throwable t) {
            Log.w(TAG, "tap failed", t);
            return false;
        }
    }

    /** 在同一个坐标点连续点两下（双击） */
    public boolean dispatchDoubleTap(int x, int y, int gapMs, int tapDurationMs) {
        try {
            int[] p = clamp(x, y);
            int tap = Math.max(20, tapDurationMs);
            int gap = Math.max(10, gapMs);

            Path path1 = new Path();
            path1.moveTo(p[0], p[1]);
            Path path2 = new Path();
            path2.moveTo(p[0], p[1]);

            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(new GestureDescription.StrokeDescription(path1, 0, tap));
            b.addStroke(new GestureDescription.StrokeDescription(path2, tap + gap, tap));
            return dispatchGesture(b.build(), null, null);
        } catch (Throwable t) {
            Log.w(TAG, "double tap failed", t);
            return false;
        }
    }

    private int[] clamp(int x, int y) {
        int w = screenWidth();
        int h = screenHeight();
        int cx = Math.max(1, Math.min(w - 2, x));
        int cy = Math.max(1, Math.min(h - 2, y));
        return new int[]{cx, cy};
    }

    // ---------------------------------------------------------------- 屏幕尺寸

    public int screenWidth() {
        return screenSize()[0];
    }

    public int screenHeight() {
        return screenSize()[1];
    }

    public int[] screenSize() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                return fallbackSize();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Rect b = wm.getCurrentWindowMetrics().getBounds();
                return new int[]{b.width(), b.height()};
            }
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            return new int[]{dm.widthPixels, dm.heightPixels};
        } catch (Throwable t) {
            return fallbackSize();
        }
    }

    private int[] fallbackSize() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return new int[]{dm.widthPixels, dm.heightPixels};
    }
}
