package com.click.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 任务持久化（内部存储 click_tasks.json） */
public class TaskStore {

    private static final String TAG = "TaskStore";
    private static final String PREF = "click_prefs";
    private static final String KEY_CURRENT = "current_task";
    private static final String KEY_ROUNDS = "repeat_rounds";
    private static final String KEY_BAR_X = "bar_x";
    private static final String KEY_BAR_Y = "bar_y";

    private static TaskStore instance;

    private final File file;
    private final SharedPreferences pref;
    private final List<Task> tasks = new ArrayList<Task>();

    public static synchronized TaskStore get(Context c) {
        if (instance == null) {
            instance = new TaskStore(c.getApplicationContext());
        }
        return instance;
    }

    private TaskStore(Context c) {
        file = new File(c.getFilesDir(), "click_tasks.json");
        pref = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    private void load() {
        tasks.clear();
        if (!file.exists()) {
            return;
        }
        try {
            String s = readFile(file);
            JSONObject root = new JSONObject(s);
            JSONArray arr = root.optJSONArray("tasks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        tasks.add(Task.fromJson(o));
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "load failed", e);
        }
    }

    private static String readFile(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    public synchronized void save() {
        try {
            JSONObject root = new JSONObject();
            JSONArray arr = new JSONArray();
            for (Task t : tasks) {
                arr.put(t.toJson());
            }
            root.put("tasks", arr);
            root.put("current", currentId());
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "save failed", e);
        }
    }

    public synchronized List<Task> tasks() {
        return new ArrayList<Task>(tasks);
    }

    public synchronized Task find(String id) {
        if (id == null) {
            return null;
        }
        for (Task t : tasks) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }

    public synchronized void add(Task t) {
        tasks.add(t);
        save();
    }

    public synchronized void remove(String id) {
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).id.equals(id)) {
                tasks.remove(i);
            }
        }
        if (id != null && id.equals(currentId())) {
            setCurrent(tasks.isEmpty() ? null : tasks.get(0).id);
        }
        save();
    }

    public synchronized void touch() {
        save();
    }

    public String currentId() {
        return pref.getString(KEY_CURRENT, null);
    }

    public void setCurrent(String id) {
        pref.edit().putString(KEY_CURRENT, id).apply();
    }

    public Task current() {
        Task t = find(currentId());
        if (t == null && !tasks.isEmpty()) {
            t = tasks.get(0);
            setCurrent(t.id);
        }
        return t;
    }

    public int rounds() {
        return pref.getInt(KEY_ROUNDS, 1);
    }

    public void setRounds(int r) {
        pref.edit().putInt(KEY_ROUNDS, r).apply();
    }

    public int barX() {
        return pref.getInt(KEY_BAR_X, -1);
    }

    public int barY() {
        return pref.getInt(KEY_BAR_Y, -1);
    }

    public void setBarPos(int x, int y) {
        pref.edit().putInt(KEY_BAR_X, x).putInt(KEY_BAR_Y, y).apply();
    }

    public SharedPreferences prefs() {
        return pref;
    }

    /** 后台常驻（前台服务）默认开启 */
    public boolean keepAlive() {
        return pref.getBoolean("keep_alive", true);
    }

    public void setKeepAlive(boolean on) {
        pref.edit().putBoolean("keep_alive", on).apply();
    }

    /** 生成一个不超过四个字、且不重复的任务名 */
    public synchronized String nextTaskName() {
        int n = tasks.size() + 1;
        for (int guard = 0; guard < 500; guard++, n++) {
            String name = "任务" + n;
            if (name.length() > 4) {
                name = name.substring(0, 4);
            }
            boolean used = false;
            for (Task t : tasks) {
                if (t.name.equals(name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return name;
            }
        }
        return "任务";
    }
}
