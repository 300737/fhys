package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自建蜘蛛基类：HTTP / HTML / JSON 小工具 + 统一输出格式。
 * 所有 csp_* 类继承它，宿主按 JSON 里的 api 字段反射实例化。
 */
public abstract class AbsSpider extends Spider {

    protected String host = "";
    protected Map<String, String> header = new LinkedHashMap<>();
    protected String ext = "";

    protected void log(String msg) {
        System.out.println("[" + getClass().getSimpleName() + "] " + msg);
    }

    /** 输出 JSON（手写序列化，避免依赖 org.json/gson） */
    protected String json(Object o) {
        StringBuilder b = new StringBuilder(1024);
        write(b, o);
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private void write(StringBuilder b, Object o) {
        if (o == null) {
            b.append("null");
        } else if (o instanceof String) {
            b.append('"').append(Json.esc((String) o)).append('"');
        } else if (o instanceof Number || o instanceof Boolean) {
            b.append(o);
        } else if (o instanceof Map) {
            b.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) b.append(',');
                first = false;
                b.append('"').append(Json.esc(e.getKey())).append("\":");
                write(b, e.getValue());
            }
            b.append('}');
        } else if (o instanceof List) {
            b.append('[');
            boolean first = true;
            for (Object v : (List<Object>) o) {
                if (!first) b.append(',');
                first = false;
                write(b, v);
            }
            b.append(']');
        } else {
            b.append('"').append(Json.esc(String.valueOf(o))).append('"');
        }
    }

    /** 标准 vod 条目 */
    protected Map<String, Object> vod(String id, String name, String pic, String remarks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vod_id", id == null ? "" : id);
        m.put("vod_name", name == null ? "" : name);
        m.put("vod_pic", pic == null ? "" : pic);
        m.put("vod_remarks", remarks == null ? "" : remarks);
        return m;
    }

    protected Map<String, Object> page(int pg, int pageCount, int limit, int total, List<Object> list) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", pg);
        m.put("pagecount", Math.max(pageCount, pg));
        m.put("limit", limit <= 0 ? list.size() : limit);
        m.put("total", total <= 0 ? list.size() : total);
        m.put("list", list);
        return m;
    }

    protected Map<String, Object> classItem(String id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type_id", id);
        m.put("type_name", name);
        return m;
    }

    // ---------- 文本工具 ----------

    protected static String abs(String host, String url) {
        if (url == null) return "";
        url = url.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return "https:" + url;
        if (url.startsWith("/")) return host + url;
        return host + "/" + url;
    }

    protected static String strip(String html) {
        if (html == null) return "";
        String t = html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "")
                .replaceAll("(?s)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
        return t.replaceAll("[\\s\\u00a0]+", " ").trim();
    }

    protected static List<String> findAll(String html, String regex, int group) {
        List<String> out = new ArrayList<>();
        if (html == null) return out;
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) out.add(m.group(group == 0 ? 1 : group));
        return out;
    }

    protected static String find(String html, String regex, int group) {
        if (html == null) return "";
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return m.find() ? (m.groupCount() >= group ? m.group(group) : "") : "";
    }

    protected static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    /** 从 ext.site 数组里取第一个可用域名 */
    protected String pickHost(String extend, String fallback) {
        try {
            Object o = Json.parse(extend);
            if (o instanceof Map) {
                Object site = Json.obj(o).get("site");
                if (site instanceof List) {
                    List<Object> list = Json.arr(site);
                    if (!list.isEmpty()) {
                        String h = Json.str(list.get(0)).trim();
                        if (!h.isEmpty()) return h.endsWith("/") ? h.substring(0, h.length() - 1) : h;
                    }
                }
                Object h = Json.obj(o).get("host");
                if (h != null && !Json.str(h).isEmpty()) return Json.str(h);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    @Override
    public void init(android.content.Context context, String extend) throws Exception {
        this.ext = extend == null ? "" : extend;
        this.header.put("Referer", host);
    }
}
