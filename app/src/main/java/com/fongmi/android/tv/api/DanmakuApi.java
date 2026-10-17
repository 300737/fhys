package com.fongmi.android.tv.api;

import android.text.TextUtils;

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
                    Danmaku.arrayFrom(response.body().string()).stream().findFirst().ifPresent(item -> App.post(() -> found.accept(item)));
                } catch (Exception ignored) {
                }
            }
        });
    }

    public static void cancel() {
        OkHttp.cancel(TAG);
    }
}
