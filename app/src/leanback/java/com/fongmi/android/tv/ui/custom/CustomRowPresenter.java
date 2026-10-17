package com.fongmi.android.tv.ui.custom;

import android.annotation.SuppressLint;

import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.RowPresenter;

import com.fongmi.android.tv.utils.ResUtil;

public class CustomRowPresenter extends ListRowPresenter {

    private final int spacing;
    private final int strategy;

    public CustomRowPresenter(int spacing) {
        this(spacing, FocusHighlight.ZOOM_FACTOR_SMALL);
    }

    @SuppressLint("RestrictedApi")
    public CustomRowPresenter(int spacing, int focusZoomFactor) {
        this(spacing, focusZoomFactor, HorizontalGridView.FOCUS_SCROLL_ITEM);
    }

    public CustomRowPresenter(int spacing, int focusZoomFactor, int strategy) {
        super(focusZoomFactor);
        this.spacing = spacing;
        this.strategy = strategy;
        setShadowEnabled(false);
        setSelectEffectEnabled(false);
        setKeepChildForeground(false);
    }

    @Override
    protected void onBindRowViewHolder(RowPresenter.ViewHolder holder, Object item) {
        super.onBindRowViewHolder(holder, item);
        // 行头（标题）一律隐藏：首页已取消「更新推荐」等行标题，header 为空文本但仍占一行高度
        androidx.leanback.widget.RowHeaderPresenter.ViewHolder header = holder.getHeaderViewHolder();
        if (header != null && header.view != null) header.view.setVisibility(android.view.View.GONE);
    }

    @Override
    @SuppressLint("RestrictedApi")
    protected void initializeRowViewHolder(RowPresenter.ViewHolder holder) {
        super.initializeRowViewHolder(holder);
        ViewHolder vh = (ViewHolder) holder;
        vh.getGridView().setFocusScrollStrategy(strategy);
        vh.getGridView().setHorizontalSpacing(ResUtil.dp2px(spacing));
    }
}