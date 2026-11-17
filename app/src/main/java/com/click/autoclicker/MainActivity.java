package com.click.autoclicker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;
    /** 首页是否在前台：服务刚连上时用它判断要不要先不显示工具条 */
    private static boolean foreground;
    /** 任务名最多四个字 */
    private static final int NAME_MAX = 4;

    public static boolean isVisible() {
        return foreground;
    }

    private TaskStore store;

    private LinearLayout tasksBox;
    private TextView empty;
    private TextView txtAcc, btnAcc, txtOv, btnOv, btnRecord, btnStop;
    private TextView txtKeep, btnKeep;
    private View dotAcc, dotOv, dotKeep;
    private boolean serviceOffPrompted;

    private int dp(float v) {
        return U.dpi(this, v);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = TaskStore.get(this);

        tasksBox = (LinearLayout) findViewById(R.id.tasks);
        empty = (TextView) findViewById(R.id.empty);
        txtAcc = (TextView) findViewById(R.id.txt_acc);
        btnAcc = (TextView) findViewById(R.id.btn_acc);
        txtOv = (TextView) findViewById(R.id.txt_ov);
        btnOv = (TextView) findViewById(R.id.btn_ov);
        btnRecord = (TextView) findViewById(R.id.btn_record);
        btnStop = (TextView) findViewById(R.id.btn_stop_run);
        dotAcc = findViewById(R.id.dot_acc);
        dotOv = findViewById(R.id.dot_ov);
        txtKeep = (TextView) findViewById(R.id.txt_keep);
        btnKeep = (TextView) findViewById(R.id.btn_keep);
        dotKeep = findViewById(R.id.dot_keep);

        TextView footer = (TextView) findViewById(R.id.txt_footer);
        if (footer != null) {
            footer.setText(getString(R.string.footer_credit, appVersion()));
        }

        btnKeep.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleKeepAlive();
            }
        });
        requestNotificationPermission();

        btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startRecording();
            }
        });
        btnAcc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        btnOv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestOverlayPermission();
            }
        });
        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRun();
            }
        });

        refreshStatus();
        renderTasks();
    }

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        refreshStatus();
        renderTasks();
        updateStopButton();
        applyKeepAlive();
        ClickAccessibilityService svc = ClickAccessibilityService.get();
        if (svc != null && svc.overlay() != null) {
            svc.overlay().setAppVisible(true);
            serviceOffPrompted = false;
        } else {
            tryAutoRestore();
            promptServiceOff();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // 只有真正看不见了（回桌面 / 切到别的应用）才放出工具条；
        // 系统弹窗（如通知授权）只会 onPause，不应让工具条冒出来。
        foreground = false;
        ClickAccessibilityService svc = ClickAccessibilityService.get();
        if (svc != null && svc.overlay() != null) {
            svc.overlay().setAppVisible(false);
        }
    }

    // --------------------------------------------------------------- 权限状态

    /** 从安装包里读真实版本号，保证底部显示的版本永远和 APK 一致 */
    private String appVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Throwable t) {
            return "";
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) {
                return false;
            }
            String full = getPackageName() + "/" + ClickAccessibilityService.class.getName();
            String shortName = getPackageName() + "/.ClickAccessibilityService";
            return enabled.contains(full) || enabled.contains(shortName);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean hasOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void refreshStatus() {
        boolean acc = isAccessibilityEnabled();
        txtAcc.setText(acc ? R.string.acc_on : R.string.acc_off);
        txtAcc.setTextColor(acc ? U.TEXT : 0xFFB8C6E8);
        btnAcc.setText(acc ? R.string.btn_acc_on : R.string.btn_acc_off);
        dotAcc.setBackgroundResource(acc ? R.drawable.bg_dot_on : R.drawable.bg_dot_off);

        boolean ov = hasOverlay();
        txtOv.setText(ov ? R.string.ov_on : R.string.ov_off);
        btnOv.setText(ov ? R.string.btn_ov_on : R.string.btn_ov_off);
        dotOv.setBackgroundResource(ov ? R.drawable.bg_dot_on : R.drawable.bg_dot_off);
        txtOv.setTextColor(ov ? 0xFFB8C6E8 : U.SUB);
        btnOv.setTextColor(ov ? U.CYAN : U.SUB);
    }

    // --------------------------------------------------------------- 后台常驻

    private void applyKeepAlive() {
        boolean on = store.keepAlive();
        btnKeep.setText(on ? R.string.btn_keep_on : R.string.btn_keep_off);
        btnKeep.setTextColor(on ? U.CYAN : U.SUB);
        txtKeep.setTextColor(on ? 0xFFB8C6E8 : U.SUB);
        dotKeep.setBackgroundResource(on ? R.drawable.bg_dot_on : R.drawable.bg_dot_off);
        if (on) {
            if (!KeepAliveService.isRunning()) {
                KeepAliveService.start(this);
            }
        } else {
            KeepAliveService.stop(this);
        }
    }

    private void toggleKeepAlive() {
        store.setKeepAlive(!store.keepAlive());
        applyKeepAlive();
        toast(store.keepAlive() ? "已开启后台常驻" : "已关闭后台常驻（更省电）");
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1002);
        }
    }

    // --------------------------------------------------------------- 无障碍掉线处理

    /** 应用被强制停止后系统会清掉无障碍开关，这里给用户一次点击就能回到该服务的详情页 */
    private void openAccessibilitySettings() {
        ComponentName cn = new ComponentName(this, ClickAccessibilityService.class);
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            // 直接跳到 Click 这一项的详情页（AOSP 及多数国产 ROM 支持该参数）
            i.putExtra(":settings:fragment_args_key", cn.flattenToString());
            startActivity(i);
            toast("找到 Click 连点器并打开开关即可");
        } catch (Throwable t) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                toast("在「已安装的服务」中找到 Click 并开启");
            } catch (Throwable t2) {
                toast("无法打开无障碍设置，请手动进入系统设置 › 无障碍");
            }
        }
    }

    /** 如果用户曾用 adb 授权过 WRITE_SECURE_SETTINGS，这里就能自动把开关写回去 */
    private void tryAutoRestore() {
        if (isAccessibilityEnabled()) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            String cur = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String mine = getPackageName() + "/" + ClickAccessibilityService.class.getName();
            if (cur == null || !cur.contains(mine)) {
                String merged = (cur == null || cur.trim().length() == 0) ? mine : cur + ":" + mine;
                Settings.Secure.putString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, merged);
                Settings.Secure.putInt(getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                toast(getString(R.string.auto_restore_ok));
            }
        } catch (Throwable ignored) {
        }
    }

    private void promptServiceOff() {
        if (!isAccessibilityEnabled()) {
            if (!serviceOffPrompted) {
                serviceOffPrompted = true;
                showServiceOffDialog();
            }
            return;
        }
        serviceOffPrompted = false;
    }

    private void showServiceOffDialog() {
        try {
            new AlertDialog.Builder(this, R.style.DialogTheme)
                    .setTitle(R.string.svc_off_title)
                    .setMessage(R.string.svc_off_msg)
                    .setPositiveButton(R.string.svc_off_go, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openAccessibilitySettings();
                        }
                    })
                    .setNegativeButton(R.string.svc_off_later, null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
            } catch (Throwable t) {
                toast("请手动进入系统设置授予「显示在其他应用上层」");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_OVERLAY) {
            refreshStatus();
        }
    }

    // --------------------------------------------------------------- 任务列表

    private void renderTasks() {
        tasksBox.removeAllViews();
        List<Task> list = store.tasks();
        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        for (Task t : list) {
            tasksBox.addView(buildTaskRow(t));
        }
    }

    private String shortName(Task t) {
        String n = t.name == null ? "" : t.name;
        return n.length() > NAME_MAX ? n.substring(0, NAME_MAX) : n;
    }

    private View buildTaskRow(final Task t) {
        boolean current = t.id.equals(store.currentId());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(12), dp(12), dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                store.setCurrent(t.id);
                renderTasks();
                toast("已选中「" + shortName(t) + "」，工具条上点「编辑」可改轨迹");
            }
        });

        // 第一行：名称 + 当前标记 + 开始
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView name = U.tv(this, shortName(t), 15, U.TEXT);
        name.setTypeface(null, Typeface.BOLD);
        name.setSingleLine(true);
        top.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (current) {
            TextView badge = U.tv(this, "当前", 10, 0xFF3ED8FF);
            badge.setPadding(dp(7), dp(2), dp(7), dp(2));
            badge.setBackground(U.outline(0x223ED8FF, 0xFF3ED8FF, 1, 8, this));
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.rightMargin = dp(8);
            top.addView(badge, blp);
        }

        TextView play = U.tv(this, "▶", 15, 0xFFFFFFFF);
        play.setGravity(Gravity.CENTER);
        play.setBackground(U.circle(U.CYAN, U.VIOLET, this));
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askRounds(t);
            }
        });
        top.addView(play, new LinearLayout.LayoutParams(dp(40), dp(40)));

        // 第二行：摘要
        TextView meta = U.tv(this, t.points.size() + " 个点位 · 循环 " + t.loopIntervalMs
                + " ms · 双击 " + t.doubleGapMs + " ms", 11, U.SUB);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(4);
        card.addView(meta, mlp);

        // 第三行：参数 / 删除
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(10);
        card.addView(actions, alp);

        actions.addView(smallButton("参数", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showParamDialog(t);
            }
        }));

        actions.addView(smallButton("删除", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete(t);
            }
        }));

        return card;
    }

    private TextView smallButton(String text, View.OnClickListener l) {
        TextView v = U.tv(this, text, 11, 0xFFB8C6E8);
        v.setPadding(dp(10), dp(6), dp(10), dp(6));
        v.setBackgroundResource(R.drawable.bg_btn_ghost);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        v.setLayoutParams(lp);
        v.setOnClickListener(l);
        return v;
    }

    // --------------------------------------------------------------- 行为

    private ClickAccessibilityService service() {
        ClickAccessibilityService svc = ClickAccessibilityService.get();
        if (svc == null || svc.overlay() == null) {
            toast(getString(R.string.toast_no_service));
            openAccessibilitySettings();
            return null;
        }
        return svc;
    }

    /** 点开始 → 先选执行轮次，选完立刻开始 */
    private void askRounds(final Task t) {
        final ClickAccessibilityService svc = service();
        if (svc == null) {
            return;
        }
        if (t.points.isEmpty()) {
            toast(getString(R.string.toast_no_point));
            return;
        }
        store.setCurrent(t.id);
        renderTasks();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        box.addView(row);

        final AlertDialog[] dlg = new AlertDialog[1];
        for (int i = 1; i <= 5; i++) {
            final int v = i;
            TextView chip = dialogChip(i + " 次", store.rounds() == i);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (dlg[0] != null) {
                        dlg[0].dismiss();
                    }
                    store.setRounds(v);
                    svc.overlay().startTask(t, v);
                    moveTaskToBack(true);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 1) {
                lp.leftMargin = dp(5);
            }
            row.addView(chip, lp);
        }

        TextView manual = dialogChip("手动停止（一直循环，点圆点暂停后结束）", store.rounds() == 0);
        manual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dlg[0] != null) {
                    dlg[0].dismiss();
                }
                store.setRounds(0);
                svc.overlay().startTask(t, 0);
                moveTaskToBack(true);
            }
        });
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = dp(8);
        box.addView(manual, mlp);

        TextView hint = U.tv(this, "选完立刻开始执行，屏幕会收缩成一个小圆点", 11, 0xFF6F7B96);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        box.addView(hint, hlp);

        dlg[0] = new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("「" + shortName(t) + "」执行轮次")
                .setView(box)
                .setNegativeButton("取消", null)
                .create();
        dlg[0].show();
    }

    private TextView dialogChip(String text, boolean selected) {
        TextView t = U.tv(this, text, 12, selected ? 0xFF081018 : 0xFFB8C6E8);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(11), dp(4), dp(11));
        t.setBackground(selected ? U.grad(U.CYAN, U.VIOLET, 10, this)
                : U.outline(0xFF1E2740, 0xFF2E3A5C, 1, 10, this));
        return t;
    }

    private void startRecording() {
        final ClickAccessibilityService svc = service();
        if (svc == null) {
            return;
        }

        final String auto = store.nextTaskName();
        final EditText input = nameField(auto);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(U.tv(this, "任务名称（最多 4 个字）", 12, U.SUB));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(6);
        box.addView(input, ilp);

        new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("录制新任务")
                .setView(box)
                .setPositiveButton("开始录制", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String n = input.getText().toString().trim();
                        if (n.length() == 0) {
                            n = auto;
                        }
                        if (n.length() > NAME_MAX) {
                            n = n.substring(0, NAME_MAX);
                        }
                        svc.overlay().startRecording(n);
                        moveTaskToBack(true);
                        toast("回到目标界面，按顺序点出要点的位置");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText nameField(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(U.TEXT);
        e.setHintTextColor(0xFF6F7B96);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NAME_MAX)});
        return e;
    }

    private void stopRun() {
        ClickAccessibilityService svc = ClickAccessibilityService.get();
        if (svc != null && svc.overlay() != null) {
            svc.overlay().stop();
        }
        updateStopButton();
    }

    private void updateStopButton() {
        ClickAccessibilityService svc = ClickAccessibilityService.get();
        boolean running = svc != null && svc.overlay() != null && svc.overlay().isRunning();
        btnStop.setVisibility(running ? View.VISIBLE : View.GONE);
    }

    private void confirmDelete(final Task t) {
        new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("删除任务")
                .setMessage("确定删除「" + shortName(t) + "」吗？点位轨迹会一起删除。")
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        store.remove(t.id);
                        renderTasks();
                        toast("已删除");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // --------------------------------------------------------------- 参数

    private void showParamDialog(final Task t) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        final EditText name = nameField(shortName(t));

        box.addView(U.tv(this, "任务名称（最多 4 个字）", 12, U.SUB));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(6);
        nlp.bottomMargin = dp(10);
        box.addView(name, nlp);

        final EditText loop = inputField(box, "双击之间的停留（毫秒）", String.valueOf(t.loopIntervalMs));
        final EditText gap = inputField(box, "双击内两次点击的间隔（毫秒）", String.valueOf(t.doubleGapMs));
        final EditText tap = inputField(box, "单次按压时长（毫秒）", String.valueOf(t.tapDurationMs));

        TextView hint = U.tv(this, "数值越小点击越快。停留过小可能造成手势派发失败，建议 120ms 以上。", 11, 0xFF6F7B96);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        box.addView(hint, hlp);

        scroll.addView(box);

        new AlertDialog.Builder(this, R.style.DialogTheme)
                .setTitle("任务参数")
                .setView(scroll)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String n = name.getText().toString().trim();
                        if (n.length() > NAME_MAX) {
                            n = n.substring(0, NAME_MAX);
                        }
                        if (n.length() > 0) {
                            t.name = n;
                        }
                        t.loopIntervalMs = clamp(parse(loop.getText().toString(), t.loopIntervalMs), 60, 20000);
                        t.doubleGapMs = clamp(parse(gap.getText().toString(), t.doubleGapMs), 20, 2000);
                        t.tapDurationMs = clamp(parse(tap.getText().toString(), t.tapDurationMs), 20, 500);
                        t.updatedAt = System.currentTimeMillis();
                        store.touch();
                        renderTasks();
                        toast("参数已保存");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText inputField(LinearLayout parent, String label, String value) {
        parent.addView(U.tv(this, label, 12, U.SUB));
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(U.TEXT);
        e.setBackgroundResource(R.drawable.bg_input);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(10);
        parent.addView(e, lp);
        return e;
    }

    private int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
