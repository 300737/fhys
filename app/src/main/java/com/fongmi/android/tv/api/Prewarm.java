package com.fongmi.android.tv.api;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进首页预热：配置加载完成后，后台把所有站点的首页内容（分类 + 推荐列表）逐个预拉一遍，
 * 缓存在内存里。用户点站点进详情页时直接命中缓存，省去现场拉分类的等待。
 * <p>
 * 用单线程串行执行：spider 大量使用静态状态（OkHttp 单例、缓存目录等），并发调用容易互相踩；
 * 预热本身在后台跑，慢一点无所谓。
 * <p>
 * 配置可能在预热中途被切换（PENDING 置位），执行循环发现后立即中止本轮、
 * 重新按最新配置跑，保证缓存的永远是当前配置的内容。
 */
public class Prewarm {

    private static final ArrayMap<String, Result> CACHE = new ArrayMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean PENDING = new AtomicBoolean(false);

    /** 配置加载完成后调用：清掉旧缓存，按新配置重新预热全部站点 */
    public static void start() {
        synchronized (CACHE) {
            CACHE.clear();
        }
        PENDING.set(true);
        if (RUNNING.compareAndSet(false, true)) EXECUTOR.execute(Prewarm::run);
    }

    private static void run() {
        try {
            do {
                PENDING.set(false);
                List<Site> sites = VodConfig.get().getSites();
                if (sites == null || sites.isEmpty()) break;
                for (Site site : sites) {
                    // 中途配置被切换：本轮作废，外层 while 会按新配置重来
                    if (PENDING.get()) break;
                    if (TextUtils.isEmpty(site.getApi())) continue;
                    try {
                        Result result = SiteApi.homeContent(site);
                        if (result != null && (!result.getTypes().isEmpty() || !result.getList().isEmpty()) && !PENDING.get()) {
                            synchronized (CACHE) {
                                CACHE.put(site.getKey(), result);
                            }
                        }
                    } catch (Throwable e) {
                        // 单站预热失败不影响其它站，用户点进去会走正常加载
                    }
                }
            } while (PENDING.get());
        } finally {
            RUNNING.set(false);
        }
        // 收尾期间又有新的预热请求：接过来继续跑，避免请求丢失
        if (PENDING.get() && RUNNING.compareAndSet(false, true)) EXECUTOR.execute(Prewarm::run);
    }

    /** 取某站点的预热结果，没有则返回 null（走正常加载） */
    public static Result get(String key) {
        if (TextUtils.isEmpty(key)) return null;
        synchronized (CACHE) {
            return CACHE.get(key);
        }
    }

    /** 配置切换/清空时作废全部预热缓存 */
    public static void clear() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }
}
