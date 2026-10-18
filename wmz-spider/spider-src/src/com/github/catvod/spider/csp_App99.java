package com.github.catvod.spider;

/**
 * 🥰胖妞丨蓝光秒播
 * <p>
 * 接「苹果CMS V10」通用引擎，默认域名 http://103.217.190.91:19987/app/bn。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_App99 extends MacCms {

    // 备用域名: http://124.221.3.182:19987/app/bn

    @Override
    protected String defaultHost() {
        return "http://103.217.190.91:19987/app/bn";
    }
}
