package com.fongmi.android.tv.api;

import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 进首页预热：配置加载完成后，后台把"会用得到的那几个"站点的首页内容（分类 + 推荐列表）
 * 预拉一遍缓存在内存里，用户点站点时直接命中缓存，省去现场拉分类的等待。
 * <p>
 * 只预热有限的站点（首页站点优先 + 配置顺序里的头几个），工具类卡片（不可搜索的、
 * 本地/推送）一律跳过——全量预热会把所有 Spider 在配置刚加载完的瞬间一次性造出来，
 * 又慢又容易踩到 jar 还没落盘的窗口。
 * <p>
 * 单线程串行执行：spider 大量使用静态状态（OkHttp 单例、缓存目录等），并发调用容易互相踩。
 * 配置在预热中途被切走（PENDING 置位）时，本轮作废并按最新配置重跑。
 */
public class Prewarm {

    /** 本轮最多预热几个站点（含首页站点），调小即"降温" */
    private static final int MAX_SITES = 6;

    private static final ArrayMap<String, Result> CACHE = new ArrayMap<>();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean PENDING = new AtomicBoolean(false);

    /** 配置加载完成后调用：清掉旧缓存，按新配置重新预热 */
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
                List<Site> targets = pick();
                for (Site site : targets) {
                    // 中途配置被切换：本轮作废，外层 while 会按新配置重来
                    if (PENDING.get()) break;
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

    /** 首页站点优先，其余按配置顺序取到上限为止 */
    private static List<Site> pick() {
        List<Site> sites = VodConfig.get().getSites();
        List<Site> targets = new ArrayList<>();
        if (sites == null || sites.isEmpty()) return targets;
        Site home = VodConfig.get().getHome();
        if (home != null && !home.isEmpty() && !skip(home)) targets.add(home);
        for (Site site : sites) {
            if (targets.size() >= MAX_SITES) break;
            if (skip(site)) continue;
            if (contains(targets, site.getKey())) continue;
            targets.add(site);
        }
        return targets;
    }

    private static boolean skip(Site site) {
        if (site == null || TextUtils.isEmpty(site.getApi())) return true;
        String key = site.getKey();
        if (TextUtils.isEmpty(key)) return true;
        if (key.startsWith("local_") || "push_agent".equals(key)) return true;
        Integer searchable = site.getSearchable();
        // 豆瓣 / 公告 / 配置中心 这类不可搜索的工具卡片，没有预热价值
        return searchable != null && searchable == 0;
    }

    private static boolean contains(List<Site> list, String key) {
        for (Site site : list) if (key.equals(site.getKey())) return true;
        return false;
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
