package com.fongmi.android.tv.player;

import com.fongmi.android.tv.setting.PlayerSetting;
import com.github.catvod.net.OkHttp;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 秒切线路：起播后提前把备选线路的连接预热好。
 *
 * <p>切换线路时最耗时的往往不是下载数据，而是 DNS 解析 + TCP 握手 + TLS 握手，
 * 在电视上这段延迟可达数百毫秒甚至一两秒。这里在后台对备选线路发起
 * 一个只取 1 字节的 Range 请求，把它建立好的连接留在连接池里。
 *
 * <p>关键点：预热使用的是 {@code OkHttp.player()} —— 与播放器实际取流用的
 * 是同一个客户端实例，因此连接池是共享的，预热成果能被真正复用。
 * 若用独立客户端预热，连接不会共享，等于白做。
 */
public class LinePreloader {

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread thread = new Thread(r, "line-preload");
        thread.setDaemon(true);
        return thread;
    });

    private static final Set<String> WARMED = Collections.synchronizedSet(new HashSet<>());

    /** 预热一组候选地址，已预热过的会自动跳过 */
    public static void warm(List<String> urls) {
        if (!PlayerSetting.isPreloadLine() || urls == null || urls.isEmpty()) return;
        OkHttpClient client = OkHttp.player();
        for (String url : urls) {
            if (url == null || url.isEmpty()) continue;
            if (!WARMED.add(url)) continue;
            EXECUTOR.execute(() -> warm(client, url));
        }
    }

    /** 换剧集/换源时清空记录，避免集合无限增长 */
    public static void clear() {
        WARMED.clear();
    }

    private static void warm(OkHttpClient client, String url) {
        try (Response response = client.newCall(new Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .build())
                .execute()) {
            // 目的只是建立连接并留在连接池，不消费数据
            response.body().close();
        } catch (Exception ignored) {
            // 预热失败不影响播放，切换时走正常握手
        }
    }
}
