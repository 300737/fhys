package com.github.catvod.spider;

/**
 * 🔍觅盘｜网盘搜索
 * <p>
 * 配置里没有下发域名，接口交由通用引擎按 ext.site / ext.host 动态取；
 * 只要在 ext 里补上域名即可直接可用，无需改 jar。
 * <p>
 * 类名必须与配置 JSON 中 sites[].api 完全一致（一个萝卜一个坑），
 * 否则宿主反射实例化会直接报 ClassNotFoundException。
 */
public class csp_Mipansou extends MacCms {

    @Override
    protected String defaultHost() {
        return "";
    }
}
