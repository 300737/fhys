package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.fongmi.android.tv.R;

import java.util.Calendar;
import java.util.Locale;

/**
 * 设置页中间的动态图：钟表 + 月历。
 * 表盘与三根指针是 PNG 素材（drawable-nodpi/ic_clock_*.png），由矩阵旋转绘制，
 * 指针真走时；月历显示当前月份，当天红圈高亮，跨天自动切换。
 * 背景完全透明，直接透出外层 bg_page 的紫色渐变，不做成卡片。
 */
public class StarFlowView extends View {

    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};

    // 生成素材时的基准尺寸，用来算指针缩放比
    private static final float ASSET_S = 1024f;
    private static final float ASSET_INNER = ASSET_S * 0.48f * 0.90f;

    // 配色：全部跟着外层 bg_page 的紫色渐变走（#3B2890 → #2A1C6B → #150F30）
    private static final int DAY_GREEN = 0xFF9BE15D;
    private static final int DAY_GREEN_DIM = 0xFF6FA84A;
    private static final int TODAY_RED = 0xFFE53935;
    private static final int TITLE_GOLD = 0xFFE8C766;

    private final Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint weekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dialRect = new RectF();
    private final Matrix handMatrix = new Matrix();

    private Bitmap dialBmp, hourBmp, minuteBmp, secondBmp;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    private final Calendar now = Calendar.getInstance();

    // 几何缓存，避免每帧重复计算
    private float clockCx, clockCy, clockR;
    private float panelLeft, panelRight, panelTop, panelBottom, corner;
    private float gridLeft, gridRight, gridTop, gridCell;
    private float titleY, weekY, arrowY;
    private boolean laid;

    public StarFlowView(@NonNull Context context) {
        super(context);
        init();
    }

    public StarFlowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public StarFlowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bmpPaint.setAntiAlias(true);
        bmpPaint.setFilterBitmap(true);
        bmpPaint.setDither(true);

        titlePaint.setColor(TITLE_GOLD);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextAlign(Paint.Align.CENTER);

        weekPaint.setColor(TITLE_GOLD);
        weekPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        weekPaint.setTextAlign(Paint.Align.CENTER);

        dayPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        todayPaint.setStyle(Paint.Style.FILL);
        todayPaint.setColor(TODAY_RED);

        todayTextPaint.setColor(Color.WHITE);
        todayTextPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        todayTextPaint.setTextAlign(Paint.Align.CENTER);

        arrowPaint.setColor(TITLE_GOLD);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeCap(Paint.Cap.ROUND);
        arrowPaint.setStrokeWidth(3f);
    }

    /** 素材只解码一次，退出时回收，避免反复 IO */
    private void loadAssets() {
        if (dialBmp != null) return;
        dialBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clock_dial);
        hourBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clock_hour);
        minuteBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clock_minute);
        secondBmp = BitmapFactory.decodeResource(getResources(), R.drawable.ic_clock_second);
    }

    private void recycleAssets() {
        if (dialBmp != null) {
            dialBmp.recycle();
            dialBmp = null;
        }
        if (hourBmp != null) {
            hourBmp.recycle();
            hourBmp = null;
        }
        if (minuteBmp != null) {
            minuteBmp.recycle();
            minuteBmp = null;
        }
        if (secondBmp != null) {
            secondBmp.recycle();
            secondBmp = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        loadAssets();
        schedule();
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        recycleAssets();
        super.onDetachedFromWindow();
    }

    /** 对齐到整秒再刷新，秒针走动才顺滑 */
    private void schedule() {
        long delay = 1000L - (System.currentTimeMillis() % 1000L);
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delay);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        laid = false;
    }

    private void layout(float w, float h) {
        if (laid) return;
        float top = h * 0.05f;

        // 表盘：取宽高约束里较小的一边，保证永远画得下
        clockR = Math.min(w * 0.33f, h * 0.185f);
        clockCx = w * 0.5f;
        clockCy = top + clockR;

        // 日历面板：从表盘下方一直延伸到底部，6 行日期全部关在面板内，
        // 背景只有渐变，不会再有任何元素和数字抢位置
        panelLeft = w * 0.06f;
        panelRight = w - panelLeft;
        panelTop = clockCy + clockR + h * 0.05f;
        panelBottom = h * 0.955f;
        float panelH = panelBottom - panelTop;

        titleY = panelTop + panelH * 0.105f;
        arrowY = titleY;
        weekY = panelTop + panelH * 0.225f;
        gridTop = panelTop + panelH * 0.325f;
        gridCell = panelH * 0.675f / 6f;

        gridLeft = panelLeft + w * 0.035f;
        gridRight = panelRight - w * 0.035f;

        float unit = Math.min(w, h);
        corner = unit * 0.022f;
        titlePaint.setTextSize(unit * 0.032f);
        weekPaint.setTextSize(unit * 0.024f);
        dayPaint.setTextSize(Math.min(gridCell * 0.44f, unit * 0.028f));
        todayTextPaint.setTextSize(dayPaint.getTextSize());

        laid = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        layout(w, h);

        now.setTimeInMillis(System.currentTimeMillis());
        drawClock(canvas);
        drawCalendar(canvas);

        schedule();
    }

    // ---------------- 背景 ----------------

    // ---------------- 钟表 ----------------

    private void drawClock(Canvas canvas) {
        if (dialBmp == null || hourBmp == null || minuteBmp == null || secondBmp == null) return;
        float r = clockR;

        // 表盘整图贴入圆形区域
        dialRect.set(clockCx - r, clockCy - r, clockCx + r, clockCy + r);
        canvas.drawBitmap(dialBmp, null, dialRect, bmpPaint);

        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int second = now.get(Calendar.MILLISECOND) / 1000;

        // 素材里指针长度按 ASSET_INNER 设计，这里按当前表盘半径等比缩放
        float scale = (r * 0.90f) / ASSET_INNER;

        // 角度：0 度朝上（12 点），顺时针
        drawHand(canvas, hourBmp, ((hour % 12) + minute / 60f) * 30f, scale);
        drawHand(canvas, minuteBmp, (minute + second / 60f) * 6f, scale);
        drawHand(canvas, secondBmp, second * 6f, scale);
    }

    /** 指针图竖直朝上、旋转点在图片底部中心，绕表盘中心旋转后绘制 */
    private void drawHand(Canvas canvas, Bitmap hand, float degree, float scale) {
        float px = hand.getWidth() / 2f;
        float py = hand.getHeight();
        handMatrix.reset();
        handMatrix.postScale(scale, scale, px, py);
        handMatrix.postRotate(degree, px, py);
        handMatrix.postTranslate(clockCx - px, clockCy - py);
        canvas.drawBitmap(hand, handMatrix, bmpPaint);
    }

    // ---------------- 月历 ----------------

    private void drawCalendar(Canvas canvas) {
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);       // 0-based
        int today = now.get(Calendar.DAY_OF_MONTH);

        // 标题 + 装饰箭头
        String label = String.format(Locale.CHINA, "%d年%d月", year, month + 1);
        canvas.drawText(label, clockCx, titleY, titlePaint);
        float half = titlePaint.measureText(label) / 2f;
        float aw = titlePaint.getTextSize() * 0.62f;
        drawArrow(canvas, clockCx - half - aw, arrowY, -1f);
        drawArrow(canvas, clockCx + half + aw, arrowY, 1f);

        // 星期行（周一在首列）
        float colW = (gridRight - gridLeft) / 7f;
        for (int c = 0; c < 7; c++) {
            canvas.drawText(WEEK[c], gridLeft + colW * (c + 0.5f), weekY, weekPaint);
        }

        // 当月 1 号是周几：周日=1 → 首列偏移
        Calendar first = Calendar.getInstance();
        first.set(year, month, 1, 0, 0, 0);
        first.set(Calendar.MILLISECOND, 0);
        int lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // 周一=0
        int days = first.getActualMaximum(Calendar.DAY_OF_MONTH);

        float radius = Math.min(colW * 0.40f, dayPaint.getTextSize() * 0.92f);
        for (int d = 1; d <= days; d++) {
            int idx = lead + d - 1;
            int row = idx / 7;
            int col = idx % 7;
            if (row > 5) break;
            float x = gridLeft + colW * (col + 0.5f);
            float y = gridTop + row * gridCell;
            if (d == today) {
                canvas.drawCircle(x, y - dayPaint.getTextSize() * 0.35f, radius, todayPaint);
                canvas.drawText(String.valueOf(d), x, y, todayTextPaint);
            } else {
                dayPaint.setColor((col >= 5) ? DAY_GREEN_DIM : DAY_GREEN);
                canvas.drawText(String.valueOf(d), x, y, dayPaint);
            }
        }
    }

    private void drawArrow(Canvas canvas, float x, float y, float dir) {
        float s = titlePaint.getTextSize() * 0.30f;
        canvas.drawLine(x - s * dir, y - s, x + s * dir, y - s, arrowPaint);
        canvas.drawLine(x - s * dir, y + s, x + s * dir, y + s, arrowPaint);
        canvas.drawLine(x - s * dir, y - s, x + s * dir, y, arrowPaint);
        canvas.drawLine(x - s * dir, y + s, x + s * dir, y, arrowPaint);
    }
}
