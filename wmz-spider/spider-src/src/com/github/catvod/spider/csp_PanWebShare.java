package com.github.catvod.spider;

/**
 * 💯木偶｜4K臻享
 * <p>
 * 接「苹果CMS V10」通用引擎，默认域名 https://123.666291.xyz。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_PanWebShare extends MacCms {

    // 备用域名: http://38.76.197.172:12521
    // 备用域名: https://tv.yydsys.top
    // 备用域名: http://sd.sduc.site
    // 备用域名: https://woog.nxog.eu.org
    // 备用域名: http://38.76.197.172:16969

    @Override
    protected String defaultHost() {
        return "https://123.666291.xyz";
    }
}
