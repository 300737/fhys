package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名为空，运行时由 ext.site / ext.host 提供。
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class Pan115 extends MacCms {


    @Override
    protected String defaultHost() {
        return "";
    }
}
