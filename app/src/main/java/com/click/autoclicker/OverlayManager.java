package com.click.autoclicker;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮层状态机：
 * TOOLBAR（工具条，可收缩成图标） / RUNNING（小圆点） / PAUSED（继续·结束）
 * RECORDING（录制层） / EDITING（轨迹编辑层）
 *
 * 工具条：图标（单击收缩/展开，双击打开首页，长按拖动）
 *        任务名（下拉选择任务） · 开始（先选轮次） · 录制 · 编辑
 */
public class OverlayManager {

    private static final String TAG = "Overlay";

    public enum Mode {TOOLBAR, RUNNING, PAUSED, RECORDING, EDITING}

    private static final int STYLE_PRIMARY = 0;
    private static final int STYLE_GHOST = 1;
    private static final int STYLE_DANGER = 2;

    private final ClickAccessibilityService svc;
    private final Context ctx;
    private final WindowManager wm;
    private final TaskStore store;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private View bar;
    private WindowManager.LayoutParams barLp;
    private View dot;
    private WindowManager.LayoutParams dotLp;
    private TrajectoryView path;
    private WindowManager.LayoutParams pathLp;
    private RecordView record;
    private WindowManager.LayoutParams recLp;
    private LinearLayout recBar;
    private WindowManager.LayoutParams recBarLp;
    private TextView recInfo;
    private String recordingName;
    private TrajectoryView editor;
    private WindowManager.LayoutParams editLp;
    private LinearLayout editBar;
    private WindowManager.LayoutParams editBarLp;
    private TextView editInfo;
    private String editingTaskId;
    private final List<List<Task.Pt>> undo = new ArrayList<List<Task.Pt>>();

    private Runner runner;
    private Mode mode = Mode.TOOLBAR;
    private int lastDotX, lastDotY;

    private PopupWindow popup;
    private Runnable pendingToggle;
    private boolean collapsed;
    private boolean appVisible;
    private int barX = -1, barY = -1;

    public OverlayManager(ClickAccessibilityService svc) {
        this.svc = svc;
        this.ctx = svc;
        this.wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        this.store = TaskStore.get(svc);
        this.barX = store.barX();
        this.barY = store.barY();
    }

    private int dp(float v) {
        return U.dpi(ctx, v);
    }

    public Mode mode() {
        return mode;
    }

    public boolean isRunning() {
        return runner != null && runner.isRunning();
    }

    public void onServiceReady() {
        ui.post(new Runnable() {
            @Override
            public void run() {
                showToolbar();
            }
        });
    }

    // ------------------------------------------------------------ 窗口基础

    private WindowManager.LayoutParams baseParams(int w, int h, boolean touchable) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams();
        p.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        p.format = PixelFormat.TRANSLUCENT;
        p.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (!touchable) {
            p.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        p.width = w;
        p.height = h;
        p.gravity = Gravity.TOP | Gravity.LEFT;
        return p;
    }

    private WindowManager.LayoutParams fullParams(boolean touchable) {
        WindowManager.LayoutParams p = baseParams(svc.screenWidth(), svc.screenHeight(), touchable);
        p.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        p.x = 0;
        p.y = 0;
        return p;
    }

