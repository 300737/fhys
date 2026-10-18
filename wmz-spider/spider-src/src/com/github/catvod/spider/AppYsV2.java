package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 http://39.105.18.5:5565
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class AppYsV2 extends MacCms {


    @Override
    protected String defaultHost() {
        return "http://39.105.18.5:5565";
    }
}
