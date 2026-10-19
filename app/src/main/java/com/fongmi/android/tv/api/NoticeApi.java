package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 首页跑马灯公告：直接取「繁华影视」后台里配的「跑马灯公告」表，不是把文案写死。
 *
 * 只保留 status=启用 且绑定电视端的记录，按权重降序，内容去重后拼成一条给跑马灯滚动。
 * 后台改了什么，下一次刷新（缓存 5 分钟 / 重启 App）就变什么。
 */
public class NoticeApi {

    private static final String TAG = "NoticeED";
    private static final long CACHE_MS = 5 * 60 * 1000L;

    private static String cached = null;
    private static long cachedAt = 0L;

    /** 取公告。回调已切回主线程。 */
    public static void load(Consumer<String> callback) {
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            callback.accept(cached);
            return;
        }
        BoxApi.table("box_marquee_notice", rows -> {
            String text = parse(rows);
            Log.d(TAG, "notice len=" + text.length());
            if (!TextUtils.isEmpty(text)) {
                cached = text;
                cachedAt = System.currentTimeMillis();
            }
            if (!TextUtils.isEmpty(text)) App.post(() -> callback.accept(text));
        });
    }

    /** 只留启用 + 绑定电视端的公告，按权重降序，内容相同的只留一条。 */
    private static String parse(JSONArray rows) {
        if (rows == null) return "";
        try {
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if (row == null) continue;
                if (!BoxApi.enabled(row) || !BoxApi.matchApp(row)) continue;
                list.add(row);
            }
            Collections.sort(list, (a, b) -> b.optInt("weigh") - a.optInt("weigh"));
            StringBuilder sb = new StringBuilder();
            List<String> seen = new ArrayList<>();
            for (JSONObject row : list) {
                String content = row.optString("content").trim();
                if (content.isEmpty()) continue;
                // 后台常有多条内容一模一样的公告（不同权重各留一份），重复滚动很吵
                if (seen.contains(content)) continue;
                seen.add(content);
                if (sb.length() > 0) sb.append("　　");
                sb.append(content);
            }
            return sb.toString();
        } catch (Throwable e) {
            Log.d(TAG, "parse error: " + e);
            return "";
        }
    }
}
