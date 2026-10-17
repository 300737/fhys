package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 设置页中间的动态图：彩虹星河（彩色星河粒子 + 旋转彩虹光环 + 「繁华影视」四色字波动呼吸）。
 * 纯 Canvas 绘制，无图片资源，任意分辨率清晰，几乎不占内存。
 */
public class StarFlowView extends View {

    private static final long LOOP_MS = 2400L;      // 一个循环时长
    private static final int STAR_COUNT = 110;

    // 四个字四种颜色：繁=青 华=紫 影=粉红 视=金
    private static final int[] CHAR_COLORS = {0xFF5EE7FF, 0xFF7C6BFF, 0xFFFF6B8B, 0xFFFFC861};
    private static final String[] CHARS = {"繁", "华", "影", "视"};
    // 星河粒子五色循环
    private static final int[] STAR_COLORS = {0xFF5EE7FF, 0xFF7C6BFF, 0xFFE86BFF, 0xFFFF6B8B, 0xFFFFC861};

    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringRect = new RectF();
    private final float[] charWidth = new float[4];

    private final float[] starX = new float[STAR_COUNT];
    private final float[] starY = new float[STAR_COUNT];
    private final float[] starSpeed = new float[STAR_COUNT];
    private final float[] starSize = new float[STAR_COUNT];
    private final int[] starAlpha = new int[STAR_COUNT];

    private float density;
    private boolean running;
    private long startTime;
    private float textSize;

    public StarFlowView(Context context) {
        this(context, null);
    }

    public StarFlowView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StarFlowView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        starPaint.setStyle(Paint.Style.FILL);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.4f));
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        glowPaint.setStyle(Paint.Style.FILL);
        initStars();
    }

    private void initStars() {
        long seed = 20261015L;
        for (int i = 0; i < STAR_COUNT; i++) {
            seed = seed * 1103515245L + 12345L;
            starX[i] = Math.abs(seed % 1000) / 1000f;
            seed = seed * 1103515245L + 12345L;
            starY[i] = Math.abs(seed % 1000) / 1000f;
            seed = seed * 1103515245L + 12345L;
            starSpeed[i] = 0.3f + Math.abs(seed % 1000) / 1000f * 0.9f;
            seed = seed * 1103515245L + 12345L;
            starSize[i] = 1.0f + Math.abs(seed % 1000) / 1000f * 2.2f;
            seed = seed * 1103515245L + 12345L;
            starAlpha[i] = 110 + (int) (Math.abs(seed % 1000) / 1000f * 145);
        }
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTime = System.currentTimeMillis();
        running = true;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        running = visibility == VISIBLE;
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float t = ((System.currentTimeMillis() - startTime) % LOOP_MS) / (float) LOOP_MS;

        // 中心柔光，避免纯黑发死
        drawCenterGlow(canvas, w, h);

        // 彩色星河粒子：缓缓上浮
        for (int i = 0; i < STAR_COUNT; i++) {
            float x = starX[i] * w;
            float y = (starY[i] - t * starSpeed[i]) % 1f;
            if (y < 0) y += 1f;
            float r = dp(starSize[i]) * 0.55f;
            starPaint.setColor(STAR_COLORS[i % STAR_COLORS.length]);
            starPaint.setAlpha(starAlpha[i]);
            canvas.drawCircle(x, y * h, r, starPaint);
        }

        // 旋转彩虹光环（拖尾）
        float rr = h * 0.21f;
        float cx = w / 2f, cy = h / 2f;
        ringRect.set(cx - rr, cy - rr * 0.62f, cx + rr, cy + rr * 0.62f);
        float startAngle = t * 360f;
        float[] hsv = {0f, 0.8f, 1f};
        for (int k = 0; k < 46; k++) {
            float a0 = startAngle + k * 7.8f;
            float al = (float) Math.pow(1 - k / 46f, 1.4) * 0.9f;
            hsv[0] = (a0 % 360f + 360f) % 360f;
            int rgb = Color.HSVToColor(hsv);
            ringPaint.setColor(rgb);
            ringPaint.setAlpha((int) (al * 255));
            canvas.drawArc(ringRect, a0, 6f, false, ringPaint);
        }

        // 「繁华影视」四色字：逐字波动 + 呼吸发光（相位错开，像波浪跟着背景走）
        float pulse = 0.55f + 0.45f * (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * t));
        textSize = w * 0.30f;
        textPaint.setTextSize(textSize);
        for (int i = 0; i < 4; i++) charWidth[i] = textPaint.measureText(CHARS[i]);
        float lineH = textSize * 1.20f;
        float cyText = h / 2f;
        float gr = textSize * 2.4f;
        glowPaint.setShader(new RadialGradient(cx, cyText, gr,
                new int[]{(int) (0x30 * pulse) << 24 | 0x78A0FF, 0x0078A0FF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cyText, gr, glowPaint);
        glowPaint.setShader(null);

        drawLine(canvas, 0, 1, cx, cyText - lineH * 0.5f + textSize * 0.36f, pulse);
        drawLine(canvas, 2, 3, cx, cyText + lineH * 0.5f + textSize * 0.36f, pulse);

        if (running) postInvalidateOnAnimation();
    }

    private void drawCenterGlow(Canvas canvas, int w, int h) {
        float rx = w * 0.45f, ry = h * 0.22f;
        glowPaint.setShader(new RadialGradient(w / 2f, h / 2f, Math.max(rx, ry),
                new int[]{0x1EFFFFFF, 0x00FFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawOval(new RectF(w / 2f - rx, h / 2f - ry, w / 2f + rx, h / 2f + ry), glowPaint);
        glowPaint.setShader(null);
    }

    /** 画一排两个字（居中），带波动与四色发光 */
    private void drawLine(Canvas canvas, int i0, int i1, float cx, float yBase, float pulse) {
        float lineW = charWidth[i0] + charWidth[i1];
        float x = cx - lineW / 2f;
        drawChar(canvas, i0, x + charWidth[i0] / 2f, yBase, pulse);
        drawChar(canvas, i1, x + charWidth[i0] + charWidth[i1] / 2f, yBase, pulse);
    }

    /** 画单个字：dy = sin(2π(t + idx*0.13)) 波动，颜色 + 同色发光 */
    private void drawChar(Canvas canvas, int idx, float cx, float yBase, float pulse) {
        float t = ((System.currentTimeMillis() - startTime) % LOOP_MS) / (float) LOOP_MS;
        float dy = (float) Math.sin(2 * Math.PI * (t + idx * 0.13)) * textSize * 0.075f;
        int color = CHAR_COLORS[idx];
        textPaint.setColor(color);
        textPaint.setShadowLayer(textSize * 0.18f, 0, 0, (int) (0x70 * pulse) << 24 | (color & 0xFFFFFF));
        canvas.drawText(CHARS[idx], cx, yBase + dy, textPaint);
        textPaint.clearShadowLayer();
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "StarFlowView[%dx%d]", getWidth(), getHeight());
    }
}
