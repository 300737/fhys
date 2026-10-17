package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MaskFrameLayout extends FrameLayout {

    private Paint mPaint;
    private Shader mShader;

    public MaskFrameLayout(@NonNull Context context) {
        super(context);
        init();
    }

    public MaskFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mShader = new LinearGradient(0, 0, w, 0, new int[]{0x00000000, 0xFF000000}, new float[]{0.0f, 1.0f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (mShader != null) {
            mPaint.setShader(mShader);
            canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
        }
    }
}
