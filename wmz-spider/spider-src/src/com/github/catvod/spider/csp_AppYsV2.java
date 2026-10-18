package com.github.catvod.spider;

/**
 * 🎬电影天堂丨蓝光
 * <p>
 * 接「苹果CMS V10」通用引擎，默认域名 http://39.105.18.5:5565。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_AppYsV2 extends MacCms {

    @Override
    protected String defaultHost() {
        return "http://39.105.18.5:5565";
    }
}
