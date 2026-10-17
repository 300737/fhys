package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;

public class Douban {

    private static final String TAG = "Douban";

    /**
     * 固定线程池：原实现每次调用都 newSingleThreadExecutor() 且从不 shutdown()，
     * 切站点时线程池不断累积。豆瓣服务器不可达时每个请求要阻塞到 OkHttp 默认
     * 30 秒超时，后续站点全部排队等待 —— 表现为「只有第一个站点能出图，后面都加载不出」。
     * 改为共享单线程池，超时也收紧到 3 秒，避免长时间占位。
     */
    private static final java.util.concurrent.ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "douban-fetch");
                t.setDaemon(true);
                return t;
            });

    /** 豆瓣图服务为局域网自建，超时无需 30 秒这么久，3 秒足够 */
    private static final long TIMEOUT_MS = 3000;

    /**
     * 豆瓣官方公开榜单接口（无需 apikey，与 TVBoxOS 等分支同源）。
     * 用于首页「豆瓣推荐」：拉取当年热门影视，作为推荐兜底数据源。
     */
    private static final String HOT_URL =
            "https://movie.douban.com/j/new_search_subjects?sort=U&range=0,10&tags=&playable=1&start=0&year_range=";

    private static final String HOT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

    public interface Callback {
        void onResponse(String bannerUrl);
    }

    public interface HotCallback {
        void onResponse(List<Vod> list);
    }

    /**
     * 每个站点独立缓存的豆瓣榜单。
     * key = 站点 key：豆瓣数据必须按站点隔离，绝不能所有站点共用一份。
     */
    private static final java.util.Map<String, List<Vod>> HOT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** 异步拉取某个站点的豆瓣热门（当年 + 去年），结果在主线程回调；失败返回空列表 */
    public static void getHotList(Site site, HotCallback callback) {
        String siteKey = siteKey(site);
        List<Vod> cached = HOT_CACHE.get(siteKey);
        if (cached != null && !cached.isEmpty()) {
            List<Vod> copy = new ArrayList<>(cached);
            if (callback != null) App.post(() -> callback.onResponse(copy));
            return;
        }
        EXECUTOR.execute(() -> {
            List<Vod> list = fetchHotList(site);
            if (!list.isEmpty()) HOT_CACHE.put(siteKey, new ArrayList<>(list));
            if (callback != null) App.post(() -> callback.onResponse(list));
        });
    }

    /** 兼容旧调用（无站点上下文） */
    public static void getHotList(HotCallback callback) {
        getHotList(null, callback);
    }

    public static String siteKey(Site site) {
        if (site == null) return "";
        String key = site.getKey();
        return key == null ? "" : key;
    }

    public static List<Vod> fetchHotList(Site site) {
        List<Vod> list = new ArrayList<>();
        try {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            for (int y = year; y >= year - 1 && list.size() < 12; y--) {
                // 关键：把站点 ID 带进请求，服务端可按站点返回各自的榜单
                String siteParam = "&site=" + URLEncoder.encode(siteKey(site), StandardCharsets.UTF_8);
                Request request = new Request.Builder()
                        .url(HOT_URL + y + "," + y + siteParam)
                        .header("User-Agent", HOT_UA)
                        .header("Referer", "https://www.douban.com/")
                        .build();
                try (Response response = OkHttp.client(8000).newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;
                    JSONArray data = new JSONObject(response.body().string()).optJSONArray("data");
                    if (data == null) continue;
                    for (int i = 0; i < data.length() && list.size() < 12; i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item == null) continue;
                        String title = item.optString("title").trim();
                        String cover = item.optString("cover").trim();
                        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(cover)) continue;
                        Vod vod = new Vod();
                        vod.setId(item.optString("id", ""));
                        vod.setName(title);
                        vod.setPic(cover);
                        // 与影视仓对齐：卡片角标显示「豆瓣热播 + 评分」，评分来自榜单的 rate 字段
                        String rate = item.optString("rate", "").trim();
                        vod.setRemarks(TextUtils.isEmpty(rate) ? "豆瓣热播" : "豆瓣热播 " + rate);
                        list.add(vod);
                    }
                }
            }
            Log.d(TAG, "fetchHotList site=" + siteKey(site) + " size=" + list.size());
        } catch (Exception e) {
            Log.e(TAG, "fetchHotList exception: " + e.getMessage(), e);
        }
        return list;
    }

    public static void getBanner(String name, String year, Callback callback) {
        getBanner(name, year, null, callback);
    }

    /** 带站点的横图：同一部片在不同站点可能有不同的图源，请求时把站点 ID 一起带上 */
    public static void getBanner(String name, String year, Site site, Callback callback) {
        getImage(name, year, "horizontal", site, callback);
    }

    public static void getPoster(String name, String year, Callback callback) {
        getImage(name, year, "vertical", null, callback);
    }

    public static void getImage(String name, String year, String aspectType, Callback callback) {
        getImage(name, year, aspectType, null, callback);
    }

    public static void getImage(String name, String year, String aspectType, Site site, Callback callback) {
        if (TextUtils.isEmpty(name)) {
            if (callback != null) App.post(() -> callback.onResponse(""));
            return;
        }
        EXECUTOR.execute(() -> {
            String imgUrl = fetchBanner(name, year, aspectType, site);
            if (callback != null) {
                App.post(() -> callback.onResponse(imgUrl));
            }
        });
    }

    public static String cleanTitle(String name) {
        if (name == null) return "";
        String title = name.replaceAll("【[^】]*】|\\[[^\\]]*\\]|（[^）]*）|\\([^\\)]*\\)", "")
                .replaceAll("(?i)\\b(4K|60帧|HD|BD|1080P|720P|4K60帧|国语|粤语|中字|双语|加长版|未删减版)\\b", "")
                .replaceAll("第[0-9一二三四五六七八九十\\s]+[集话季期].*", "")
                .replaceAll("(?i)\\b(EP?|E)\\s*\\d+.*", "")
                .replaceAll("\\s+\\d+$", "")
                .trim();
        return title;
    }

    public static String fetchBanner(String name, String year) {
        return fetchBanner(name, year, "horizontal", null);
    }

    public static String fetchBanner(String name, String year, String aspectType) {
        return fetchBanner(name, year, aspectType, null);
    }

    public static String fetchBanner(String name, String year, String aspectType, Site site) {
        try {
            String cleanName = cleanTitle(name);
            String serverUrl = com.fongmi.android.tv.setting.Setting.getDouban();
            if (TextUtils.isEmpty(serverUrl)) {
                Log.d(TAG, "Douban serverUrl is empty in Settings");
                return null;
            }
            if (serverUrl.endsWith("/")) serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
            String typeParam = TextUtils.isEmpty(aspectType) ? "horizontal" : aspectType;
            Log.d(TAG, "fetchBanner via Docker API (" + serverUrl + ") type=" + typeParam + " for: " + cleanName);
            // 站点 ID 一并下发，自建服务可以按站点返回各自的豆瓣数据
            String siteParam = "&site=" + URLEncoder.encode(siteKey(site), StandardCharsets.UTF_8);
            String apiUrl = serverUrl + "/api/search?q=" + URLEncoder.encode(cleanName, StandardCharsets.UTF_8) + "&type=" + typeParam + siteParam;

            Request request = new Request.Builder().url(apiUrl).build();
            // 关键：用短超时客户端，避免服务器不可达时占满 OkHttp 默认 30 秒连接池，
            // 导致后续站点请求排队。局域网服务正常响应在 1 秒内。
            try (Response response = OkHttp.client(TIMEOUT_MS).newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonStr = response.body().string();
                    Log.d(TAG, "Docker API response: " + jsonStr);
                    JSONObject json = new JSONObject(jsonStr);
                    if (json.has("img_url")) {
                        String imgUrl = json.optString("img_url");
                        if (!TextUtils.isEmpty(imgUrl)) {
                            Log.d(TAG, "SUCCESS! Received imgUrl from Docker: " + imgUrl);
                            return imgUrl;
                        }
                    }
                } else {
                    Log.d(TAG, "Docker API error code: " + response.code());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchBanner via Docker API exception: " + e.getMessage(), e);
        }
        return null;
    }
}
