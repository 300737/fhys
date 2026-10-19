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
        OkHttp.cancel(TAG);
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        String url = DanmakuSetting.getEffectiveApiUrl();
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

    public static void search(String name, String episode, String playUrl, Consumer<Danmaku> found) {
        newCall(name, episode, playUrl).enqueue(new Callback() {
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
