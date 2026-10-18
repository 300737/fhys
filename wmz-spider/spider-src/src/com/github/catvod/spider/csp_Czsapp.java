package com.github.catvod.spider;

/**
 * 🏪厂长｜蓝光影视
 * <p>
 * 接「苹果CMS V10」通用引擎，默认域名 https://cz01.vip。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_Czsapp extends MacCms {

    @Override
    protected String defaultHost() {
        return "https://cz01.vip";
    }
}
