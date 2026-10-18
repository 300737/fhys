package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 http://103.217.190.91:19987/app/bn
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class App99 extends MacCms {

    // 备用域名: http://124.221.3.182:19987/app/bn

    @Override
    protected String defaultHost() {
        return "http://103.217.190.91:19987/app/bn";
    }
}
