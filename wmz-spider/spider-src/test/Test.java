import com.github.catvod.crawler.Spider;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * 桌面联调：直接 new 出蜘蛛跑四个接口，验证有数据。
 * 用法： java -cp build/stubs:build/classes:build/test Test csp_AppYsV2 '' home
 */
public class Test {

    public static void main(String[] args) throws Exception {
        String cls = args.length > 0 ? args[0] : "csp_Douban";
        String ext = args.length > 1 ? args[1] : "";
        String mode = args.length > 2 ? args[2] : "all";
        String extra = args.length > 3 ? args[3] : "";

        Class<?> c = Class.forName("com.github.catvod.spider." + cls);
        Constructor<?> ctor = c.getDeclaredConstructor();
        ctor.setAccessible(true);
        Spider s = (Spider) ctor.newInstance();
        s.init(null, ext);

        if (mode.equals("home") || mode.equals("all")) {
            String r = s.homeContent(true);
            p("homeContent", r, 420);
        }
        if (mode.equals("cate") || mode.equals("all")) {
            String tid = extra.isEmpty() ? "1" : extra;
            String r = s.categoryContent(tid, "1", true, null);
            p("categoryContent(" + tid + ")", r, 420);
        }
        if (mode.equals("search") || mode.equals("all")) {
            String k = extra.isEmpty() ? "流浪地球" : extra;
            String r = s.searchContent(k, false);
            p("searchContent(" + k + ")", r, 420);
        }
        if (mode.equals("detail") || mode.equals("all")) {
            List<String> ids = new ArrayList<>();
            ids.add(extra.isEmpty() ? "1" : extra);
            String r = s.detailContent(ids);
            p("detailContent(" + ids + ")", r, 700);
        }
    }

    private static void p(String tag, String s, int n) {
        if (s == null) s = "null";
        if (System.getProperty("full") != null) n = Integer.MAX_VALUE;
        System.out.println("---- " + tag + "  len=" + s.length());
        System.out.println(s.length() > n ? s.substring(0, n) + " ..." : s);
        System.out.println();
    }
}
