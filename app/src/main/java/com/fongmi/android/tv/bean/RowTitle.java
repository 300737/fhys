package com.fongmi.android.tv.bean;

/**
 * 首页行的标题（电视剧 / 电影 / 综艺 / 动漫 / 短剧）。
 * 之所以单独建一个类型，是因为 String 已经被 ProgressPresenter 占用了，
 * 不能拿字符串当行标题，否则会和「加载中」占位冲突。
 */
public class RowTitle {

    private final String text;

    public RowTitle(String text) {
        this.text = text == null ? "" : text;
    }

    public String getText() {
        return text;
    }
}
