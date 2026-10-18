package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.R;

/**
 * 冷启动开机动画（可见后 5 秒，4K视界式全屏大图）：
 * <ul>
 *   <li>全屏梦幻壁纸 + Ken Burns 缓动（缓慢放大+上移，画面有生命感）</li>
 *   <li>底部绿色进度胶囊 + 圆点，随时间推进</li>
 *   <li>进度下方状态文字分三段：正在启动应用 → 正在解析配置 → 正在选择线路</li>
 *   <li>3.4s 起压轴打出招牌金字「繁华影视欢迎您」（放大+淡入）</li>
 *   <li>最后 0.6 秒整体淡出；任意按键可跳过（skip()），结束自动移除</li>
 * </ul>
 * 计时从第一次真正上屏开始（避免冷启动系统闪屏期间被白白消耗）。
 */
public class LaunchSplashView extends View {

    private static final long DURATION = 5000;
    private static final long TITLE_AT = 3400;
    private static final String[] STAGES = {"正在启动应用…", "正在解析配置…", "正在选择线路…"};
    private static final String TITLE = "繁华影视欢迎您";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (finished) return;
            invalidate();
            postDelayed(this, 16);
        }
    };
    private Drawable background;
    private float textPx;
    private float titlePx;
    private boolean finished;

    // 进程级计时：HomeActivity 启动途中可能被 recreate，计时与"本次冷启动已播过"
    // 的状态必须跨实例保存，重建后补挂并接续剩余时间
    private static long sStartAt = -1;
    private static boolean sProcessDone;

    /** 本次冷启动是否还需要显示开机动画（新进程未播完即需要） */
    public static boolean shouldShow() {
        return !sProcessDone && (sStartAt < 0 || SystemClock.elapsedRealtime() - sStartAt < DURATION);
    }

    public LaunchSplashView(Context context) {
        this(context, null);
    }

    public LaunchSplashView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        background = ContextCompat.getDrawable(context, R.drawable.bg_launch);
        textPx = 15f * context.getResources().getDisplayMetrics().scaledDensity;
        titlePx = 54f * context.getResources().getDisplayMetrics().scaledDensity;
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

    /** 跳过动画（任意按键触发），立即移除自身 */
    public void skip() {
        finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        sProcessDone = true;
        sStartAt = -1;
        removeCallbacks(ticker);
        post(() -> {
            if (getParent() instanceof android.view.ViewGroup) ((android.view.ViewGroup) getParent()).removeView(this);
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            drawSplash(canvas);
        } catch (Exception e) {
            android.util.Log.e("LaunchSplash", "draw error at t=" + elapsed(), e);
        }
    }

    private long elapsed() {
        return sStartAt < 0 ? 0 : SystemClock.elapsedRealtime() - sStartAt;
    }

    private void drawSplash(Canvas canvas) {
        if (sStartAt < 0) sStartAt = SystemClock.elapsedRealtime();
        long t = SystemClock.elapsedRealtime() - sStartAt;
        if (t >= DURATION) {
            finish();
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float fade = t > DURATION - 600 ? Math.max(0f, (DURATION - t) / 600f) : 1f;

        // ---- 全屏壁纸：Ken Burns 缓动（放大 1.02→1.10，轻微上移）----
        if (background != null) {
            float scale = 1.02f + 0.08f * (t / (float) DURATION);
            float bw = background.getIntrinsicWidth();
            float bh = background.getIntrinsicHeight();
            float base = Math.max(w / bw, h / bh);
            float dx = (w - bw * base * scale) / 2f;
            float dy = (h - bh * base * scale) / 2f - (h * 0.02f) * (t / (float) DURATION);
            matrix.reset();
            matrix.setScale(base * scale, base * scale);
            matrix.postTranslate(dx, dy);
            paint.setAlpha((int) (255 * fade));
            canvas.drawBitmap(((android.graphics.drawable.BitmapDrawable) background).getBitmap(), matrix, paint);
            paint.setAlpha(255);
        }

        if (fade <= 0f) return;

        // ---- 底部进度：绿色胶囊 + 圆点（4K视界式）----
        float pillW = w * 0.10f;
        float pillH = 14f * getResources().getDisplayMetrics().scaledDensity;
        float pillX = cx(w, pillW);
        float pillY = h - h * 0.115f;
        paint.setAlpha((int) (255 * fade));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66101825);
        float trackW = pillW + 34f * getResources().getDisplayMetrics().scaledDensity;
        canvas.drawRoundRect(pillX - 8, pillY - pillH / 2f - 8, pillX + trackW + 8, pillY + pillH / 2f + 8, pillH, pillH, paint);
        paint.setColor(0xFF4CAF50);
        rect.set(pillX, pillY - pillH / 2f, pillX + pillW * Math.min(1f, t / (float) DURATION * 1.05f), pillY + pillH / 2f);
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, paint);
        float dotR = pillH * 0.42f;
        canvas.drawCircle(pillX + pillW + 22f * getResources().getDisplayMetrics().scaledDensity, pillY, dotR, paint);

        // ---- 状态文字 ----
        String stage = STAGES[Math.min(STAGES.length - 1, (int) (t / (DURATION / 3f)))];
        paint.setColor(Color.WHITE);
        paint.setTextSize(textPx);
        float tw = paint.measureText(stage);
        float th = paint.getFontSpacing();
        float tx = cx(w, tw);
        float ty = pillY + pillH + 18f * getResources().getDisplayMetrics().scaledDensity;
        paint.setColor(0xB3101825);
        canvas.drawRoundRect(tx - 18, ty - th + 4, tx + tw + 18, ty + 8, th / 2f, th / 2f, paint);
        paint.setColor(0xFFE8EDF4);
        canvas.drawText(stage, tx, ty, paint);

        // ---- 压轴招牌：繁华影视欢迎您（3.4s 起放大淡入，金色渐变+阴影）----
        if (t >= TITLE_AT) {
            float tp = Math.min(1f, (t - TITLE_AT) / 1000f);
            float eased = 1f - (1f - tp) * (1f - tp);
            paint.setColor(Color.WHITE);
            paint.setTextSize(titlePx * (0.92f + 0.08f * eased));
            float bandH = titlePx * 1.9f;
            float bandY = h * 0.42f - titlePx * 1.15f;
            paint.setShader(null);
            paint.setColor(0x59060911);
            paint.setAlpha((int) (0x59 * eased * fade));
            canvas.drawRect(0, bandY, w, bandY + bandH, paint);
            paint.setShadowLayer(8, 0, 5, 0xDD000000);
            paint.setShader(new android.graphics.LinearGradient(cx(w, 0), 0, w, 0,
                    0xFFFFE082, 0xFFFFB300, android.graphics.Shader.TileMode.CLAMP));
            paint.setAlpha((int) (255 * eased * fade));
            float bw = paint.measureText(TITLE);
            canvas.drawText(TITLE, cx(w, bw), h * 0.42f, paint);
            paint.setShader(null);
            paint.setShadowLayer(0, 0, 0, 0);
            paint.setAlpha(255);
        }
        paint.setAlpha(255);
    }

    private float cx(float w, float item) {
        return (w - item) / 2f;
    }
}
