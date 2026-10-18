package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 http://v.rbotv.cn
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class AppRJ extends MacCms {


    @Override
    protected String defaultHost() {
        return "http://v.rbotv.cn";
    }
}
