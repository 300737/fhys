package com.fongmi.android.tv.ui.custom.view;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * 常驻滚动的跑马灯。
 *
 * <p>系统 TextView 的 marquee 只有在 isFocused() 或 isSelected() 为 true 时才会启动动画。
 * 顶栏公告所在的父容器是可聚焦的搜索框，焦点一旦被抢走（或父容器状态变化），
 * 跑马灯就会停住且不会恢复。这里让 isFocused() 恒为 true，
 * 使滚动不再依赖实际焦点，同时仍保留 ellipsize=marquee 的溢出滚动。
 */
public class MarqueeTextView extends AppCompatTextView {

    public MarqueeTextView(Context context) {
        super(context);
        init();
    }

    public MarqueeTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MarqueeTextView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        setMarqueeRepeatLimit(-1); // -1 = marquee_forever
        setSingleLine(true);
        setHorizontalFadingEdgeEnabled(false);
        setSelected(true);
    }

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 布局完成后宽度才确定，此时启动 marquee 才有效
        post(() -> setSelected(true));
    }

    @Override
    protected void onTextChanged(CharSequence s, int start, int before, int count) {
        super.onTextChanged(s, start, before, count);
        if (s != null && s.length() > 0) setSelected(true);
    }
}
