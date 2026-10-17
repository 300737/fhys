package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.player.lrc.LrcParser;
import com.fongmi.android.tv.player.lrc.LrcRow;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;

public class LrcView extends View {

    public static final int MODE_DOUBLE_LINE = 0;
    public static final int MODE_MULTI_LINE = 1;

    private List<LrcRow> mLrcRows = new ArrayList<>();
    private int mLrcMode = MODE_DOUBLE_LINE;
    private long mCurrentPosition = 0;
    private int mCurrentLineIndex = -1;

    // Paints
    private Paint mNormalPaint;
    private Paint mHighlightPaint;
    private Paint mHighlightOverlayPaint;

    // Layout configuration
    private float mNormalTextSize;
    private float mHighlightTextSize;
    private float mLineSpacing;

    // Scroll animation state for multi-line mode
    private float mCurrentScrollY = 0;
    private float mTargetScrollY = 0;



    public LrcView(Context context) {
        this(context, null);
    }

    public LrcView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LrcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mLrcMode = Setting.getLrcMode();

        // Initialize sizes
        mNormalTextSize = dp2px(18);
        mHighlightTextSize = dp2px(22);
        mLineSpacing = dp2px(35);

        // Paints
        mNormalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mNormalPaint.setColor(Color.parseColor("#90FFFFFF")); // Semi-transparent white
        mNormalPaint.setTextSize(mNormalTextSize);
        mNormalPaint.setTextAlign(Paint.Align.CENTER);
        mNormalPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        mNormalPaint.setShadowLayer(4, 0, 2, Color.BLACK);

        mHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHighlightPaint.setColor(Color.parseColor("#FFFFFFFF")); // Solid white background for active line
        mHighlightPaint.setTextSize(mHighlightTextSize);
        mHighlightPaint.setTextAlign(Paint.Align.CENTER);
        mHighlightPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mHighlightPaint.setShadowLayer(4, 0, 2, Color.BLACK);

        mHighlightOverlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mHighlightOverlayPaint.setColor(Color.parseColor("#FF00E5FF")); // Neon Cyan overlay color
        mHighlightOverlayPaint.setTextSize(mHighlightTextSize);
        mHighlightOverlayPaint.setTextAlign(Paint.Align.LEFT); // Draw word by word left-aligned
        mHighlightOverlayPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        mHighlightOverlayPaint.setShadowLayer(4, 0, 2, Color.BLACK);



