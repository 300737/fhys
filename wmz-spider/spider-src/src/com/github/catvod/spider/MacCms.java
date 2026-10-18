package com.github.catvod.spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用「苹果CMS V10 / MacCMS」蜘蛛引擎，双模式：
 * <p>
 * 模式一（优先）：JSON API  ——  {host}/api.php/provide/vod/  (ac=list / ac=detail / wd=关键词)
 * 模式二（兜底）：HTML 页面 ——  {host}/index.php/vod/show/id/{tid}/page/{pg}.html
 * <p>
 * 绝大多数「网盘分享站 / 蓝光站」都是这套程序换皮，所以一个引擎可以吃下整片。
 * 子类只需给出默认域名，或由配置里的 ext.site[] / ext.host 覆盖。
 */
public class MacCms extends AbsSpider {

    /** 子类可覆盖：默认域名 */
    protected String defaultHost() {
        return "";
    }

    /** 是否走 HTML 页面模式（网盘站没有开放 JSON API） */
    protected boolean htmlMode() {
        return false;
    }

    protected static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    protected String api() {
        return host + "/api.php/provide/vod/";
    }

    /** 抓 HTML 页面用：Accept 必须是 text/html，否则部分站点会回 JSON 转义字符串 */
    protected Map<String, String> head(String referer) {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        h.put("Accept-Language", "zh-CN,zh;q=0.9");
        h.put("Referer", referer == null || referer.isEmpty() ? host + "/" : referer);
        return h;
    }

