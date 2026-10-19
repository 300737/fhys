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
    // 3 线程：首页要同时拉 5 个分类高分榜，单线程会把总耗时拖到 20 秒开外
    private static final java.util.concurrent.ExecutorService EXECUTOR =
            Executors.newFixedThreadPool(3, r -> {
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

    /**
     * 豆瓣 App 用的 rexxar 口碑榜接口（不是网页版 j/new_search_subjects）。
     * 网页版那个接口对机房/盒子 IP 反爬很凶，实测直接返回
     * {"msg":"检测到有异常请求从您的IP发出，请登录再试!"}，四个榜全空；
     * rexxar 这组接口反爬宽松，且本身就是「口碑榜」，天然是高分内容。
     */
    private static final java.util.Map<String, String> COLLECTION = new java.util.HashMap<>();
    private static final String REXXAR = "https://m.douban.com/rexxar/api/v2/subject_collection/%s/items?start=0&count=20&for_mobile=1";
    private static final String REXXAR_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148";

    static {
        COLLECTION.put("电视剧", "tv_chinese_best_weekly");   // 华语口碑剧集榜
        COLLECTION.put("电影", "movie_weekly_best");           // 一周口碑电影榜
        COLLECTION.put("综艺", "show_chinese_best_weekly");    // 华语口碑综艺榜
        COLLECTION.put("动漫", "tv_animation");                // 动画口碑榜
    }

    /** 分类高分榜缓存：key = 豆瓣标签（电视剧/电影/综艺/动漫） */
    private static final java.util.Map<String, List<Vod>> CAT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 按豆瓣分类拉「评分最高」的一批片。
     * sort=S 按评分排序、range=7,10 只取 7 分以上，跨最近三年合并去重，
     * 这样既能覆盖当年新剧，也不至于一年只有两三条。
     */
    /** 分类榜单独用单线程串行拉：4 个分类同时打豆瓣必被限流（实测电影/动漫直接返回空） */
    private static final java.util.concurrent.ExecutorService CAT_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "douban-cat");
                t.setDaemon(true);
                return t;
            });

    /** 抓到空结果的时间戳：豆瓣短时间连打会整批返回空，60 秒内不再重复打 */
    private static final java.util.Map<String, Long> EMPTY_AT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long EMPTY_COOLDOWN = 60 * 1000L;

    /** 每次成功拉到豆瓣数据的时间，用于节流 */
    private static final java.util.Map<String, Long> CAT_AT = new java.util.concurrent.ConcurrentHashMap<>();
    /** 普通读取的缓存有效期 */
    private static final long CACHE_MS = 5 * 60 * 1000L;
    /** 强制刷新时的最小间隔：60 秒内重复进页面不重复打豆瓣，避免被限流 */
    private static final long FORCE_MIN_MS = 60 * 1000L;

    public static void getCategoryList(String tag, int want, HotCallback callback) {
        getCategoryList(tag, want, false, callback);
    }

    /**
     * @param force true = 每次进入搜索页都实时向豆瓣重新拉最新榜（60 秒内不重复打）
     *              false = 5 分钟内存缓存
     *              拉取失败时回退上一次成功的数据，不会让用户看到空白。
     */
    public static void getCategoryList(String tag, int want, boolean force, HotCallback callback) {
        List<Vod> cached = CAT_CACHE.get(tag);
        Long at = CAT_AT.get(tag);
        long now = System.currentTimeMillis();
        boolean hasCache = cached != null && !cached.isEmpty();

        if (force) {
            // 刚拉过一分钟内就不再打豆瓣（连续进出页面会把它打限流）
            if (hasCache && at != null && now - at < FORCE_MIN_MS) {
                List<Vod> copy = new ArrayList<>(cached);
                if (callback != null) App.post(() -> callback.onResponse(copy));
                return;
            }
        } else if (hasCache && at != null && now - at < CACHE_MS) {
            List<Vod> copy = new ArrayList<>(cached);
            if (callback != null) App.post(() -> callback.onResponse(copy));
            return;
        }

        Long emptyAt = EMPTY_AT.get(tag);
        if (!hasCache && emptyAt != null && now - emptyAt < EMPTY_COOLDOWN) {
            if (callback != null) App.post(() -> callback.onResponse(new ArrayList<>()));
            return;
        }
        CAT_EXECUTOR.execute(() -> {
            List<Vod> list = fetchCategoryList(tag, want);
            if (list.isEmpty()) {
                // 大概率是被豆瓣限流，等一下换一次再试
                try { Thread.sleep(700); } catch (InterruptedException ignored) {}
                list = fetchCategoryList(tag, want);
            }
            // lambda 里不能引用被重新赋值的局部变量（not effectively final），先落成常量
            final List<Vod> result = list;
            if (!result.isEmpty()) {
                CAT_CACHE.put(tag, new ArrayList<>(result));
                CAT_AT.put(tag, System.currentTimeMillis());
                EMPTY_AT.remove(tag);
                if (callback != null) App.post(() -> callback.onResponse(result));
            } else {
                EMPTY_AT.put(tag, System.currentTimeMillis());
                // 拉空了就回退上次的榜，别让热搜凭空消失
                List<Vod> fallback = CAT_CACHE.get(tag);
                final List<Vod> out = fallback == null ? new ArrayList<>() : new ArrayList<>(fallback);
                if (callback != null) App.post(() -> callback.onResponse(out));
            }
        });
    }

    public static List<Vod> fetchCategoryList(String tag, int want) {
        List<Vod> list = fetchCollection(tag, want);
        if (!list.isEmpty()) return list;
        return fetchSearchSubjects(tag, want);
    }

    /** rexxar 口碑榜 */
    public static List<Vod> fetchCollection(String tag, int want) {
        List<Vod> list = new ArrayList<>();
        String collection = COLLECTION.get(tag);
        if (collection == null) return list;
        try {
            Request request = new Request.Builder()
                    .url(String.format(REXXAR, collection))
                    .header("User-Agent", REXXAR_UA)
                    .header("Referer", "https://m.douban.com/subject_collection/" + collection)
                    .build();
            try (Response response = OkHttp.client(8000).newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return list;
                JSONObject root = new JSONObject(response.body().string());
                JSONArray items = root.optJSONArray("subject_collection_items");
                if (items == null) return list;
                for (int i = 0; i < items.length() && list.size() < want; i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String title = item.optString("title").trim();
                    if (TextUtils.isEmpty(title)) continue;
                    String cover = "";
                    JSONObject pic = item.optJSONObject("cover");
                    if (pic != null) cover = pic.optString("url", "").trim();
                    if (TextUtils.isEmpty(cover)) cover = item.optString("pic", "").trim();
                    String rate = "";
                    JSONObject rating = item.optJSONObject("rating");
                    if (rating != null) rate = rating.optString("value", "").trim();
                    Vod vod = new Vod();
                    vod.setId(item.optString("id", ""));
                    vod.setName(title);
                    vod.setPic(cover);
                    vod.setRemarks(TextUtils.isEmpty(rate) ? "豆瓣口碑" : "豆瓣 " + rate);
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchCollection " + tag + " exception: " + e.getMessage());
        }
        Log.d(TAG, "fetchCollection " + tag + " size=" + list.size());
        return list;
    }

    /** 网页版搜索榜（rexxar 挂了才走这里，容易被反爬拦，只当兜底） */
    public static List<Vod> fetchSearchSubjects(String tag, int want) {
        List<Vod> list = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        try {
            // 豆瓣没有「短剧」分类，只有「短片」（微电影）；而且短片得走 j/search_subjects，
            // new_search_subjects 对它是空的。这里单独处理，取不到就返回空，上层跳过该行。
            if ("短片".equals(tag)) return fetchShortList(want);
            int year = Calendar.getInstance().get(Calendar.YEAR);
            for (int y = year; y >= year - 1 && list.size() < want; y--) {
                String url = "https://movie.douban.com/j/new_search_subjects?sort=S&range=7,10&tags="
                        + URLEncoder.encode(tag, StandardCharsets.UTF_8)
                        + "&playable=1&start=0&year_range=" + y + "," + y;
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", HOT_UA)
                        .header("Referer", "https://www.douban.com/")
                        .build();
                try (Response response = OkHttp.client(8000).newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) continue;
                    JSONArray data = new JSONObject(response.body().string()).optJSONArray("data");
                    if (data == null) continue;
                    for (int i = 0; i < data.length() && list.size() < want; i++) {
                        JSONObject item = data.optJSONObject(i);
                        if (item == null) continue;
                        String title = item.optString("title").trim();
                        String cover = item.optString("cover").trim();
                        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(cover)) continue;
                        if (!seen.add(title)) continue;
                        Vod vod = new Vod();
                        vod.setId(item.optString("id", ""));
                        vod.setName(title);
                        vod.setPic(cover);
                        String rate = item.optString("rate", "").trim();
                        vod.setRemarks(TextUtils.isEmpty(rate) ? "豆瓣高分" : "豆瓣 " + rate);
                        list.add(vod);
                    }
                }
                // 豆瓣对密集请求敏感，年份之间稍作停顿
                Thread.sleep(300);
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchCategoryList " + tag + " exception: " + e.getMessage());
        }
        Log.d(TAG, "fetchCategoryList " + tag + " size=" + list.size());
        return list;
    }

    /** 豆瓣「短片」榜（短剧兜底数据源）。 */
    public static List<Vod> fetchShortList(int want) {
        List<Vod> list = new ArrayList<>();
        try {
            String url = "https://movie.douban.com/j/search_subjects?type=movie&tag="
                    + URLEncoder.encode("短片", StandardCharsets.UTF_8)
                    + "&sort=recommend&page_limit=20&page_start=0";
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", HOT_UA)
                    .header("Referer", "https://movie.douban.com/")
                    .build();
            try (Response response = OkHttp.client(8000).newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return list;
                JSONArray subjects = new JSONObject(response.body().string()).optJSONArray("subjects");
                if (subjects == null) return list;
                for (int i = 0; i < subjects.length() && list.size() < want; i++) {
                    JSONObject item = subjects.optJSONObject(i);
                    if (item == null) continue;
                    String title = item.optString("title").trim();
                    String cover = item.optString("cover").trim();
                    if (TextUtils.isEmpty(title) || TextUtils.isEmpty(cover)) continue;
                    Vod vod = new Vod();
                    vod.setId(item.optString("id", ""));
                    vod.setName(title);
                    vod.setPic(cover);
                    String rate = item.optString("rate", "").trim();
                    vod.setRemarks(TextUtils.isEmpty(rate) ? "豆瓣短片" : "豆瓣 " + rate);
                    list.add(vod);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "fetchShortList exception: " + e.getMessage());
        }
        Log.d(TAG, "fetchShortList size=" + list.size());
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
