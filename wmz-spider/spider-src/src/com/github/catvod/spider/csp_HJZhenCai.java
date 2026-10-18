package com.github.catvod.spider;

/**
 * 💯花卷丨4K臻彩
 * <p>
 * 接「苹果CMS V10」通用引擎，默认域名 https://www.hjzhencai.top。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_HJZhenCai extends MacCms {

    @Override
    protected String defaultHost() {
        return "https://www.hjzhencai.top";
    }
}
