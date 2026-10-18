package com.github.catvod.spider;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** 极简 HTTP 客户端（HttpURLConnection，零第三方依赖，支持 gzip / 重定向 / 超时） */
public final class Http {

    private static final String UA = "Mozilla/5.0 (Linux; Android 13; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    public static String get(String url, Map<String, String> headers) {
        return request("GET", url, headers, null);
    }

    public static String post(String url, Map<String, String> headers, String body) {
        return request("POST", url, headers, body);
    }

    private static String request(String method, String url, Map<String, String> headers, String body) {
        HttpURLConnection conn = null;
        try {
            for (int hop = 0; hop < 4; hop++) {
                URL u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestMethod(method);
                conn.setRequestProperty("User-Agent", UA);
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Accept-Encoding", "gzip");
                if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) conn.setRequestProperty(e.getKey(), e.getValue());
                if (body != null) {
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                    conn.getOutputStream().write(body.getBytes("UTF-8"));
                }
                int code = conn.getResponseCode();
                if (code >= 300 && code < 400 && conn.getHeaderField("Location") != null) {
                    url = new URL(u, conn.getHeaderField("Location")).toString();
                    conn.disconnect();
                    continue;
                }
                InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (in == null) return "";
                if ("gzip".equalsIgnoreCase(conn.getContentEncoding())) in = new GZIPInputStream(in);
                String text = read(in, charset(conn.getContentType()));
                conn.disconnect();
                return text;
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return "";
    }

    private static String charset(String contentType) {
        if (contentType != null) {
            int p = contentType.toLowerCase().indexOf("charset=");
            if (p > 0) return contentType.substring(p + 8).trim().replace("\"", "").split(";")[0];
        }
        return "UTF-8";
    }

    private static String read(InputStream in, String charset) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        String text = new String(out.toByteArray(), charset);
        // 很多站声明为 UTF-8 实际是 GBK，反之一但出现大量替换符就换编码重试
        if (text.indexOf('\uFFFD') >= 0 && !"GBK".equalsIgnoreCase(charset)) {
            String alt = new String(out.toByteArray(), "GBK");
            if (alt.indexOf('\uFFFD') < 0) return alt;
        }
        return text;
    }
}
