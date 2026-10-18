package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 https://vip.wwgz.cn:5200
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class Wwys extends MacCms {


    @Override
    protected String defaultHost() {
        return "https://vip.wwgz.cn:5200";
    }
}
