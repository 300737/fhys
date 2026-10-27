package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 「繁华影视」后台（绿豆UI9，http://612526.xyz:9977）的统一接口层。
 *
 * 后台是 SPA + Bearer token 的形态：
 *   POST /api/login  {username,password}  → {"token":"..."}
 *   GET  /api/t/<表名>                      → 该表全部记录（JSON 数组）
 * 所以客户端要先登录拿 token，再按表名取数据。token 放在静态变量里复用，
 * 遇到 401（后台重启/改密码）自动重登一次。
 *
 * 所有回调都在**后台线程**，调用方自己 App.post 到主线程。
 */
public class BoxApi {

    private static final String TAG = "BoxApi";

    /** 后台地址：浏览器里 #/f/box_marquee_notice 那个后台 */
    public static final String BASE = "http://612526.xyz:9977";
    /** 后台账号。改了后台密码这里要跟着改，否则所有后台功能会静默失效。 */
    private static final String USER = "admin";
    private static final String PASS = "admin888";
    /** 绑定应用 ID：繁华影视-电视端 = 10000，手机端 = 10004 */
    public static final String APP_ID = "10000";

    private static String token = "";

    public interface TableCallback {
        void onResult(JSONArray rows);
    }

    /**
     * 取一张表的全部记录。失败/为空都会回调 null，调用方按"没数据"处理即可。
     */
    public static void table(String name, TableCallback callback) {
        if (TextUtils.isEmpty(token)) login(() -> request(name, callback, true), () -> done(callback, null));
        else request(name, callback, true);
    }

    private static void done(TableCallback callback, JSONArray rows) {
        if (callback != null) callback.onResult(rows);
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
        Log.d(TAG, "login");
        OkHttp.newCall(BASE + "/api/login", headers, rb).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String text = response.body() == null ? "" : response.body().string();
                    token = new JSONObject(text).optString("token", "");
                    Log.d(TAG, "login " + (TextUtils.isEmpty(token) ? "failed: " + response.code() : "ok"));
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

    private static void request(String name, TableCallback callback, boolean retry) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        OkHttp.newCall(BASE + "/api/t/" + name, headers).enqueue(new okhttp3.Callback() {
            @Override
            public void onResponse(Call call, Response response) {
                try {
                    String text = response.body() == null ? "" : response.body().string();
                    if (response.code() == 401 && retry) {
                        Log.d(TAG, "table " + name + " 401, relogin");
                        token = "";
                        login(() -> request(name, callback, false), () -> done(callback, null));
                        return;
                    }
                    if (!response.isSuccessful()) {
                        Log.d(TAG, "table " + name + " failed: HTTP " + response.code());
                        done(callback, null);
                        return;
                    }
                    JSONArray rows = new JSONArray(text);
                    Log.d(TAG, "table " + name + " ok: " + rows.length() + " rows");
                    done(callback, rows);
                } catch (Throwable e) {
                    Log.d(TAG, "table " + name + " error: " + e);
                    done(callback, null);
                }
            }

            @Override
            public void onFailure(Call call, java.io.IOException e) {
                Log.d(TAG, "table " + name + " error: " + e.getMessage());
                done(callback, null);
            }
        });
    }

    /** 把后台的相对图片路径补成完整地址（如 /uploads/xxx → http://.../uploads/xxx）。 */
    public static String image(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.startsWith("http")) return path;
        return BASE + (path.startsWith("/") ? path : "/" + path);
    }

    /** 这条记录是否属于电视端。box_app_ids 形如 "10000,10004"。 */
    public static boolean matchApp(JSONObject row) {
        return row != null && row.optString("box_app_ids").contains(APP_ID);
    }

    /** 是否启用。后台里 status 有的表用 "1"/"0"，有的用 "normal"/"hidden"，统一判一下。 */
    public static boolean enabled(JSONObject row) {
        if (row == null) return false;
        String status = row.optString("status", "1");
        return "1".equals(status) || "normal".equalsIgnoreCase(status) || "enabled".equalsIgnoreCase(status);
    }

    public static void post(Runnable runnable) {
        App.post(runnable);
    }
}