    /** 抓 JSON 接口用 */
    protected Map<String, String> headJson() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("User-Agent", UA);
        h.put("Accept", "application/json, text/plain, */*");
        h.put("Referer", host + "/");
        return h;
    }

    /** 少数站点会把整页 HTML 包成一个 JSON 字符串返回，这里解包 */
    protected static String unescape(String s) {
        if (s == null || s.isEmpty()) return "";
        String t = s.trim();
        if (t.startsWith("\"")) {
            Object o = Json.parse(t);
            if (o instanceof String) return (String) o;
        }
        return s;
    }

    protected String getHtml(String url) {
        return unescape(Http.get(url, head(url)));
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        super.init(context, extend);
        String h = pickHost(extend, "");
        if (h.isEmpty()) h = fromExt(extend);
        if (h.isEmpty()) h = defaultHost();
        this.host = h == null ? "" : (h.endsWith("/") ? h.substring(0, h.length() - 1) : h);
        this.header.put("Referer", this.host);
    }

    /** 兼容 ext 直接是字符串域名（含逗号分隔的多域名）的情况 */
    private String fromExt(String extend) {
        if (extend == null) return "";
        String s = extend.trim();
        if (!s.startsWith("http")) return "";
        s = s.split("[,\\s]")[0].replace("\"", "");
        int p = s.indexOf("/api.php/");
        if (p > 0) s = s.substring(0, p);
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ============================ 首页 ============================

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Object> classes = new ArrayList<>();
        List<Object> videos = new ArrayList<>();
        if (host.isEmpty()) return json(emptyHome());
        try {
            if (!htmlMode()) {
                String txt = Http.get(api() + "?ac=list", headJson());
                Map<String, Object> root = Json.obj(Json.parse(txt));
                List<Object> cls = Json.arr(root.get("class"));
                for (Object c : cls) {
                    Map<String, Object> m = Json.obj(c);
                    classes.add(classItem(str(m.get("type_id")), str(m.get("type_name"))));
                }
                if (!classes.isEmpty()) {
                    videos.addAll(listPage(api() + "?ac=detail&pg=1", 24));
                }
            }
            if (classes.isEmpty()) {
                // HTML 模式：从首页/分类页导航抓分类
                String html = getHtml(host + "/index.php/vod/type/id/1.html");
                if (html.length() < 500) html = getHtml(host + "/");
                classes.addAll(navClasses(html));
                videos.addAll(htmlList(html, 24));
                log("home html=" + html.length() + " classes=" + classes.size() + " videos=" + videos.size());
            }
        } catch (Exception e) {
            log("homeContent error: " + e);
        }
        if (classes.isEmpty()) classes.addAll(defaultClasses());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("class", classes);
        out.put("filters", new LinkedHashMap<String, Object>());
        out.put("list", videos);
        return json(out);
    }

    @Override
    public String homeVideoContent() throws Exception {
        List<Object> videos = new ArrayList<>();
        if (host.isEmpty()) return json(wrapList(videos));
        try {
            if (!htmlMode()) {
                videos.addAll(listPage(api() + "?ac=detail&pg=1", 24));
            }
            if (videos.isEmpty()) {
                videos.addAll(htmlList(getHtml(host + "/index.php/vod/type/id/1.html"), 24));
            }
        } catch (Exception e) {
            log("homeVideoContent error: " + e);
        }
        return json(wrapList(videos));
    }

    private Map<String, Object> wrapList(List<Object> list) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("list", list);
        return m;
    }

    private Map<String, Object> emptyHome() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("class", new ArrayList<Object>());
        m.put("filters", new LinkedHashMap<String, Object>());
        m.put("list", new ArrayList<Object>());
        return m;
    }

    /** 站点连不上时的兜底分类，保证 UI 不空 */
    protected List<Object> defaultClasses() {
        List<Object> l = new ArrayList<>();
        l.add(classItem("1", "电影"));
        l.add(classItem("2", "剧集"));
        l.add(classItem("3", "综艺"));
        l.add(classItem("4", "动漫"));
        return l;
    }

    /** 从 HTML 导航抓分类（去重，过滤「更多xxx」） */
    protected List<Object> navClasses(String html) {
        List<Object> out = new ArrayList<>();
        Map<String, String> seen = new LinkedHashMap<>();
        for (String[] g : findAllGroups(html,
                "href=\"(?:/index\\.php)?/vod/type/id/(\\d+)\\.html\"[^>]*>([\\s\\S]{0,200}?)</a>", 2)) {
            String id = g[1];
            String name = strip(g[2]);
            if (id == null || name == null || name.isEmpty()) continue;
            if (name.startsWith("更多") || name.length() > 8) continue;
            if (seen.containsKey(id)) continue;
            seen.put(id, name);
            out.add(classItem(id, name));
        }
        return out;
    }

    // ============================ 分类 ============================

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int p = 1;
        try {
            p = Integer.parseInt(pg);
        } catch (Exception ignored) {
        }
        List<Object> list = new ArrayList<>();
        if (host.isEmpty()) return json(page(p, 1, 0, 0, list));
        try {
            if (!htmlMode()) {
                String txt = Http.get(api() + "?ac=detail&t=" + tid + "&pg=" + p, headJson());
                Map<String, Object> root = Json.obj(Json.parse(txt));
                list.addAll(toVods(Json.arr(root.get("list"))));
                int pageCount = (int) dbl(root.get("pagecount"));
                int total = (int) dbl(root.get("total"));
                if (!list.isEmpty()) return json(page(p, pageCount, list.size(), total, list));
            }
            String html = getHtml(host + "/index.php/vod/show/id/" + tid + "/page/" + p + ".html");
            list.addAll(htmlList(html, -1));
        } catch (Exception e) {
            log("categoryContent error: " + e);
        }
        return json(page(p, Math.max(p, 1), list.size(), 0, list));
    }

    // ============================ 详情 ============================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        if (ids == null || ids.isEmpty() || host.isEmpty()) {
            out.put("list", list);
            return json(out);
        }
        String id = ids.get(0);
        try {
            Map<String, Object> v = null;
            if (!htmlMode()) {
                String txt = Http.get(api() + "?ac=detail&ids=" + id, headJson());
                List<Object> arr = Json.arr(Json.obj(Json.parse(txt)).get("list"));
                if (!arr.isEmpty()) v = fromApi(Json.obj(arr.get(0)));
            }
            if (v == null) v = fromHtml(id);
            if (v != null) list.add(v);
        } catch (Exception e) {
            log("detailContent error: " + e);
        }
        out.put("list", list);
        return json(out);
    }

    private Map<String, Object> fromApi(Map<String, Object> m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("vod_id", str(m.get("vod_id")));
        v.put("vod_name", str(m.get("vod_name")));
        v.put("vod_pic", str(m.get("vod_pic")));
        v.put("type_name", str(m.get("type_name")));
        v.put("vod_year", str(m.get("vod_year")));
        v.put("vod_area", str(m.get("vod_area")));
        v.put("vod_remarks", str(m.get("vod_remarks")));
        v.put("vod_actor", str(m.get("vod_actor")));
        v.put("vod_director", str(m.get("vod_director")));
        v.put("vod_content", strip(str(m.get("vod_content"))));
        String from = str(m.get("vod_play_from"));
        String url = str(m.get("vod_play_url"));
        String[] pair = sortLines(from, url);
        v.put("vod_play_from", pair[0].isEmpty() ? "默认线路" : pair[0]);
        v.put("vod_play_url", pair[1]);
        return v;
    }

    /**
     * 把「直连 m3u8/mp4」的线路排到最前面，用户点开就是能直接播的那条，
     * 不用先踩一遍 qq/iqiyi 的网页解析线路。from 与 url 是平行数组，必须同步换位。
     */
    private String[] sortLines(String from, String url) {
        if (from == null || url == null || from.isEmpty() || url.isEmpty()) return new String[]{from, url};
        if (!from.contains("$$$") && !url.contains("$$$")) return new String[]{from, url};
        String[] fs = from.split("\\$\\$\\$");
        String[] us = url.split("\\$\\$\\$", -1);
        int n = Math.min(fs.length, us.length);
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < n; i++) idx.add(i);
        final String[] uu = us;
        // 稳定排序：直连优先
        java.util.Collections.sort(idx, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                boolean da = isDirect(uu[a]);
                boolean db = isDirect(uu[b]);
                if (da == db) return a.compareTo(b);
                return da ? -1 : 1;
            }
        });
        StringBuilder fb = new StringBuilder();
        StringBuilder ub = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int i = idx.get(k);
            if (k > 0) {
                fb.append("$$$");
                ub.append("$$$");
            }
            fb.append(fs[i]);
            ub.append(us[i]);
        }
        return new String[]{fb.toString(), ub.toString()};
    }

    private boolean isDirect(String group) {
        String g = group.toLowerCase();
        return g.contains(".m3u8") || g.contains(".mp4") || g.contains(".flv") || g.contains(".mkv");
    }

    /** HTML 详情页：优先网盘直链（data-clipboard-text），其次 maccms 播放列表 */
    private Map<String, Object> fromHtml(String id) {
        String url = id.startsWith("http") ? id : host + "/index.php/vod/detail/id/" + id + ".html";
        String html = getHtml(url);
        if (html.length() < 200) return null;

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("vod_id", id);
        v.put("vod_name", firstNonEmpty(
                find(html, "<h1[^>]*>\\s*([^<]{1,60})\\s*</h1>", 1),
                find(html, "<title>\\s*([^<（(【]{1,60})", 1)));
        v.put("vod_pic", find(html, "(?:data-src|data-original|src)=\"(https?://[^\"]+\\.(?:jpg|png|webp))\"", 1));
        v.put("vod_content", strip(find(html, "class=\"[^\"]*(?:module-info-introduction|vod_content|content-desc)[^\"]*\"[^>]*>([\\s\\S]{1,3000}?)</", 1)));
        v.put("vod_remarks", find(html, "class=\"[^\"]*module-info-item-content[^\"]*\"[^>]*>\\s*([^<]{1,30})\\s*<", 1));

        // 1) 网盘直链：<a class="...copy" data-clipboard-text="https://drive.uc.cn/s/xxx" title="复制《片名》第1集下载地址">
        //    同一行通常有「复制」和「下载」两个按钮指向同一链接，需要按 URL 去重
        Map<String, String> pans = new LinkedHashMap<>();
        for (String[] g : findAllGroups(html,
                "data-clipboard-text=\"(https?://[^\"]+)\"([^>]*?)title=\"([^\"]*)\"", 3)) {
            String u = g[1];
            if (u == null || u.isEmpty()) continue;
            if (u.contains("qrcode") || u.contains("javascript")) continue;
            if (pans.containsKey(u)) continue;
            String t = g[3] == null ? "" : g[3];
            pans.put(u, epName(t, pans.size() + 1));
        }
        if (!pans.isEmpty()) {
            StringBuilder b = new StringBuilder();
            int i = 0;
            for (Map.Entry<String, String> e : pans.entrySet()) {
                if (i++ > 0) b.append('#');
                b.append(e.getValue()).append('$').append(e.getKey());
            }
            v.put("vod_play_from", "网盘");
            v.put("vod_play_url", b.toString());
            return v;
        }

        // 2) 常规 maccms 播放列表
        List<String> nids = new ArrayList<>();
        for (String[] g : findAllGroups(html,
                "href=\"(/index\\.php/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html)\"[^>]*>\\s*([^<]{1,24})\\s*<", 2)) {
            nids.add(g[1]);
        }
        if (nids.isEmpty()) {
            for (String[] g : findAllGroups(html,
                    "href=\"(/vod/play/id/\\d+/sid/\\d+/nid/\\d+\\.html)\"[^>]*>\\s*([^<]{1,24})\\s*<", 2)) {
                nids.add(g[1]);
            }
        }
        if (!nids.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < nids.size(); i++) {
                if (i > 0) b.append('#');
                b.append("第").append(i + 1).append("集$").append(abs(host, nids.get(i)));
            }
            v.put("vod_play_from", "网页线路");
            v.put("vod_play_url", b.toString());
            return v;
        }
        v.put("vod_play_from", "网页线路");
        v.put("vod_play_url", "");
        return v;
    }

    /** 从「复制《片名》第1集下载地址」这类 title 里抠出集数 */
    private String epName(String title, int index) {
        String t = title == null ? "" : title.trim();
        int p = t.indexOf('》');
        if (p > 0) t = t.substring(p + 1);
        t = t.replace("下载地址", "").replace("复制", "").replace("下载", "")
                .replace("《", "").replace("》", "").trim();
        if (t.isEmpty()) return "第" + index + "集";
        return t;
    }

    // ============================ 搜索 ============================

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Object> list = new ArrayList<>();
        if (host.isEmpty() || key == null || key.trim().isEmpty()) return json(wrapList(list));
        try {
            if (!htmlMode()) {
                // 必须带 ac=detail，否则苹果CMS 只回精简字段（没有封面）
                String txt = Http.get(api() + "?ac=detail&wd=" + urlEncode(key) + "&pg=" + pg, headJson());
                Map<String, Object> root = Json.obj(Json.parse(txt));
                list.addAll(toVods(Json.arr(root.get("list"))));
            }
            if (list.isEmpty()) {
                String html = getHtml(host + "/index.php/vod/search.html?wd=" + urlEncode(key));
                list.addAll(htmlList(html, -1));
            }
        } catch (Exception e) {
            log("searchContent error: " + e);
        }
        return json(wrapList(list));
    }

    // ============================ 播放 ============================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        String real = "";
        try {
            if (id != null && (id.startsWith("http://") || id.startsWith("https://"))) {
                real = id;
            } else if (id != null && id.startsWith("/")) {
                // maccms 播放页：从 player_aaaa 里挖真实地址
                String page = host + id;
                String html = getHtml(page);
                String body = find(html, "player_aaaa\\s*=\\s*(\\{[\\s\\S]*?\\})\\s*</script>", 1);
                if (!body.isEmpty()) real = str(Json.obj(Json.parse(body)).get("url"));
                if (real.isEmpty()) real = find(html, "data-clipboard-text=\"(https?://[^\"]+)\"", 1);
            } else {
                real = id == null ? "" : id;
            }
        } catch (Exception e) {
            log("playerContent error: " + e);
        }
        boolean direct = isDirect(real);
        m.put("parse", direct ? 0 : 1);   // 网页地址交给宿主嗅探，直连媒体直接播
        m.put("jx", 0);
        m.put("playUrl", "");
        m.put("url", real == null ? "" : real);
        m.put("header", header);
        return json(m);
    }

    // ============================ 解析工具 ============================

    protected List<Object> listPage(String url, int limit) {
        List<Object> out = new ArrayList<>();
        try {
            String txt = Http.get(url, headJson());
            Map<String, Object> root = Json.obj(Json.parse(txt));
            out.addAll(toVods(Json.arr(root.get("list"))));
        } catch (Exception ignored) {
        }
        if (limit > 0 && out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }

    protected List<Object> toVods(List<Object> arr) {
        List<Object> out = new ArrayList<>();
        for (Object o : arr) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = Json.obj(o);
            out.add(vod(str(m.get("vod_id")), str(m.get("vod_name")), str(m.get("vod_pic")), str(m.get("vod_remarks"))));
        }
        return out;
    }

    /**
     * 从 maccms 任意页面抓卡片。
     * <p>
     * 模板千差万别，有的把 &lt;img&gt; 放在 &lt;a&gt; 外面，所以不能按 &lt;/a&gt; 切块，
     * 改成「以每个详情链接为起点、到下一个详情链接为终点」切区间，图片和标题都能带出来。
     */
    protected List<Object> htmlList(String html, int limit) {
        List<Object> out = new ArrayList<>();
        if (html == null || html.isEmpty()) return out;
        Map<String, Boolean> seen = new LinkedHashMap<>();

        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "href=\"(?:/index\\.php)?/vod/detail/id/(\\d+)\\.html\"[^>]*",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);

        List<String> ids = new ArrayList<>();
        List<int[]> spans = new ArrayList<>();
        while (m.find()) {
            ids.add(m.group(1));
            spans.add(new int[]{m.start(), m.end()});
        }
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (seen.containsKey(id)) continue;
            int start = spans.get(i)[0];
            int nextStart = i + 1 < spans.size() ? spans.get(i + 1)[0] : html.length();
            int end = Math.min(nextStart, start + 1000);
            if (end <= start) continue;
            String block = html.substring(start, end);

            String name = firstNonEmpty(
                    find(block, "title=\"([^\"]{1,60})\"", 1),
                    strip(find(block, "<h[34][^>]*>([^<]{1,60})</h[34]>", 1)),
                    strip(find(block, "class=\"[^\"]*(?:module-item-title|title)[^\"]*\"[^>]*>\\s*([^<]{1,60})<", 1)));
            if (name.isEmpty()) continue;

            String pic = firstNonEmpty(
                    find(block, "(?:data-src|data-original|data-echo)=\"(https?://[^\"]+)\"", 1),
                    find(block, "(?:data-src|data-original|data-echo)=\"(/[^\"]+\\.(?:jpg|jpeg|png|webp))\"", 1),
                    find(block, "src=\"(https?://[^\"]+\\.(?:jpg|jpeg|png|webp))\"", 1));
            String remark = strip(find(block, "class=\"[^\"]*(?:module-item-caption|module-item-text|pic-text|module-item-note)[^\"]*\"[^>]*>([^<]{1,24})<", 1));

            seen.put(id, Boolean.TRUE);
            // 封面是空串时不要 abs()，否则会变成 "http://站点/" 这种假图
            out.add(vod(id, name, pic == null || pic.isEmpty() ? "" : abs(host, pic), remark));
            if (limit > 0 && out.size() >= limit) break;
        }
        return out;
    }

    protected static List<String[]> findAllGroups(String html, String regex, int groupCount) {
        List<String[]> out = new ArrayList<>();
        if (html == null) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex,
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String[] g = new String[groupCount + 1];
            for (int i = 1; i <= groupCount && i <= m.groupCount(); i++) g[i] = m.group(i);
            out.add(g);
        }
        return out;
    }

    protected static String str(Object o) {
        return Json.str(o);
    }

    protected static double dbl(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(Json.str(o));
        } catch (Exception e) {
            return 0;
        }
    }

    protected static String firstNonEmpty(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }
}
