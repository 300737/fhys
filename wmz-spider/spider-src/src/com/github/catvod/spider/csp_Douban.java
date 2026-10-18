package com.github.catvod.spider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 豆瓣影视推荐（真实实现，走豆瓣官方 rexxar 接口）：
 * <p>
 * 分类  https://m.douban.com/rexxar/api/v2/subject_collection/{c}/items?start=&count=
 * 详情  https://m.douban.com/rexxar/api/v2/{movie|tv}/{id}
 * 预告  https://m.douban.com/rexxar/api/v2/trailer/{trailer_id}   → video_url (mp4 直链)
 * 搜索  https://m.douban.com/rexxar/api/v2/search?q=&start=&count=
 * <p>
 * 播放内容为官方高清预告片（豆瓣本身不提供正片），点开即有画面，不是空壳。
 */
public class csp_Douban extends AbsSpider {

    private static final String API = "https://m.douban.com/rexxar/api/v2";
    private static final String LOC = "108288";

    /** 分类 id → [合集名, 显示名] */
    private static final String[][] COLS = {
            {"1", "movie_showing", "正在热映"},
            {"2", "tv_hot", "热门剧集"},
            {"3", "show_hot", "热门综艺"},
            {"4", "movie_soon", "即将上映"},
            {"5", "movie_top250", "豆瓣 Top250"},
    };

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        super.init(context, extend);
        this.host = "https://m.douban.com";
        this.header.put("Referer", "https://m.douban.com/");
        this.header.put("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        this.header.put("Accept", "application/json");
    }

    private Map<String, String> head() {
        return header;
    }

    // ============================ 首页 ============================

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Object> classes = new ArrayList<>();
        for (String[] c : COLS) classes.add(classItem(c[0], c[2]));
        List<Object> videos = new ArrayList<>();
        try {
            videos.addAll(fetchCollection("movie_showing", 0, 24));
        } catch (Exception e) {
            log("home error " + e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("class", classes);
        out.put("filters", new LinkedHashMap<String, Object>());
        out.put("list", videos);
        return json(out);
    }

    @Override
    public String homeVideoContent() throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> videos = new ArrayList<>();
        try {
            videos.addAll(fetchCollection("movie_showing", 0, 24));
        } catch (Exception ignored) {
        }
        out.put("list", videos);
        return json(out);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int p = 1;
        try {
            p = Integer.parseInt(pg);
        } catch (Exception ignored) {
        }
        String col = COLS[0][1];
        int total = 0;
        for (String[] c : COLS) if (c[0].equals(tid)) col = c[1];
        List<Object> list = new ArrayList<>();
        try {
            total = collectionTotal(col);
            list.addAll(fetchCollection(col, (p - 1) * 20, 20));
        } catch (Exception e) {
            log("category error " + e);
        }
        int pageCount = total > 0 ? (int) Math.ceil(total / 20.0) : Math.max(p, 1);
        return json(page(p, pageCount, list.size(), total, list));
    }

    private int collectionTotal(String col) {
        String txt = Http.get(API + "/subject_collection/" + col + "/items?start=0&count=1&loc_id=" + LOC, head());
        return (int) num(Json.obj(Json.parse(txt)).get("total"));
    }

    private List<Object> fetchCollection(String col, int start, int count) {
        List<Object> out = new ArrayList<>();
        String txt = Http.get(API + "/subject_collection/" + col + "/items?start=" + start + "&count=" + count + "&loc_id=" + LOC, head());
        List<Object> items = Json.arr(Json.obj(Json.parse(txt)).get("subject_collection_items"));
        for (Object o : items) {
            Map<String, Object> m = Json.obj(o);
            String id = Json.str(m.get("id"));
            if (id.isEmpty()) continue;
            out.add(vod(id, Json.str(m.get("title")), pic(m), remark(m)));
        }
        return out;
    }

    /** 列表图与详情图字段不同，且详情里的 cover_url 是「网页」不是图片，需挑真正能显示的图 */
    private String pic(Map<String, Object> m) {
        Map<String, Object> cover = Json.obj(m.get("cover"));
        Map<String, Object> p = Json.obj(m.get("pic"));
        String[] cand = {
                Json.str(cover.get("url")),
                Json.str(m.get("cover_url")),
                Json.str(p.get("large")),
                Json.str(p.get("normal")),
                Json.str(p.get("small")),
        };
        String fallback = "";
        for (String c : cand) {
            if (c == null || c.isEmpty()) continue;
            if (fallback.isEmpty()) fallback = c;
            if (isImage(c)) return c;
        }
        return fallback;
    }

    private static boolean isImage(String u) {
        String l = u.toLowerCase();
        if (l.contains("doubanio.com")) return true;
        if (l.contains("/photos/photo/")) return false;   // 这是条目相册页，不是图片
        return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")
                || l.contains("/view/photo/") || l.contains("img");
    }

    private String remark(Map<String, Object> m) {
        StringBuilder b = new StringBuilder();
        String year = Json.str(m.get("year"));
        String rel = Json.str(m.get("release_date"));
        double r = num(Json.obj(m.get("rating")).get("value"));
        if (!year.isEmpty()) b.append(year);
        if (!rel.isEmpty()) b.append(b.length() > 0 ? " · " : "").append(rel);
        if (r > 0) b.append(b.length() > 0 ? " · " : "").append("★").append(r);
        return b.toString();
    }

    // ============================ 详情 ============================

