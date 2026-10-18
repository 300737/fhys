package com.github.catvod.spider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 解析/构造器（零依赖，Android 与桌面 JDK 都能跑）。
 * 解析结果：Map&lt;String,Object&gt; / List&lt;Object&gt; / String / Double / Boolean / null。
 */
public final class Json {

    private final String s;
    private int i;

    private Json(String s) {
        this.s = s;
    }

    public static Object parse(String text) {
        if (text == null) return null;
        try {
            Json j = new Json(text.trim());
            Object v = j.value();
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<>();
    }

    public static String str(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.floor(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return String.valueOf(o);
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.toString();
    }

    private Object value() {
        skip();
        if (i >= s.length()) return null;
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't' && s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (c == 'f' && s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (c == 'n' && s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        while (true) {
            skip();
            if (i >= s.length()) break;
            if (s.charAt(i) == '}') { i++; break; }
            if (s.charAt(i) == ',') { i++; continue; }
            String k = string();
            skip();
            if (i < s.length() && s.charAt(i) == ':') i++;
            m.put(k, value());
        }
        return m;
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        while (true) {
            skip();
            if (i >= s.length()) break;
            if (s.charAt(i) == ']') { i++; break; }
            if (s.charAt(i) == ',') { i++; continue; }
            l.add(value());
        }
        return l;
    }

    private String string() {
        skip();
        if (i >= s.length() || s.charAt(i) != '"') return "";
        i++;
        StringBuilder b = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 'r': b.append('\r'); break;
                    case 't': b.append('\t'); break;
                    case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u':
                        if (i + 4 <= s.length()) {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
        return b.toString();
    }

    private Object number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (Exception e) {
            i = start + 1;
            return 0d;
        }
    }

    private void skip() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }
}