    private boolean attach(View v, WindowManager.LayoutParams lp) {
        try {
            wm.addView(v, lp);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "addView via accessibility overlay failed", t);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(ctx)) {
                    lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                    wm.addView(v, lp);
                    return true;
                }
            } catch (Throwable t2) {
                Log.w(TAG, "addView via app overlay failed", t2);
            }
            return false;
        }
    }

    private void detach(View v) {
        if (v == null) {
            return;
        }
        try {
            wm.removeView(v);
        } catch (Throwable ignored) {
        }
    }

    private void toast(String msg) {
        try {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    // ------------------------------------------------------------ 工具条

    private String currentTaskName() {
        Task t = store.current();
        return t == null ? "无任务" : t.name;
    }

    private void showToolbar() {
        mode = Mode.TOOLBAR;
        removeBar();
        removeDot();
        removePath();
        dismissPopup();
        if (appVisible || MainActivity.isVisible()) {
            return;
        }

        View b = buildBar(false);
        WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);

        if (barX < 0 || barY < 0) {
            barX = dp(10);
            barY = Math.round(svc.screenHeight() * 0.42f);
        }
        int estW = collapsed ? dp(56) : dp(300);
        int maxX = Math.max(0, svc.screenWidth() - estW);
        int maxY = Math.max(0, svc.screenHeight() - dp(56));
        lp.x = Math.max(0, Math.min(barX, maxX));
        lp.y = Math.max(dp(24), Math.min(barY, maxY));
        barX = lp.x;
        barY = lp.y;

        bar = b;
        barLp = lp;
        if (!attach(b, lp)) {
            toast("悬浮层显示失败，请确认无障碍服务正常运行");
        }
    }

    private void removeBar() {
        detach(bar);
        bar = null;
        barLp = null;
    }

    private View buildBar(boolean paused) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(6), dp(5), collapsed && !paused ? dp(6) : dp(8), dp(5));
        GradientDrawable bg = U.grad(0xF20E1526, 0xF21B2540, 30, ctx);
        bg.setStroke(dp(1), 0x993ED8FF);
        root.setBackground(bg);
        root.setElevation(dp(8));

        // 图标：单击收缩/展开，双击打开首页，拖动移动
        TextView handle = U.tv(ctx, "C", 13, 0xFFFFFFFF);
        handle.setGravity(Gravity.CENTER);
        handle.setTypeface(Typeface.DEFAULT_BOLD);
        handle.setBackground(U.circle(U.CYAN, U.VIOLET, ctx));
        handle.setClickable(true);
        root.addView(handle, new LinearLayout.LayoutParams(dp(34), dp(34)));
        handle.setOnTouchListener(new IconTouch(root));

        if (paused) {
            TextView info = U.tv(ctx, "已暂停 " + roundText(), 11, U.SUB);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.leftMargin = dp(9);
            root.addView(info, ilp);

            root.addView(pill("继续", STYLE_PRIMARY, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    resumeRun();
                }
            }), marginLeft(dp(8)));
            root.addView(pill("结束", STYLE_DANGER, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopRun(true);
                }
            }), marginLeft(dp(6)));
            return root;
        }

        if (collapsed) {
            View mark = new View(ctx);
            mark.setBackground(U.solid(0xFF3ED8FF, 2, ctx));
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(dp(2), dp(13));
            mlp.leftMargin = dp(6);
            root.addView(mark, mlp);
            return root;
        }

        // 任务名（点一下弹出任务下拉）
        TextView name = U.tv(ctx, currentTaskName() + " ▾", 11.5f, 0xFFB8C6E8);
        name.setSingleLine(true);
        name.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        name.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTaskPicker(v);
            }
        });

        final TextView startBtn = pillSmall("开始", STYLE_PRIMARY);
        root.addView(startBtn, marginLeft(dp(6)));
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRoundsPicker(v, store.current());
            }
        });

        TextView recBtn = pillSmall("录制", STYLE_GHOST);
        root.addView(recBtn, marginLeft(dp(6)));
        recBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecordingFromBar();
            }
        });

        TextView editBtn = pillSmall("编辑", STYLE_GHOST);
        root.addView(editBtn, marginLeft(dp(6)));
        editBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Task t = store.current();
                if (t == null || t.points.isEmpty()) {
                    toast("还没有点位，先点「录制」");
                    return;
                }
                startEditing(t.id);
            }
        });

        return root;
    }

    private String roundText() {
        if (runner == null) {
            return "";
        }
        int total = runner.total();
        return total > 0 ? runner.round() + "/" + total + " 轮" : "第 " + runner.round() + " 轮";
    }

    private LinearLayout.LayoutParams marginLeft(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = px;
        return lp;
    }

    private TextView pillSmall(String text, int style) {
        TextView t = U.tv(ctx, text, 12.5f, 0xFFFFFFFF);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(13), dp(8), dp(13), dp(8));
        applyPillStyle(t, style);
        return t;
    }

    private TextView pill(String text, int style, View.OnClickListener l) {
        TextView t = pillSmall(text, style);
        t.setOnClickListener(l);
        return t;
    }

    private void applyPillStyle(TextView t, int style) {
        if (style == STYLE_PRIMARY) {
            t.setBackground(U.grad(U.CYAN, U.VIOLET, 18, ctx));
            t.setTextColor(0xFF081018);
        } else if (style == STYLE_DANGER) {
            t.setBackground(U.outline(0xFF2A1420, U.RED, 1, 18, ctx));
            t.setTextColor(U.RED);
        } else {
            t.setBackground(U.outline(0x1A3ED8FF, 0xFF3ED8FF, 1, 18, ctx));
            t.setTextColor(0xFFB8E9FF);
        }
    }

    /** 图标手势：拖动移动、单击收缩/展开、双击打开首页 */
    private class IconTouch implements View.OnTouchListener {
        private final View win;
        private final float slop;
        private float downRawX, downRawY;
        private int startX, startY;
        private boolean dragged;

        IconTouch(View win) {
            this.win = win;
            this.slop = ViewConfiguration.get(ctx).getScaledTouchSlop() + dp(3);
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            WindowManager.LayoutParams lp = barLp;
            if (lp == null) {
                return false;
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragged = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRawX;
                    float dy = e.getRawY() - downRawY;
                    if (!dragged && (Math.abs(dx) > slop || Math.abs(dy) > slop)) {
                        dragged = true;
                    }
                    if (dragged) {
                        lp.x = startX + Math.round(dx);
                        lp.y = startY + Math.round(dy);
                        try {
                            wm.updateViewLayout(win, lp);
                        } catch (Throwable ignored) {
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragged) {
                        clampBar(win, lp);
                    } else {
                        handleIconTap();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (dragged) {
                        clampBar(win, lp);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private void clampBar(View win, WindowManager.LayoutParams lp) {
        if (lp == null) {
            return;
        }
        int ww = Math.max(win.getWidth(), dp(60));
        int wh = Math.max(win.getHeight(), dp(40));
        lp.x = Math.max(0, Math.min(lp.x, svc.screenWidth() - ww));
        lp.y = Math.max(0, Math.min(lp.y, svc.screenHeight() - wh));
        try {
            wm.updateViewLayout(win, lp);
        } catch (Throwable ignored) {
        }
        barX = lp.x;
        barY = lp.y;
        store.setBarPos(lp.x, lp.y);
    }

    /** 单击 = 收缩/展开；双击 = 打开首页 */
    private void handleIconTap() {
        if (pendingToggle != null) {
            ui.removeCallbacks(pendingToggle);
            pendingToggle = null;
            openApp();
            return;
        }
        pendingToggle = new Runnable() {
            @Override
            public void run() {
                pendingToggle = null;
                toggleCollapse();
            }
        };
        ui.postDelayed(pendingToggle, 260);
    }

    private void toggleCollapse() {
        if (mode != Mode.TOOLBAR) {
            return;
        }
        collapsed = !collapsed;
        showToolbar();
    }

    private void openApp() {
        try {
            Intent i = new Intent();
            i.setClassName(ctx.getPackageName(), "com.click.autoclicker.MainActivity");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(i);
            appVisible = true;
            removeBar();
        } catch (Throwable t) {
            Log.w(TAG, "open app failed", t);
            toast("打开首页失败");
        }
    }

    // ------------------------------------------------------------ 弹窗

    private void dismissPopup() {
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Throwable ignored) {
            }
            popup = null;
        }
    }

    private PopupWindow showPopup(View anchor, View content, int widthDp, int maxHeightDp) {
        dismissPopup();
        if (barLp == null || anchor == null) {
            return null;
        }
        int w = dp(widthDp);
        int cap = dp(maxHeightDp);

        ScrollView sc = new ScrollView(ctx);
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(content);
        sc.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int h = Math.min(sc.getMeasuredHeight(), cap);

        PopupWindow pw = new PopupWindow(sc, w, h, false);
        pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(10));

        int sw = svc.screenWidth();
        int sh = svc.screenHeight();
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int xScreen = Math.max(dp(6), Math.min(loc[0], sw - w - dp(6)));

        int barH = bar == null ? dp(52) : Math.max(bar.getHeight(), dp(44));
        int yBelow = barLp.y + barH + dp(8);
        int yAbove = barLp.y - h - dp(10);
        int yScreen = (yBelow + h <= sh - dp(16) || yAbove < dp(40)) ? yBelow : yAbove;
        yScreen = Math.max(dp(24), Math.min(yScreen, sh - h - dp(10)));

        try {
            pw.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT,
                    xScreen - barLp.x, yScreen - barLp.y);
            popup = pw;
            return pw;
        } catch (Throwable t) {
            Log.w(TAG, "popup failed", t);
            dismissPopup();
            toast("菜单弹出失败，请先在系统里开启悬浮窗权限");
            return null;
        }
    }

    private LinearLayout popupCard() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = U.grad(0xFF141B2E, 0xFF1E2740, 16, ctx);
        bg.setStroke(dp(1), 0x993ED8FF);
        box.setBackground(bg);
        box.setElevation(dp(8));
        return box;
    }

    private void showRoundsPicker(View anchor, final Task task) {
        if (task == null) {
            toast("还没有任务，先点「录制」录一条");
            return;
        }
        if (task.points.isEmpty()) {
            toast("这个任务还没有点位");
            return;
        }

        LinearLayout box = popupCard();
        TextView title = U.tv(ctx, "执行轮次（选完立刻开始）", 11, U.SUB);
        title.setPadding(dp(4), dp(2), dp(4), dp(8));
        box.addView(title);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row);
        for (int i = 1; i <= 5; i++) {
            final int v = i;
            TextView chip = roundChip(i + " 次", store.rounds() == i);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismissPopup();
                    store.setRounds(v);
                    startTask(task, v);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 1) {
                lp.leftMargin = dp(4);
            }
            row.addView(chip, lp);
        }

        TextView manual = roundChip("手动停止（一直循环）", store.rounds() == 0);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissPopup();
                store.setRounds(0);
                startTask(task, 0);
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(6);
        box.addView(manual, mlp);

        showPopup(anchor, box, 250, 260);
    }

    private TextView roundChip(String text, boolean selected) {
        TextView t = U.tv(ctx, text, 11.5f, selected ? 0xFF081018 : 0xFFB8C6E8);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(6), dp(9), dp(6), dp(9));
        t.setBackground(selected ? U.grad(U.CYAN, U.VIOLET, 10, ctx)
                : U.outline(0xFF1E2740, 0xFF2E3A5C, 1, 10, ctx));
        return t;
    }

    private void showTaskPicker(View anchor) {
        List<Task> tasks = store.tasks();
        if (tasks.isEmpty()) {
            toast("还没有任务，点「录制」录一条");
            return;
        }
        LinearLayout box = popupCard();
        TextView title = U.tv(ctx, "选择任务", 11, U.SUB);
        title.setPadding(dp(6), dp(2), dp(6), dp(8));
        box.addView(title);

        String cur = store.currentId();
        for (final Task t : tasks) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(10), dp(8), dp(10));
            boolean isCur = t.id.equals(cur);
            if (isCur) {
                row.setBackground(U.outline(0x223ED8FF, 0x003ED8FF, 1, 10, ctx));
            }
            TextView name = U.tv(ctx, t.name, 12.5f, isCur ? 0xFF3ED8FF : U.TEXT);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(name, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView meta = U.tv(ctx, t.points.size() + " 点", 10.5f, U.SUB);
            row.addView(meta);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    store.setCurrent(t.id);
                    dismissPopup();
                    showToolbar();
                }
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(2);
            box.addView(row, rlp);
        }
        showPopup(anchor, box, 190, 300);
    }

    // ------------------------------------------------------------ 执行

    public void startTask(Task task, int rounds) {
        dismissPopup();
        if (task == null || task.points.isEmpty()) {
            toast("这个任务还没有点位");
            return;
        }
        if (runner != null) {
            runner.stop();
            runner = null;
        }
        removeAll();
        mode = Mode.RUNNING;

        List<Task.Pt> pts = task.copyPoints();
        showPath(pts);

        int dotSize = dp(68);
        int[] pos = safeDotPosition(pts, dotSize);
        showDot(pos[0], pos[1], dotSize);

        runner = new Runner(svc);
        runner.start(task, rounds, new Runner.Listener() {
            @Override
            public void onTick(int round, int totalRounds, int index) {
                if (path != null) {
                    path.setHighlight(index);
                }
            }

            @Override
            public void onFinish(boolean completed) {
                removeDot();
                removePath();
                runner = null;
                showToolbar();
                if (completed) {
                    toast("执行完成");
                }
            }
        });
    }

    /** 选一个离所有点位都远的位置放圆点，避开执行区域 */
    private int[] safeDotPosition(List<Task.Pt> pts, int size) {
        int sw = svc.screenWidth();
        int sh = svc.screenHeight();
        int inset = dp(10);
        int topInset = dp(46);
        int bottomInset = dp(56);

        int[][] cands = new int[][]{
                {inset, topInset},
                {sw - size - inset, topInset},
                {inset, sh - size - bottomInset},
                {sw - size - inset, sh - size - bottomInset},
                {sw / 2 - size / 2, topInset},
                {sw / 2 - size / 2, sh - size - bottomInset},
                {inset, sh / 2 - size / 2},
                {sw - size - inset, sh / 2 - size / 2}
        };

        int best = 0;
        double bestScore = -1;
        for (int i = 0; i < cands.length; i++) {
            double minDist = Double.MAX_VALUE;
            double cx = cands[i][0] + size / 2.0;
            double cy = cands[i][1] + size / 2.0;
            for (Task.Pt p : pts) {
                double dx = p.x - cx;
                double dy = p.y - cy;
                minDist = Math.min(minDist, Math.sqrt(dx * dx + dy * dy));
            }
            double score = minDist + (i < 4 ? 260 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return cands[best];
    }

    private void showDot(int x, int y, int size) {
        removeDot();
        DotView d = new DotView(ctx);
        d.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseRun();
            }
        });
        WindowManager.LayoutParams lp = baseParams(size, size, true);
        lp.x = x;
        lp.y = y;
        dot = d;
        dotLp = lp;
        attach(d, lp);
        lastDotX = x;
        lastDotY = y;
    }

    private void removeDot() {
        detach(dot);
        dot = null;
        dotLp = null;
    }

    private void showPath(List<Task.Pt> pts) {
        removePath();
        TrajectoryView v = new TrajectoryView(ctx);
        v.setPoints(pts);
        v.setHighlight(0);
        WindowManager.LayoutParams lp = fullParams(false);
        path = v;
        pathLp = lp;
        attach(v, lp);
    }

    private void removePath() {
        detach(path);
        path = null;
        pathLp = null;
    }

    private void pauseRun() {
        if (runner == null || !runner.isRunning()) {
            return;
        }
        runner.pause();
        mode = Mode.PAUSED;
        collapsed = false;
        removeDot();
        removeBar();
        showPausedBar();
    }

    private void showPausedBar() {
        dismissPopup();
        removeBar();
        View b = buildBar(true);
        WindowManager.LayoutParams lp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        int x = Math.max(0, Math.min(lastDotX, svc.screenWidth() - dp(260)));
        int y = Math.max(dp(24), Math.min(lastDotY, svc.screenHeight() - dp(120)));
        lp.x = x;
        lp.y = y;
        bar = b;
        barLp = lp;
        attach(b, lp);
    }

    private void resumeRun() {
        if (runner == null) {
            return;
        }
        mode = Mode.RUNNING;
        removeBar();
        showDot(lastDotX, lastDotY, dp(68));
        runner.resume();
    }

    /** 供主界面调用：结束当前执行并回到工具条 */
    public void stop() {
        stopRun(true);
    }

    private void stopRun(boolean notify) {
        if (runner != null) {
            runner.stop();
            runner = null;
        }
        removeDot();
        removePath();
        showToolbar();
        if (notify) {
            toast("已结束执行");
        }
    }

    // ------------------------------------------------------------ 录制

    private String autoTaskName() {
        return store.nextTaskName();
    }

    private void startRecordingFromBar() {
        startRecording(autoTaskName());
    }

    public void startRecording(String name) {
        recordingName = name;
        if (runner != null) {
            runner.stop();
            runner = null;
        }
        removeAll();
        dismissPopup();
        mode = Mode.RECORDING;

        RecordView rv = new RecordView(ctx);
        WindowManager.LayoutParams lp = fullParams(true);
        record = rv;
        recLp = lp;
        if (!attach(rv, lp)) {
            record = null;
            toast("悬浮窗创建失败");
            showToolbar();
            return;
        }

        LinearLayout b = buildRecordBar();
        WindowManager.LayoutParams blp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        blp.y = dp(34);
        recBar = b;
        recBarLp = blp;
        attach(b, blp);

        rv.setListener(new RecordView.Listener() {
            @Override
            public void onChange(int count, int intervalMs, int doubleGapMs) {
                if (recInfo != null) {
                    recInfo.setText(count == 0
                            ? (recordingName + " · 轻点屏幕，按顺序点出要点的位置")
                            : (recordingName + " · 已记录 " + count + " 个点位 · 节奏 " + intervalMs + " ms"));
                }
            }
        });
    }

    private LinearLayout buildRecordBar() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        GradientDrawable bg = U.grad(0xF20E1526, 0xF21B2540, 22, ctx);
        bg.setStroke(dp(1), 0x993ED8FF);
        root.setBackground(bg);
        root.setElevation(dp(10));

        recInfo = U.tv(ctx, "轻点屏幕，按顺序点出要点的位置", 11.5f, 0xFFB8C6E8);
        root.addView(recInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        root.addView(row, rlp);

        row.addView(pill("撤销", STYLE_GHOST, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (record != null) {
                    record.undo();
                }
            }
        }));
        row.addView(pill("保存任务", STYLE_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveRecording();
            }
        }), marginLeft(dp(8)));
        row.addView(pill("退出", STYLE_DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                discardRecording();
            }
        }), marginLeft(dp(8)));
        return root;
    }

    private void saveRecording() {
        if (record == null) {
            return;
        }
        if (record.points().isEmpty()) {
            toast("还没有记录任何点位");
            return;
        }
        Task t = new Task(recordingName == null || recordingName.length() == 0
                ? autoTaskName() : recordingName);
        if (t.name.length() > 4) {
            t.name = t.name.substring(0, 4);
        }
        List<Task.Pt> pts = new ArrayList<Task.Pt>();
        for (Task.Pt p : record.points()) {
            pts.add(p.copy());
        }
        t.points = pts;
        t.loopIntervalMs = record.medianInterval();
        t.doubleGapMs = record.doubleGap();
        t.updatedAt = System.currentTimeMillis();
        store.add(t);
        store.setCurrent(t.id);
        store.touch();

        removeRecord();
        showToolbar();
        toast("已保存：" + t.name + "（" + t.points.size() + " 个点位）");
    }

    private void discardRecording() {
        removeRecord();
        showToolbar();
        toast("已放弃本次录制");
    }

    private void removeRecord() {
        detach(record);
        record = null;
        detach(recBar);
        recBar = null;
        recInfo = null;
    }

    // ------------------------------------------------------------ 编辑轨迹

    public void startEditing(String taskId) {
        Task t = store.find(taskId);
        if (t == null || t.points.isEmpty()) {
            toast("这个任务还没有点位");
            return;
        }
        if (runner != null) {
            runner.stop();
            runner = null;
        }
        removeAll();
        mode = Mode.EDITING;
        editingTaskId = t.id;
        undo.clear();

        TrajectoryView v = new TrajectoryView(ctx);
        v.setPoints(t.points);
        v.setEditable(true);
        v.setOnChange(new TrajectoryView.OnChange() {
            @Override
            public void onBeforeChange(List<Task.Pt> snapshot) {
                if (undo.size() > 40) {
                    undo.remove(0);
                }
                undo.add(snapshot);
            }

            @Override
            public void onChanged() {
                updateEditorInfo();
            }
        });
        WindowManager.LayoutParams lp = fullParams(true);
        editor = v;
        editLp = lp;
        if (!attach(v, lp)) {
            editor = null;
            toast("悬浮窗创建失败");
            showToolbar();
            return;
        }

        LinearLayout b = buildEditorBar();
        WindowManager.LayoutParams blp = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, true);
        blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        blp.y = dp(30);
        editBar = b;
        editBarLp = blp;
        attach(b, blp);
        updateEditorInfo();
    }

    private void updateEditorInfo() {
        if (editInfo == null) {
            return;
        }
        Task t = store.find(editingTaskId);
        String name = t == null ? "任务" : t.name;
        int n = editor == null ? 0 : editor.points().size();
        editInfo.setText(name + " · " + n + " 个点位 · 拖动圆点可微调位置");
    }

    private LinearLayout buildEditorBar() {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(10));
        GradientDrawable bg = U.grad(0xF20E1526, 0xF21B2540, 22, ctx);
        bg.setStroke(dp(1), 0x993ED8FF);
        root.setBackground(bg);
        root.setElevation(dp(10));

        editInfo = U.tv(ctx, "任务", 11.5f, 0xFFB8C6E8);
        root.addView(editInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(10);
        root.addView(row, rlp);

        row.addView(pill("撤销", STYLE_GHOST, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undoEdit();
            }
        }));
        row.addView(pill("清空", STYLE_GHOST, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editor != null) {
                    undo.add(editor.snapshot());
                    editor.setPoints(new ArrayList<Task.Pt>());
                    updateEditorInfo();
                }
            }
        }), marginLeft(dp(8)));
        row.addView(pill("保存", STYLE_PRIMARY, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveEditing();
            }
        }), marginLeft(dp(8)));
        row.addView(pill("退出", STYLE_DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                removeEditor();
                showToolbar();
            }
        }), marginLeft(dp(8)));
        return root;
    }

    private void undoEdit() {
        if (editor == null || undo.isEmpty()) {
            toast("没有可撤销的操作");
            return;
        }
        List<Task.Pt> last = undo.remove(undo.size() - 1);
        editor.setPoints(last);
        updateEditorInfo();
    }

    private void saveEditing() {
        Task t = store.find(editingTaskId);
        if (t == null) {
            removeEditor();
            showToolbar();
            return;
        }
        if (editor == null || editor.points().isEmpty()) {
            toast("至少保留一个点位");
            return;
        }
        List<Task.Pt> pts = new ArrayList<Task.Pt>();
        for (Task.Pt p : editor.points()) {
            pts.add(p.copy());
        }
        t.points = pts;
        t.updatedAt = System.currentTimeMillis();
        store.touch();
        removeEditor();
        showToolbar();
        toast("轨迹已更新（" + pts.size() + " 个点位）");
    }

    private void removeEditor() {
        detach(editor);
        editor = null;
        detach(editBar);
        editBar = null;
        editInfo = null;
    }

    // ------------------------------------------------------------ 首页可见性

    /** 打开 APP 首页时隐藏工具条，离开首页后再出现 */
    public void setAppVisible(final boolean visible) {
        appVisible = visible;
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    dismissPopup();
                    if (mode == Mode.TOOLBAR || mode == Mode.PAUSED) {
                        removeBar();
                    }
                } else {
                    if (mode == Mode.TOOLBAR) {
                        showToolbar();
                    } else if (mode == Mode.PAUSED && bar == null) {
                        showPausedBar();
                    }
                }
            }
        });
    }

    // ------------------------------------------------------------ 清理

    private void removeAll() {
        dismissPopup();
        removeBar();
        removeDot();
        removePath();
        removeRecord();
        removeEditor();
    }

    public void destroy() {
        if (runner != null) {
            runner.stop();
            runner = null;
        }
        removeAll();
    }
}
