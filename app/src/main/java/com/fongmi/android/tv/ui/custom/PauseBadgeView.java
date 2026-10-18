package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * 暂停徽章底盘（彩虹表盘）：
 * 暗色半透明圆面 + 彩虹渐变外环（四段暖→冷渐变拼接成整环）+ 8 向刻度线。
 * 暂停/快进图标与时间由布局内的兄弟视图叠加，本控件只负责盘面。
 */
public class PauseBadgeView extends View {

    private static final int[][] QUAD = {
            {0xFFFFB300, 0xFFFF4081},
            {0xFFFF4081, 0xFF7C4DFF},
            {0xFF7C4DFF, 0xFF00E5FF},
            {0xFF00E5FF, 0xFFFFB300}
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public PauseBadgeView(Context context) {
        this(context, null);
    }

    public PauseBadgeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = Math.min(cx, cy) - 4f;
        if (r <= 0) return;

        // 暗色盘面
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF0101825);
        canvas.drawCircle(cx, cy, r - 3, paint);

        // 彩虹外环：四个象限各自起止色渐变，拼成整环
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < 4; i++) {
            double a0 = Math.toRadians(i * 90);
            double a1 = Math.toRadians(i * 90 + 90);
            paint.setShader(new LinearGradient(
                    (float) (cx + r * Math.cos(a0)), (float) (cy + r * Math.sin(a0)),
                    (float) (cx + r * Math.cos(a1)), (float) (cy + r * Math.sin(a1)),
                    QUAD[i][0], QUAD[i][1], Shader.TileMode.CLAMP));
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, i * 90 + 2, 86, false, paint);
        }

        // 8 向刻度
        paint.setShader(null);
        paint.setColor(0xFF9FB0C6);
        paint.setStrokeWidth(2);
        paint.setStrokeCap(Paint.Cap.ROUND);
        float inner = r * 0.78f;
        float outer = r * 0.87f;
        canvas.save();
        for (int i = 0; i < 8; i++) {
            canvas.drawLine(cx, cy - inner, cx, cy - outer, paint);
            canvas.rotate(45, cx, cy);
        }
        canvas.restore();
    }
}
