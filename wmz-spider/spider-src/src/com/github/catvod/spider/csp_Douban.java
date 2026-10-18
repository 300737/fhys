package com.github.catvod.spider;

/**
 * Douban 的别名类。
 * <p>
 * 本分支宿主 JarLoader 按 {@code "com.github.catvod.spider." + api.split("csp_")[1]}
 * 加载，即 csp_Douban → Douban；此类别名保留，兼容按全名加载的分支。
 */
public class csp_Douban extends Douban {
}
