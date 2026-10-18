package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 彩色圆形八卦（太极）缓冲动画：
 * <ul>
 *   <li>内圈 —— 彩色渐变太极（暖色橙→品红 为阳，冷色青→蓝紫 为阴），顺时针旋转</li>
 *   <li>外环 —— 8 段彩虹弧线（八卦卦位），色相随旋转流动，逆时针旋转</li>
 * </ul>
 * 纯自绘无位图资源，只在可见时刷新，隐藏时零开销。
 */
public class TaijiSpinnerView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (isShown()) {
                angle = (angle + 2.4f) % 360f;
                invalidate();
            }
            postDelayed(this, 16);
        }
    };
    private float angle;
    private int size;

    public TaijiSpinnerView(Context context) {
        this(context, null);
    }

    public TaijiSpinnerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(ticker);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        size = Math.min(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (size <= 0) return;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = size * 0.30f;
        float ring = size * 0.43f;

        // ---- 外环：8 段彩虹弧（八卦），逆时针 ----
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ring * 0.20f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();
        canvas.rotate(-angle, cx, cy);
        for (int i = 0; i < 8; i++) {
            paint.setColor(Color.HSVToColor(new float[]{(i * 45f + angle) % 360f, 0.72f, 1f}));
            canvas.drawArc(cx - ring, cy - ring, cx + ring, cy + ring, i * 45f + 5f, 35f, false, paint);
        }
        canvas.restore();

        // ---- 内圈：彩色太极，顺时针 ----
        canvas.save();
        canvas.rotate(angle, cx, cy);
        paint.setStyle(Paint.Style.FILL);
        // 右半（阳：暖色 橙→品红）
        paint.setShader(new LinearGradient(cx, cy - r, cx, cy + r, 0xFFFFB300, 0xFFFF4081, Shader.TileMode.CLAMP));
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, -90, 180, false, paint);
        // 左半（阴：冷色 青→蓝紫）
        paint.setShader(new LinearGradient(cx, cy - r, cx, cy + r, 0xFF00E5FF, 0xFF7C4DFF, Shader.TileMode.CLAMP));
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 90, 180, false, paint);
        // 上小圆（暖色，咬进左半）+ 下小圆（冷色，咬进右半），构成 S 形鱼身
        paint.setShader(new LinearGradient(cx, cy - r, cx, cy, 0xFFFFB300, 0xFFFF4081, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy - r / 2f, r / 2f, paint);
        paint.setShader(new LinearGradient(cx, cy, cx, cy + r, 0xFF00E5FF, 0xFF7C4DFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy + r / 2f, r / 2f, paint);
        // 两枚鱼眼（对面色系）
        paint.setShader(null);
        paint.setColor(0xFF00E5FF);
        canvas.drawCircle(cx, cy - r / 2f, r / 6f, paint);
        paint.setColor(0xFFFFB300);
        canvas.drawCircle(cx, cy + r / 2f, r / 6f, paint);
        canvas.restore();
    }
}
