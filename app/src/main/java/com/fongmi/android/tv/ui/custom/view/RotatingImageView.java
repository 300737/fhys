package com.fongmi.android.tv.ui.custom.view;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

/**
 * 会转的图标：图片由布局指定（首页左上角用的是 ic_fengche），
 * rotation 0→360 匀速循环 = 从左往右顺时针转。
 * 只在前台可见时转，退到后台自动停。
 */
public class RotatingImageView extends AppCompatImageView {

    private ObjectAnimator animator;

    public RotatingImageView(@NonNull Context context) {
        this(context, null);
    }

    public RotatingImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RotatingImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        animator = ObjectAnimator.ofFloat(this, View.ROTATION, 0f, 360f);
        animator.setDuration(2800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
    }

    private void start() {
        if (animator != null && !animator.isStarted()) animator.start();
        else if (animator != null && animator.isPaused()) animator.resume();
    }

    private void stop() {
        if (animator != null && animator.isStarted()) animator.pause();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }
}
