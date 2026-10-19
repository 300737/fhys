package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.net.URLEncoder;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && !TextUtils.isEmpty(DanmakuSetting.getEffectiveApiUrl());
    }

    public static Call newCall(String name, String episode) {
        return newCall(name, episode, null);
    }

    /**
     * 构造弹幕搜索请求。
     *
     * 支持的接口地址占位符：
     * - {url}     当前播放地址（URL 编码后替换），用于「按播放地址匹配」的自建弹幕服务
     * - {name}    影视名称
     * - {episode} 集数
     * 若都不含，则回退为 POST 表单 name/episode。
     */
    public static Call newCall(String name, String episode, String playUrl) {
        return newCall(DanmakuSetting.getEffectiveApiUrl(), name, episode, playUrl);
    }

    /** 按指定接口地址构造请求（弹弹play 回退时要临时换成 ?url= 模板地址）。 */
    private static Call newCall(String url, String name, String episode, String playUrl) {
        OkHttp.cancel(TAG);
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        if (TextUtils.isEmpty(url)) return OkHttp.newCall("about:blank", TAG);
        if (!TextUtils.isEmpty(playUrl) && url.contains("{url}")) {
            return OkHttp.newCall(url.replace("{url}", encode(playUrl)).replace("{name}", name).replace("{episode}", episode), TAG);
        }
        if (url.contains("{name}") || url.contains("{episode}")) {
            return OkHttp.newCall(url.replace("{name}", name).replace("{episode}", episode), TAG);
        } else {
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("name", name);
            params.put("episode", episode);
            return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
        }
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        search(name, episode, null, found);
    }

    /**
     * 弹幕规则入口。地址里带占位符（{url}/{name}/{episode}）就按模板直取；
     * 不带占位符的按「弹弹play 协议」处理：先按片名搜 animeId，再取弹幕，
     * 取不到再回退到模板 / 表单，三条路都试，哪条通走哪条。
     */
    public static void search(String name, String episode, String playUrl, Consumer<Danmaku> found) {
        String api = DanmakuSetting.getEffectiveApiUrl();
        if (TextUtils.isEmpty(api)) {
            Log.d(TAG, "danmaku: 没有可用接口地址，内置默认也没生效？");
            return;
        }
        if (api.contains("{url}") || api.contains("{name}") || api.contains("{episode}")) {
            requestTemplate(name, episode, playUrl, found);
        } else {
            requestDandanplay(api, name, episode, playUrl, found);
        }
    }

    /**
     * 弹弹play（dandanplay）协议：GET /api/v2/search/anime?keyword=片名 拿 animeId，
     * 再 GET /api/v2/comment/{animeId} 拿弹幕本体。
     * 自建服务经常只实现其中一半，所以任一步失败都要往下回退，别直接放弃。
     */
    private static void requestDandanplay(String api, String name, String episode, String playUrl, Consumer<Danmaku> found) {
        String searchUrl = api + "/api/v2/search/anime?keyword=" + encode(Trans.t2s(name));
        Log.d(TAG, "danmaku[dp] step1 search: " + searchUrl);
        OkHttp.newCall(searchUrl, TAG).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                String animeId = "";
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (response.isSuccessful()) animeId = parseAnimeId(body);
                    else Log.d(TAG, "danmaku[dp] step1 failed: HTTP " + response.code());
                } catch (Exception e) {
                    Log.d(TAG, "danmaku[dp] step1 error: " + e);
                }
                if (animeId.isEmpty()) {
                    Log.d(TAG, "danmaku[dp] 没搜到 animeId，回退模板模式");
                    fallback(api, name, episode, playUrl, found);
                    return;
                }
                requestComment(api, animeId, name, episode, playUrl, found);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.d(TAG, "danmaku[dp] step1 request error: " + e.getMessage());
                fallback(api, name, episode, playUrl, found);
            }
        });
    }

    private static void requestComment(String api, String animeId, String name, String episode, String playUrl, Consumer<Danmaku> found) {
        String url = api + "/api/v2/comment/" + animeId;
        Log.d(TAG, "danmaku[dp] step2 comment: " + url);
        OkHttp.newCall(url, TAG).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful() || !body.contains("<d")) {
                        Log.d(TAG, "danmaku[dp] step2 没拿到弹幕: HTTP " + response.code() + " len=" + body.length() + " body=" + body.trim().substring(0, Math.min(60, body.trim().length())));
                        fallback(api, name, episode, playUrl, found);
                        return;
                    }
                    Danmaku item = new Danmaku();
                    item.setName(name + " " + episode);
                    item.setUrl(url);
                    Log.d(TAG, "danmaku[dp] 命中: id=" + animeId + " len=" + body.length());
                    App.post(() -> found.accept(item));
                } catch (Exception e) {
                    Log.d(TAG, "danmaku[dp] step2 error: " + e);
                    fallback(api, name, episode, playUrl, found);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.d(TAG, "danmaku[dp] step2 request error: " + e.getMessage());
                fallback(api, name, episode, playUrl, found);
            }
        });
    }

    /** 弹弹play 走不通时的兜底：有播放地址就换 ?url= 模板再试一次，没有就回老的表单 POST。 */
    private static void fallback(String api, String name, String episode, String playUrl, Consumer<Danmaku> found) {
        String url = TextUtils.isEmpty(playUrl) ? api : api + "/api/v2/comment?url={url}";
        Log.d(TAG, "danmaku[dp] 回退: " + url);
        requestTemplate(url, name, episode, playUrl, found);
    }

    private static String parseAnimeId(String body) {
        try {
            org.json.JSONObject object = new org.json.JSONObject(body);
            org.json.JSONArray animes = object.optJSONArray("animes");
            if (animes == null || animes.length() == 0) return "";
            org.json.JSONObject first = animes.getJSONObject(0);
            return String.valueOf(first.optLong("animeId", 0));
        } catch (Exception e) {
            return "";
        }
    }

    private static void requestTemplate(String name, String episode, String playUrl, Consumer<Danmaku> found) {
        requestTemplate(DanmakuSetting.getEffectiveApiUrl(), name, episode, playUrl, found);
    }

    private static void requestTemplate(String url, String name, String episode, String playUrl, Consumer<Danmaku> found) {
        Call call = newCall(url, name, episode, playUrl);
        Log.d(TAG, "danmaku request: " + call.request().url() + " (playUrl=" + (TextUtils.isEmpty(playUrl) ? "空" : "有") + ")");
        call.enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) {
                        Log.d(TAG, "danmaku search failed: HTTP " + response.code() + " name=" + name + " episode=" + episode);
                        return;
                    }
                    // 兼容「接口直接返回弹幕本体」的弹幕服务。
                    // 标准协议要求返回 [{name,url}] 的弹幕源索引，但不少自建服务直接吐
                    // bilibili 风格 XML。以前这种响应会掉进 gson 解析抛 JsonSyntaxException
                    // 再被吞掉，表现为"一点弹幕都没有"，分不清是没数据还是协议不认。
                    if (isDanmakuXmlBody(body)) {
                        if (!body.contains("<d")) {
                            Log.d(TAG, "danmaku xml has no entries: name=" + name + " episode=" + episode + " url=" + call.request().url());
                            return;
                        }
                        Danmaku item = new Danmaku();
                        item.setName(name);
                        item.setUrl(call.request().url().toString());
                        Log.d(TAG, "danmaku xml source: name=" + name + " episode=" + episode + " len=" + body.length());
                        App.post(() -> found.accept(item));
                        return;
                    }
                    java.util.List<Danmaku> list = Danmaku.arrayFrom(body);
                    if (list.isEmpty()) {
                        Log.d(TAG, "danmaku empty: name=" + name + " episode=" + episode + " bodyLen=" + body.length());
                        return;
                    }
                    list.stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception e) {
                    // 以前这里是 catch(Exception ignored)，请求失败毫无痕迹，根本没法排查
                    Log.d(TAG, "danmaku search error: name=" + name + " episode=" + episode + " " + e);
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                Log.d(TAG, "danmaku request error: name=" + name + " episode=" + episode + " " + e.getMessage());
            }
        });
    }

    /**
     * 判断响应是不是弹幕本体 XML（bilibili 风格 {@code <i><d p="...">text</d></i>}），
     * 而不是本协议要求的弹幕源索引 JSON 数组。
     */
    private static boolean isDanmakuXmlBody(String body) {
        if (TextUtils.isEmpty(body)) return false;
        String head = body.length() > 128 ? body.substring(0, 128) : body;
        head = head.trim().toLowerCase();
        if (!head.startsWith("<?xml") && !head.startsWith("<i")) return false;
        return head.contains("<i") || body.contains("<d");
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