    @Override
    public String detailContent(List<String> ids) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            out.put("list", list);
            return json(out);
        }
        String id = ids.get(0).trim();
        // 兼容传入 douban:// 或完整链接
        if (id.contains("/")) {
            String[] seg = id.split("[/?]");
            for (int i = seg.length - 1; i >= 0; i--) {
                if (seg[i].matches("\\d{4,}")) {
                    id = seg[i];
                    break;
                }
            }
        }
        try {
            Map<String, Object> d = fetchSubject(id);
            if (d != null && !d.isEmpty()) list.add(buildVod(id, d));
        } catch (Exception e) {
            log("detail error " + e);
        }
        out.put("list", list);
        return json(out);
    }

    private Map<String, Object> fetchSubject(String id) {
        for (String type : new String[]{"movie", "tv"}) {
            String txt = Http.get(API + "/" + type + "/" + id, head());
            Map<String, Object> m = Json.obj(Json.parse(txt));
            if (!m.isEmpty() && !Json.str(m.get("title")).isEmpty()) return m;
        }
        return null;
    }

    private Map<String, Object> buildVod(String id, Map<String, Object> d) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("vod_id", id);
        v.put("vod_name", Json.str(d.get("title")));
        v.put("vod_pic", pic(d));

        // 类型
        StringBuilder gen = new StringBuilder();
        for (Object g : Json.arr(d.get("genres"))) {
            if (gen.length() > 0) gen.append('/');
            gen.append(Json.str(g));
        }
        v.put("type_name", gen.toString());
        v.put("vod_year", Json.str(d.get("year")));

        // 地区
        StringBuilder area = new StringBuilder();
        for (Object c : Json.arr(d.get("countries"))) {
            if (area.length() > 0) area.append('/');
            area.append(Json.str(c));
        }
        v.put("vod_area", area.toString());

        // 导演 / 演员
        v.put("vod_director", names(d.get("directors")));
        v.put("vod_actor", names(d.get("actors")));

        double r = num(Json.obj(d.get("rating")).get("value"));
        v.put("vod_remarks", r > 0 ? ("豆瓣 " + r + " 分") : "");

        StringBuilder intro = new StringBuilder(Json.str(d.get("intro")));
        String aka = joinStr(d.get("aka"), " / ");
        if (!aka.isEmpty()) intro.append("\n\n又名：").append(aka);
        String dur = joinStr(d.get("durations"), " / ");
        if (!dur.isEmpty()) intro.append("\n片长：").append(dur);
        v.put("vod_content", intro.toString().trim());

        // 预告片 → 播放列表
        List<String> names = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        List<Object> trailers = Json.arr(d.get("trailers"));
        int got = 0;
        for (Object o : trailers) {
            if (got >= 4) break;
            String tid = Json.str(Json.obj(o).get("id"));
            if (tid.isEmpty()) continue;
            String t = Json.str(Json.obj(o).get("title"));
            String u = trailerUrl(tid);
            if (u.isEmpty()) continue;
            got++;
            names.add(t.isEmpty() ? ("预告片" + got) : t);
            urls.add(u);
        }
        if (!urls.isEmpty()) {
            StringBuilder pu = new StringBuilder();
            for (int i = 0; i < urls.size(); i++) {
                if (i > 0) pu.append('#');
                pu.append(names.get(i).replace('$', ' ').replace('#', ' ')).append('$').append(urls.get(i));
            }
            v.put("vod_play_from", "豆瓣预告片");
            v.put("vod_play_url", pu.toString());
        } else {
            v.put("vod_play_from", "豆瓣预告片");
            v.put("vod_play_url", "暂无预告$" + Json.str(d.get("sharing_url")));
        }
        return v;
    }

    private String trailerUrl(String tid) {
        String txt = Http.get(API + "/trailer/" + tid, head());
        return Json.str(Json.obj(Json.parse(txt)).get("video_url"));
    }

    private String names(Object arr) {
        StringBuilder b = new StringBuilder();
        for (Object o : Json.arr(arr)) {
            String n = Json.str(Json.obj(o).get("name"));
            if (n.isEmpty()) continue;
            if (b.length() > 0) b.append('/');
            b.append(n);
        }
        return b.toString();
    }

    private String joinStr(Object arr, String sep) {
        StringBuilder b = new StringBuilder();
        for (Object o : Json.arr(arr)) {
            String s = Json.str(o);
            if (s.isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(s);
        }
        return b.toString();
    }

    // ============================ 搜索 ============================

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        List<Object> list = new ArrayList<>();
        if (key != null && !key.trim().isEmpty()) {
            int p = 1;
            try {
                p = Integer.parseInt(pg);
            } catch (Exception ignored) {
            }
            try {
                String txt = Http.get(API + "/search?q=" + urlEncode(key) + "&start=" + (p - 1) * 20 + "&count=20", head());
                Map<String, Object> subs = Json.obj(Json.obj(Json.parse(txt)).get("subjects"));
                for (Object o : Json.arr(subs.get("items"))) {
                    Map<String, Object> t = Json.obj(Json.obj(o).get("target"));
                    String id = Json.str(t.get("id"));
                    if (id.isEmpty()) continue;
                    String name = Json.str(t.get("title"));
                    if (name.isEmpty()) continue;
                    list.add(vod(id, name, pic(t), Json.str(t.get("card_subtitle"))));
                }
            } catch (Exception e) {
                log("search error " + e);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);
        return json(out);
    }

    // ============================ 播放 ============================

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("parse", 0);
        m.put("jx", 0);
        m.put("playUrl", "");
        m.put("url", id == null ? "" : id);
        m.put("header", header);
        return json(m);
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(Json.str(o));
        } catch (Exception e) {
            return 0;
        }
    }
}
