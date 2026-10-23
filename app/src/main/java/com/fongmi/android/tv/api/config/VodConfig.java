package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    public static void load(Config config, Callback callback) {
        get().clear().config(config).load(callback);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        this.config = config;
        return this;
    }

    public VodConfig clear() {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        String url = config.getUrl();
        if (url != null && (url.startsWith("file://") || url.startsWith("file:/"))) {
            String path = url.startsWith("file://") ? url.substring(7) : url.substring(6);
            java.io.File file = new java.io.File(path);
            if (!file.isAbsolute()) {
                file = new java.io.File(com.github.catvod.utils.Path.root(), path);
            }
            if (file.isDirectory()) {
                String assembledJson = assembleLocalDirectory(file);
                java.io.File cacheFile = new java.io.File(App.get().getCacheDir(), "local_assembled_config.json");
                writeFile(cacheFile, assembledJson);
                String json = readFile(cacheFile);
                checkJson(config, Json.parse(json).getAsJsonObject());
                return;
            }
        }
        String json = Decoder.getJson(UrlUtil.convert(config.getUrl()), TAG);
        checkJson(config, Json.parse(json).getAsJsonObject());
    }

    private String assembleLocalDirectory(java.io.File dir) {
        com.google.gson.JsonArray sitesArray = new com.google.gson.JsonArray();
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            for (java.io.File f : files) {
                if (f.isFile()) {
                    String name = f.getName();
                    String extName = "";
                    int dotIdx = name.lastIndexOf('.');
                    if (dotIdx > 0) {
                        extName = name.substring(dotIdx).toLowerCase();
                    }
                    if (".py".equals(extName)) {
                        String baseName = name.substring(0, dotIdx);
                        com.google.gson.JsonObject site = new com.google.gson.JsonObject();
                        site.addProperty("key", "local_py_" + baseName);
                        site.addProperty("name", baseName + " (本地Py)");
                        site.addProperty("type", 3);
                        site.addProperty("api", "file://" + f.getAbsolutePath());
                        site.addProperty("ext", "");
                        sitesArray.add(site);
                    } else if (".js".equals(extName)) {
                        String baseName = name.substring(0, dotIdx);
                        com.google.gson.JsonObject site = new com.google.gson.JsonObject();
                        site.addProperty("key", "local_js_" + baseName);
                        site.addProperty("name", baseName + " (本地JS)");
                        site.addProperty("type", 3);
                        site.addProperty("api", "file://" + f.getAbsolutePath());
                        site.addProperty("ext", "");
                        sitesArray.add(site);
                    }
                }
            }
        }
        com.google.gson.JsonObject configJson = new com.google.gson.JsonObject();
        configJson.add("sites", sitesArray);
        return configJson.toString();
    }

    private void writeFile(java.io.File file, String content) throws java.io.IOException {
        java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
        fos.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        fos.close();
    }

    private String readFile(java.io.File file) throws java.io.IOException {
        java.io.FileInputStream fis = new java.io.FileInputStream(file);
        byte[] bytes = new byte[(int) file.length()];
        int read = fis.read(bytes);
        fis.close();
        return new String(bytes, 0, read, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    private void checkJson(Config config, JsonObject object) throws Throwable {
        if (object.has("msg")) {
            throw new Exception(object.get("msg").getAsString());
        } else if (object.has("urls")) {
            parseDepot(config, object);
        } else if (object.has("return")) {
            parseReturn(config, object);
        } else {
            parseConfig(config, object);
        }
    }

    // 动态地址 / 线路聚合：{ "return": [ { "url": "...", "name": "..." } ] }
    // 后台通过该单入口控制实际 box 配置接口（可换源/故障转移），App 取第一条作为真实配置加载。
    private void parseReturn(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("return").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Return urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseDepot(Config config, JsonObject object) throws Throwable {
        List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        if (configs.isEmpty()) throw new Exception("Depot urls is empty");
        load(this.config = configs.get(0));
        Config.delete(config.getUrl());
    }

    private void parseConfig(Config config, JsonObject object) {
        initList(object);
        initLive(config, object);
        initWall(config, object);
        initSite(config, object);
        initParse(config, object);
        config.setLogo(Json.safeString(object, "logo"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
    }

    private void initList(JsonObject object) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        setRules(Rule.arrayFrom(fetchArray(object, "rules")));
        setDoh(Doh.arrayFrom(fetchArray(object, "doh")));
        setFlags(Json.safeListString(object, "flags"));
        setHosts(Json.safeListString(object, "hosts"));
        setAds(Json.safeListString(object, "ads"));
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object) {
        if (Json.isEmpty(object, "wallpaper")) return;
        this.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    /**
     * spider jar 地址兜底：面板/中转下发的配置常把 jar 写成相对路径（./xxx.jar），
     * Decoder 只会按「配置地址」所在域名拼接；若 jar 实际放在站点 api 的目录里（面板服务器上没有），
     * 拼出来的地址就是 404，所有 csp 站点会变成空壳、分类全空。
     * 处理：主地址加载失败时，用配置里各站点 api 的目录（先上一级、再本级）逐个重试同名 jar，
     * 找到第一个能下载到的地址就用它。主地址正常时零开销（直接返回）。
     */
    private String resolveSpider(String spider, JsonObject object) {
        if (TextUtils.isEmpty(spider) || !spider.startsWith("http")) return spider;
        String raw = spider.split(";md5;")[0];
        String file = raw.substring(raw.lastIndexOf('/') + 1);
        if (TextUtils.isEmpty(file)) return spider;
        if (BaseLoader.get().dex(spider) != null) return spider;
        LinkedHashSet<String> bases = new LinkedHashSet<>();
        for (JsonElement e : Json.safeListElement(object, "sites")) {
            if (!e.isJsonObject()) continue;
            String api = Json.safeString(e.getAsJsonObject(), "api");
            int cut = api.lastIndexOf('/');
            if (cut < 8) continue;
            String dir = api.substring(0, cut + 1);
            int parent = dir.lastIndexOf('/', dir.length() - 2);
            if (parent >= 8) bases.add(dir.substring(0, parent + 1));
            bases.add(dir);
        }
        for (String base : bases) {
            String candidate = base + file;
            try {
                okhttp3.Response resp = OkHttp.newCall(candidate).execute();
                boolean ok;
                try {
                    ok = resp.isSuccessful();
                } finally {
                    resp.close();
                }
                if (ok) return candidate;
            } catch (Throwable ignored) {
            }
        }
        return spider;
    }

    private void initSite(Config config, JsonObject object) {
        String spider = resolveSpider(Json.safeString(object, "spider"), object);
        BaseLoader.get().parseJar(spider, true);
        setSites(Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new)));
        List<Site> dbSites = Site.findAll();
        Map<String, Site> items = dbSites.stream().collect(Collectors.toMap(Site::getKey, Function.identity(), (k1, k2) -> k1));
        getSites().forEach(site -> site.sync(items.get(site.getKey())));
        
        // Add Local File System Site
        Site localFileSite = new Site();
        localFileSite.setKey("local_file_system");
        localFileSite.setName(App.get().getString(com.fongmi.android.tv.R.string.home_local));
        localFileSite.setApi("csp_LocalFile");
        localFileSite.setExt("file:///" + com.github.catvod.utils.Path.root().getAbsolutePath());
        localFileSite.setJar(spider);
        localFileSite.setType(3);
        getSites().add(localFileSite);

        List<Site> localNasSites = dbSites.stream()
                .filter(site -> site.getKey() != null && site.getKey().startsWith("local_nas_"))
                .toList();
        for (Site site : localNasSites) {
            if (TextUtils.isEmpty(site.getJar())) {
                site.setJar(spider);
            }
        }
        getSites().addAll(localNasSites);
        setHome(config, getSites().isEmpty() ? new Site() : getSites().stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(getSites().get(0)), false);
    }

    private void initParse(Config config, JsonObject object) {
        setParses(Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new)));
        setParse(config, getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(getParses().get(0)), false);
    }

    public List<Site> getSites() {
        return sites == null ? sites = new ArrayList<>() : sites;
    }

    private void setSites(List<Site> sites) {
        this.sites = sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    private void setDoh(List<Doh> doh) {
        this.doh = doh;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    private void setRules(List<Rule> rules) {
        this.rules = rules;
        RuleConfig.get().invalidate();
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    private void setFlags(List<String> flags) {
        this.flags = flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    private void setAds(List<String> ads) {
        this.ads = ads;
        RuleConfig.get().invalidate();
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        if ("iqiyi".equals(key)) {
            Site iqiyiSite = new Site();
            iqiyiSite.setKey("iqiyi");
            iqiyiSite.setName("爱奇艺首页");
            iqiyiSite.setApi("iqiyi.py");
            iqiyiSite.setType(3);
            return iqiyiSite;
        }
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