        setClickable(false);
        setFocusable(false);
    }

    public void setLrcMode(int mode) {
        if (mLrcMode != mode) {
            mLrcMode = mode;
            mCurrentScrollY = 0; // Reset scroll animation
            invalidate();
        }
    }

    public int getLrcMode() {
        return mLrcMode;
    }

    public void setData(String rawText) {
        mLrcRows = LrcParser.parse(rawText);
        mCurrentLineIndex = -1;
        mCurrentScrollY = 0;
        mTargetScrollY = 0;
        invalidate();
    }

    public void c() {
        mLrcRows.clear();
        mCurrentLineIndex = -1;
        mCurrentScrollY = 0;
        mTargetScrollY = 0;
        invalidate();
    }

    public void updateTime(long time) {
        if (mLrcRows.isEmpty()) return;
        mCurrentPosition = time;
        int newLineIndex = findLineIndex(time);
        if (newLineIndex != mCurrentLineIndex) {
            mCurrentLineIndex = newLineIndex;
            // Trigger scroll target update for multi-line
            if (mLrcMode == MODE_MULTI_LINE && mCurrentLineIndex >= 0) {
                float lineHeight = mHighlightTextSize + mLineSpacing;
                mTargetScrollY = mCurrentLineIndex * lineHeight;
            }
            invalidate();
        } else if (mLrcMode == MODE_DOUBLE_LINE || (mCurrentLineIndex >= 0 && mLrcRows.get(mCurrentLineIndex).getWords() != null)) {
            // For KTV dynamic lyrics, we need constant updates for progressive highlight
            invalidate();
        }
    }

    private int findLineIndex(long time) {
        int left = 0;
        int right = mLrcRows.size() - 1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            LrcRow row = mLrcRows.get(mid);
            if (time < row.getStartTime()) {
                right = mid - 1;
            } else if (time >= row.getStartTime() + row.getDuration()) {
                left = mid + 1;
            } else {
                return mid;
            }
        }
        left = 0;
        right = mLrcRows.size() - 1;
        int index = -1;
        while (left <= right) {
            int mid = (left + right) >>> 1;
            if (mLrcRows.get(mid).getStartTime() <= time) {
                index = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return index;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mLrcRows.isEmpty()) {
            return;
        }

        if (mLrcMode == MODE_DOUBLE_LINE) {
            drawDoubleLine(canvas);
        } else {
            drawMultiLine(canvas);
        }
    }

    private void drawDoubleLine(Canvas canvas) {
        int index = Math.max(0, mCurrentLineIndex);
        int topIndex, bottomIndex;
        boolean isTopHighlight, isBottomHighlight;

        if (index % 2 == 0) {
            topIndex = index;
            bottomIndex = index + 1;
            isTopHighlight = true;
            isBottomHighlight = false;
        } else {
            topIndex = index - 1;
            bottomIndex = index;
            isTopHighlight = false;
            isBottomHighlight = true;
        }

        float height = getHeight();
        float topY = height - mLineSpacing - dp2px(15);
        float bottomY = height - dp2px(15);
        float margin = dp2px(36);

        // Draw top line (aligned left)
        if (topIndex >= 0 && topIndex < mLrcRows.size()) {
            LrcRow topRow = mLrcRows.get(topIndex);
            Paint paint = isTopHighlight ? mHighlightPaint : mNormalPaint;
            float totalWidth = paint.measureText(topRow.getContent());
            float targetLeft = margin;
            float topX = targetLeft + totalWidth / 2f;
            if (topX + totalWidth / 2f > getWidth() - margin) {
                topX = getWidth() - margin - totalWidth / 2f;
            }
            if (topX - totalWidth / 2f < margin) {
                topX = margin + totalWidth / 2f;
            }
            drawSingleLineLrc(canvas, topRow, topX, topY, isTopHighlight, 1.0f);
        }

        // Draw bottom line (aligned right)
        if (bottomIndex >= 0 && bottomIndex < mLrcRows.size()) {
            LrcRow bottomRow = mLrcRows.get(bottomIndex);
            Paint paint = isBottomHighlight ? mHighlightPaint : mNormalPaint;
            float totalWidth = paint.measureText(bottomRow.getContent());
            float targetRight = getWidth() - margin;
            float bottomX = targetRight - totalWidth / 2f;
            if (bottomX - totalWidth / 2f < margin) {
                bottomX = margin + totalWidth / 2f;
            }
            if (bottomX + totalWidth / 2f > getWidth() - margin) {
                bottomX = getWidth() - margin - totalWidth / 2f;
            }
            drawSingleLineLrc(canvas, bottomRow, bottomX, bottomY, isBottomHighlight, 1.0f);
        }
    }

    private void drawMultiLine(Canvas canvas) {
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float lineHeight = mHighlightTextSize + mLineSpacing;

        // Smooth scroll Y interpolation
        if (Math.abs(mTargetScrollY - mCurrentScrollY) > 0.5f) {
            mCurrentScrollY += (mTargetScrollY - mCurrentScrollY) * 0.15f;
            postInvalidateDelayed(16);
        } else {
            mCurrentScrollY = mTargetScrollY;
        }

        canvas.save();
        canvas.translate(0, centerY - mCurrentScrollY);

        for (int i = 0; i < mLrcRows.size(); i++) {
            LrcRow row = mLrcRows.get(i);
            boolean isHighlight = (i == mCurrentLineIndex);
            float currentY = i * lineHeight;

            // Calculate fading alpha based on distance from center
            float distance = Math.abs(currentY - mCurrentScrollY);
            float maxDistance = centerY;
            float alpha = 1.0f;
            if (distance > 0) {
                alpha = 1.0f - (distance / maxDistance);
                alpha = Math.max(0.15f, Math.min(1.0f, alpha));
            }
            
            drawSingleLineLrc(canvas, row, centerX, currentY, isHighlight, alpha);
        }

        canvas.restore();
    }

    private void drawSingleLineLrc(Canvas canvas, LrcRow row, float x, float y, boolean isHighlight, float alpha) {
        Paint paint = isHighlight ? mHighlightPaint : mNormalPaint;
        int originalAlpha = paint.getAlpha();
        paint.setAlpha((int) (originalAlpha * alpha));

        List<LrcRow.Word> words = row.getWords();
        if (isHighlight && words != null && !words.isEmpty()) {
            // Draw KTV progressive highlight
            // 1. Draw background full text in highlight paint (solid white)
            String content = row.getContent();
            canvas.drawText(content, x, y, mHighlightPaint);

            // 2. Compute total width and starting X of the text
            float totalWidth = mHighlightPaint.measureText(content);
            float startX = x - totalWidth / 2f;

            // 3. Draw highlighted segments on top
            long timeInLine = mCurrentPosition - row.getStartTime();
            float wordStartX = startX;

            int originalOverlayAlpha = mHighlightOverlayPaint.getAlpha();
            mHighlightOverlayPaint.setAlpha((int) (originalOverlayAlpha * alpha));

            for (LrcRow.Word word : words) {
                String wordText = word.getText();
                float wordWidth = mHighlightPaint.measureText(wordText);

                long wordStart = word.getOffset();
                long wordEnd = wordStart + word.getDuration();
                float progress = 0f;

                if (timeInLine >= wordEnd) {
                    progress = 1f;
                } else if (timeInLine > wordStart) {
                    progress = (float) (timeInLine - wordStart) / word.getDuration();
                }

                if (progress > 0) {
                    canvas.save();
                    canvas.clipRect(wordStartX, y - mHighlightTextSize, wordStartX + wordWidth * progress, y + dp2px(10));
                    canvas.drawText(wordText, wordStartX, y, mHighlightOverlayPaint);
                    canvas.restore();
                }

                wordStartX += wordWidth;
            }
            mHighlightOverlayPaint.setAlpha(originalOverlayAlpha);
        } else {
            canvas.drawText(row.getContent(), x, y, paint);
        }

        paint.setAlpha(originalAlpha);
    }

    public void toggleLrcMode() {
        int nextMode = mLrcMode == MODE_DOUBLE_LINE ? MODE_MULTI_LINE : MODE_DOUBLE_LINE;
        setLrcMode(nextMode);
        Setting.putLrcMode(nextMode);
        String text = nextMode == MODE_DOUBLE_LINE ? "已切换到双行歌词模式" : "已切换到多行歌词模式";
        Notify.show(text);
    }

    private float dp2px(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
