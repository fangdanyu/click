package com.click.autoclicker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一个「连点任务」：一串点位 + 点击节奏 */
public class Task {

    public static class Pt {
        public int x;
        public int y;

        public Pt(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public Pt copy() {
            return new Pt(x, y);
        }
    }

    public String id = UUID.randomUUID().toString();
    public String name = "任务";
    /** 两组双击之间等待的毫秒数（连续双击的频率） */
    public int loopIntervalMs = 600;
    /** 一次双击内两次点击之间的间隔（毫秒） */
    public int doubleGapMs = 90;
    /** 单次点击按压时长（毫秒） */
    public int tapDurationMs = 60;
    public long updatedAt = System.currentTimeMillis();
    public List<Pt> points = new ArrayList<Pt>();

    public Task() {
    }

    public Task(String name) {
        this.name = name;
    }

    public List<Pt> copyPoints() {
        List<Pt> list = new ArrayList<Pt>();
        for (Pt p : points) {
            list.add(p.copy());
        }
        return list;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("loop", loopIntervalMs);
        o.put("gap", doubleGapMs);
        o.put("tap", tapDurationMs);
        o.put("updatedAt", updatedAt);
        JSONArray arr = new JSONArray();
        for (Pt p : points) {
            JSONObject jp = new JSONObject();
            jp.put("x", p.x);
            jp.put("y", p.y);
            arr.put(jp);
        }
        o.put("points", arr);
        return o;
    }

    public static Task fromJson(JSONObject o) {
        Task t = new Task();
        t.id = o.optString("id", UUID.randomUUID().toString());
        t.name = o.optString("name", "任务");
        t.loopIntervalMs = o.optInt("loop", 600);
        t.doubleGapMs = o.optInt("gap", 90);
        t.tapDurationMs = o.optInt("tap", 60);
        t.updatedAt = o.optLong("updatedAt", System.currentTimeMillis());
        JSONArray arr = o.optJSONArray("points");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject jp = arr.optJSONObject(i);
                if (jp == null) {
                    continue;
                }
                t.points.add(new Pt(jp.optInt("x", 0), jp.optInt("y", 0)));
            }
        }
        return t;
    }

    /** 一次完整的双击周期耗时（不含点位之间的停留） */
    public int doubleClickCost() {
        return tapDurationMs * 2 + doubleGapMs;
    }
}
