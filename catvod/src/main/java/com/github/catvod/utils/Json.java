package com.github.catvod.utils;

import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Json {

    public static JsonElement parse(String json) {
        try {
            return JsonParser.parseString(json);
        } catch (Throwable e) {
            return new JsonParser().parse(json);
        }
    }

    public static boolean isObj(String text) {
        try {
            if (TextUtils.isEmpty(text)) return false;
            new JSONObject(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isArray(String text) {
        try {
            if (TextUtils.isEmpty(text)) return false;
            new JSONArray(text);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isEmpty(JsonObject obj, String key) {
        if (!obj.has(key)) return true;
        JsonElement element = obj.get(key);
        if (element.isJsonNull()) return true;
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty();
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) return element.getAsString().trim().isEmpty();
        return true;
    }

    public static String safeString(JsonObject obj, String key) {
        try {
            return obj.getAsJsonPrimitive(key).getAsString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static List<String> safeListString(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(safeString(obj, key));
        else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsString());
        return result;
    }

    public static List<JsonElement> safeListElement(JsonObject obj, String key) {
        List<JsonElement> result = new ArrayList<>();
        if (!obj.has(key)) return result;
        if (obj.get(key).isJsonObject()) result.add(obj.get(key).getAsJsonObject());
        else for (JsonElement opt : obj.getAsJsonArray(key)) result.add(opt.getAsJsonObject());
        return result;
    }

    public static JsonObject safeObject(JsonElement element) {
        try {
            if (element.isJsonPrimitive()) element = parse(element.getAsJsonPrimitive().getAsString());
            return element.getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static Map<String, String> toMap(String json) {
        return TextUtils.isEmpty(json) ? null : toMap(parse(json));
    }

    public static Map<String, String> toMap(JsonElement element) {
        Map<String, String> map = new HashMap<>();
        JsonObject object = safeObject(element);
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) map.put(entry.getKey(), safeString(object, entry.getKey()));
        return map;
    }

    /**
     * 以下 obj/str/arr/vod/fromMap/emptyListResult/error 是外部 spider jar 按原始类名直接引用的。
     * <p>
     * 外部 jar 的 ClassLoader 只对 com.github.catvod.spider.** 走 dex 自加载，
     * com.github.catvod.utils.Json 会**父优先**拿到宿主 app 里同名同姓的这个类。
     * 一旦 app 版缺了某个方法，jar 里调用它就会 NoSuchMethodError —— 该 spider 直接变空壳，
     * 表现就是「站点一个分类都出不来」。所以这套签名必须和 jar 侧保持一致，删改前先确认。
     */
    public static String str(Object obj) {
        if (obj == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        String text = obj.toString();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\f') {
                sb.append("\\f");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\"') {
                sb.append("\\\"");
            } else if (c == '\\') {
                sb.append("\\\\");
            } else {
                switch (c) {
                    case '\b':
                        sb.append("\\b");
                        break;
                    case '\t':
                        sb.append("\\t");
                        break;
                    case '\n':
                        sb.append("\\n");
                        break;
                    default:
                        if (c < ' ') sb.append(String.format("\\u%04x", Integer.valueOf(c)));
                        else sb.append(c);
                        break;
                }
            }
        }
        sb.append('\"');
        return sb.toString();
    }

    public static String obj(Object... objArr) {
        if (objArr.length % 2 != 0) throw new IllegalArgumentException("kv 必须成对: " + objArr.length);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < objArr.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append(str(String.valueOf(objArr[i])));
            sb.append(':');
            sb.append(raw(objArr[i + 1]));
        }
        sb.append('}');
        return sb.toString();
    }

    public static String arr(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(list.get(i));
            }
        }
        sb.append(']');
        return sb.toString();
    }

    public static String fromMap(Map<String, ?> map) {
        StringBuilder sb = new StringBuilder("{");
        if (map != null) {
            boolean first = true;
            for (Map.Entry<String, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                sb.append(str(entry.getKey()));
                sb.append(':');
                sb.append(raw(entry.getValue()));
                first = false;
            }
        }
        sb.append('}');
        return sb.toString();
    }

    private static String raw(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Boolean || obj instanceof Number) return obj.toString();
        String text = obj.toString();
        if ("true".equals(text) || "false".equals(text) || "null".equals(text)) return text;
        char first = text.isEmpty() ? (char) 0 : text.charAt(0);
        return (first == '{' || first == '[' || first == '\"') ? text : str(text);
    }

    public static String vod(String id, String name, String pic, String remarks) {
        String cover = pic == null || pic.trim().isEmpty() ? fallbackPic(name) : pic;
        return obj("vod_id", str(id), "vod_name", str(name), "vod_pic", str(cover), "vod_remarks", str(remarks));
    }

    /** 接口没给封面时按片名去自建图床取豆瓣真海报，避免首页一整排裂图、也避免图跟片名对不上 */
    private static String fallbackPic(String name) {
        if (name == null || name.trim().isEmpty()) return "https://img.612526.xyz/api/douban?type=movie&tag=" + 0 + "&i=" + 0;
        try {
            return "https://img.612526.xyz/api/smart?wd=" + java.net.URLEncoder.encode(name.trim(), "UTF-8").replace("+", "%20");
        } catch (Throwable e) {
            return "https://img.612526.xyz/api/smart?wd=" + name.trim();
        }
    }

    public static String emptyListResult(int page) {
        return obj("page", page, "pagecount", 0, "limit", 0, "total", 0, "list", arr(new ArrayList<>()));
    }

    public static String error(String error, String message) {
        return obj("code", 500, "msg", str(String.valueOf(error) + (message == null ? "" : ":" + message)));
    }
}
