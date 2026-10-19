package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 首页跑马灯公告：直接取「繁华影视」后台（绿豆UI9 管理系统）里配的
 * 「跑马灯公告」表数据，不是把文案写死在代码里。
 *
 * 链路：
 *   1. POST /api/login  {username,password}  → Bearer token
 *   2. GET  /api/t/box_marquee_notice        → 公告列表
 *   3. 只保留 status=1（启用）且 box_app_ids 含电视端 ID(10000) 的，
 *      按 weigh 从大到小排序，content 拼成一条给跑马灯滚动
 *
 * 后台那条跑马灯改了什么，这里下一次刷新就变什么 —— 用户要的就是这个。
 * 失败时上层自动回退到配置 JSON 里的 notice 字段，不会让公告凭空消失。
 */
public class NoticeApi {

    private static final String TAG = "NoticeED";

    /** 繁华影视后台地址（就是浏览器里 #/f/box_marquee_notice 那个后台） */
    private static final String BASE = "http://612526.xyz:9977";
    /** 后台账号。改了后台密码这里要跟着改，否则公告会回退成配置里的 notice。 */
    private static final String USER = "admin";
    private static final String PASS = "123456";
    /** 绑定应用 ID：繁华影视-电视端 = 10000，手机端 = 10004 */
    private static final String APP_ID = "10000";

    private static final long CACHE_MS = 5 * 60 * 1000L;

    private static String token = "";
    private static String cached = null;
    private static long cachedAt = 0L;

    /** 取公告。回调可能发生在后台线程，上层自行 post 到主线程。 */
    public static void load(Consumer<String> callback) {
        if (cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            callback.accept(cached);
            return;
        }
        if (TextUtils.isEmpty(token)) login(() -> fetch(callback, true), () -> Log.d(TAG, "login failed"));
        else fetch(callback, true);
    }

    private static void login(Runnable success, Runnable failure) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        try {
            body.put("username", USER);
            body.put("password", PASS);
        } catch (Exception ignored) {
        }
        RequestBody rb = RequestBody.create(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), MediaType.parse("application/json; charset=utf-8"));
        Log.d(TAG, "login " + BASE + "/api/login");
        OkHttp.newCall(BASE + "/api/login", headers, rb).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String text = response.body() == null ? "" : response.body().string();
                    JSONObject object = new JSONObject(text);
                    token = object.optString("token", "");
                    Log.d(TAG, "login " + (response.isSuccessful() && !token.isEmpty() ? "ok" : "failed: " + response.code()));
                } catch (Throwable e) {
                    Log.d(TAG, "login error: " + e);
                    token = "";
                }
                if (TextUtils.isEmpty(token)) failure.run();
                else success.run();
            }

            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.d(TAG, "login error: " + e.getMessage());
                token = "";
                failure.run();
            }
        });
    }

    private static void fetch(Consumer<String> callback, boolean retry) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        Log.d(TAG, "fetch " + BASE + "/api/t/box_marquee_notice");
        OkHttp.newCall(BASE + "/api/t/box_marquee_notice", headers).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String text = response.body() == null ? "" : response.body().string();
                    if (response.code() == 401 && retry) {
                        // token 过期（后台重启/改过密码），重新登录再来一次
                        Log.d(TAG, "fetch 401, relogin");
                        token = "";
                        login(() -> fetch(callback, false), () -> Log.d(TAG, "relogin failed"));
                        return;
                    }
                    if (!response.isSuccessful()) {
                        Log.d(TAG, "fetch failed: HTTP " + response.code());
                        return;
                    }
                    String result = parse(text);
                    Log.d(TAG, "fetch ok: len=" + result.length() + " text=" + result);
                    cached = result;
                    cachedAt = System.currentTimeMillis();
                    final String out = result;
                    App.post(() -> callback.accept(out));
                } catch (Throwable e) {
                    Log.d(TAG, "fetch error: " + e);
                }
            }

            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.d(TAG, "fetch error: " + e.getMessage());
            }
        });
    }

    /** 只留启用 + 绑定电视端的公告，按权重降序拼成一条。 */
    private static String parse(String body) {
        try {
            JSONArray array = new JSONArray(body);
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                if (!"1".equals(object.optString("status"))) continue;
                String ids = object.optString("box_app_ids");
                if (ids.contains(APP_ID)) list.add(object);
            }
            Collections.sort(list, (a, b) -> b.optInt("weigh") - a.optInt("weigh"));
            StringBuilder sb = new StringBuilder();
            List<String> seen = new ArrayList<>();
            for (JSONObject object : list) {
                String content = object.optString("content").trim();
                if (content.isEmpty()) continue;
                // 后台常有多条内容一模一样的公告（不同权重各留一份），重复滚动很吵，这里去重
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
