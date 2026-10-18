package com.github.catvod.spider;

    /**
     * 苹果CMS 通用引擎实例。
     * <p>
     * 默认域名 https://www.hjzhencai.top
     * 类名由宿主 JarLoader 按 api 去掉 "csp_" 前缀推导。
     */

public class HJZhenCai extends MacCms {


    @Override
    protected String defaultHost() {
        return "https://www.hjzhencai.top";
    }
}
