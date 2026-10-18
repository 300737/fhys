package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 https://123.666291.xyz
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class PanWebShare extends MacCms {

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
