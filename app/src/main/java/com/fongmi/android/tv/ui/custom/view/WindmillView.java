package com.fongmi.android.tv.ui.custom.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 首页左上角的小风车：纯代码绘制（6 片彩色叶+中心轴），顺时针匀速旋转。
 * 尺寸由布局给（28dp 左右），不会挤到旁边的按钮。
 */
public class WindmillView extends View {

    private static final int[] COLORS = {
            0xFFF5A623, // 橙
            0xFF7CB342, // 绿
            0xFFFDD835, // 黄
            0xFF29B6F6, // 青
            0xFF1E88E5, // 蓝
            0xFFE53935, // 红
    };
    private static final int SEGMENT = 360 / COLORS.length;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float angle;
    private ValueAnimator animator;

    public WindmillView(Context context) {
        this(context, null);
    }

    public WindmillView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f;
        // 每片留一点缝隙，看得出是几片叶子而不是一个圆饼
        float sweep = SEGMENT - 8f;

        canvas.save();
        canvas.rotate(angle, cx, cy);
        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        for (int i = 0; i < COLORS.length; i++) {
            paint.setColor(COLORS[i]);
            canvas.drawArc(oval, i * SEGMENT, sweep, true, paint);
        }
        canvas.restore();

        // 中心轴：深色小圆 + 白点，旋转时不会糊成一团
        paint.setColor(0xFF202020);
        canvas.drawCircle(cx, cy, radius * 0.20f, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, radius * 0.08f, paint);
    }

    private void start() {
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 360f);
        animator.setDuration(2600);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 只在页面上才转，退到后台停掉，电视盒子上别空转耗资源
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }
}
