package com.fongmi.android.tv.api.loader;

import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;

    private BaseLoader() {
        jarLoader = new JarLoader();
        pyLoader = new PyLoader();
        jsLoader = new JsLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    public void clear() {
        Task.execute(() -> {
            jarLoader.clear();
            pyLoader.clear();
            jsLoader.clear();
        });
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (isPy(api)) return pyLoader.getSpider(key, api, ext);
        else if (isJs(api)) return jsLoader.getSpider(key, api, ext, jar);
        else if (isCsp(api)) return jarLoader.getSpider(key, api, ext, jar);
        else return new SpiderNull();
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (isJs(api)) jsLoader.setRecent(key);
        else if (isPy(api)) pyLoader.setRecent(key);
        else if (isCsp(api)) jarLoader.setRecent(Util.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if ("danmu".equals(params.get("do"))) return proxyDanmu(params);
        if (params.containsKey("siteKey")) return getSpider(params.get("siteKey")).proxy(params);
        if ("js".equals(params.get("do"))) return jsLoader.proxy(params);
        if ("py".equals(params.get("do"))) return pyLoader.proxy(params);
        return jarLoader.proxy(params);
    }

    private static final byte[] EMPTY_DANMU = "<?xml version=\"1.0\" encoding=\"utf-8\"?><i></i>".getBytes();

    /**
     * 处理站点弹幕代理请求：/proxy?do=danmu&url=<播放地址>
     * 站点把弹幕地址指向本地代理，这里转发到配置的弹幕接口并透传结果。
     * 配置接口若返回 [{name,url}] 列表则取第一项再取弹幕内容；若直接返回弹幕体则原样透传。
     */
    private Object[] proxyDanmu(Map<String, String> params) {
        String url = params.get("url");
        String api = DanmakuSetting.getEffectiveApiUrl();
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(api)) return danmuXml(EMPTY_DANMU);
        try {
            String encoded = URLEncoder.encode(url, "UTF-8");
            String search = api;
            if (api.contains("{url}")) {
                search = api.replace("{url}", encoded);
            } else if (!api.contains("{name}") && !api.contains("{episode}") && !api.contains("url=")) {
                search = api + (api.contains("?") ? "&" : "?") + "url=" + encoded;
            }
            okhttp3.Response response = OkHttp.newCall(search).execute();
            String body = response.body() == null ? "" : response.body().string();
            response.close();
            String trim = body == null ? "" : body.trim();
            if (trim.startsWith("[")) {
                List<Danmaku> items = Danmaku.arrayFrom(body);
                if (!items.isEmpty()) {
                    String target = items.get(0).getRealUrl();
                    if (!TextUtils.isEmpty(target)) {
                        okhttp3.Response r2 = OkHttp.newCall(target).execute();
                        byte[] data = r2.body() == null ? EMPTY_DANMU : r2.body().bytes();
                        r2.close();
                        return danmuXml(data);
                    }
                }
                return danmuXml(EMPTY_DANMU);
            }
            if (!trim.isEmpty()) return danmuXml(body.getBytes("UTF-8"));
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return danmuXml(EMPTY_DANMU);
    }

    private Object[] danmuXml(byte[] data) {
        return new Object[]{200, "text/xml; charset=utf-8", new ByteArrayInputStream(data)};
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Util.md5(jar);
        jarLoader.parseJar(key, jar);
        if (recent) jarLoader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
