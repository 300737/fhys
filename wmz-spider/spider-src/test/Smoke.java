import com.github.catvod.crawler.Spider;

import java.lang.reflect.Constructor;
import java.util.List;

/** 全量冒烟：73 个 csp 类逐个 homeContent，不许抛异常、必须回合法 JSON */
public class Smoke {
    public static void main(String[] args) throws Exception {
        String[] classes = args;
        int ok = 0, bad = 0;
        for (String name : classes) {
            String r = "";
            long t = System.currentTimeMillis();
            try {
                Class<?> c = Class.forName("com.github.catvod.spider." + name);
                Constructor<?> ctor = c.getDeclaredConstructor();
                ctor.setAccessible(true);
                Spider s = (Spider) ctor.newInstance();
                s.init(null, "");
                r = s.homeContent(true);
                boolean valid = r != null && r.trim().startsWith("{") && r.contains("\"class\"");
                long ms = System.currentTimeMillis() - t;
                if (valid) {
                    ok++;
                    System.out.println("OK   " + pad(name) + " " + ms + "ms  len=" + r.length());
                } else {
                    bad++;
                    System.out.println("BADJ " + pad(name) + " " + ms + "ms  -> " + (r == null ? "null" : r.substring(0, Math.min(60, r.length()))));
                }
            } catch (Throwable e) {
                bad++;
                System.out.println("ERR  " + pad(name) + " " + e);
            }
        }
        System.out.println("\n==== ok=" + ok + " bad=" + bad + " total=" + classes.length);
        if (bad > 0) System.exit(1);
    }

    private static String pad(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < 20) b.append(' ');
        return b.toString();
    }
}
