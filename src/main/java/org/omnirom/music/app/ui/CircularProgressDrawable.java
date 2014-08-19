package org.omnirom.music.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

/**
 * Created by Guigui on 15/08/2014.
 */
public class CircularProgressDrawable extends Drawable {

    private float mValue;
    private float mMax;
    private Paint mPaint;
    private RectF mRect;
    private int mOpacity;
    private float mPadding;

    public CircularProgressDrawable() {
        mValue = 50;
        mMax = 100;
        mOpacity = 255;
        mPadding = 0;
        mRect = new RectF();
        mPaint = new Paint();
        mPaint.setColor(0xFFFFFFFF);
        mPaint.setStrokeWidth(10.0f);
        mPaint.setAntiAlias(true);
        mPaint.setStyle(Paint.Style.STROKE);
    }

    public void setValue(float value) {
        mValue = value;
        invalidateSelf();
    }

    public void setMax(float max) {
        mMax = max;
        invalidateSelf();
    }

    public void setColor(int color) {
        mPaint.setColor(color);
        invalidateSelf();
    }

    public float getValue() {
        return mValue;
    }

    public float getMax() {
        return mMax;
    }

    public void setPadding(float pad) {
        mPadding = pad;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mRect.set(bounds);
    }

    @Override
    public void draw(Canvas canvas) {
        mRect.set(getBounds());
        final float paddedStrokeWidth = mPaint.getStrokeWidth() + mPadding * 2.0f;
        mRect.left += paddedStrokeWidth / 2.0f;
        mRect.right -= paddedStrokeWidth / 2.0f;
        mRect.top += paddedStrokeWidth / 2.0f;
        mRect.bottom -= paddedStrokeWidth / 2.0f;

        float sweepAngle = mValue * 360.0f / mMax;
        canvas.drawArc(mRect, -90.0f, sweepAngle, false, mPaint);
    }

    @Override
    public void setAlpha(int i) {
        mOpacity = i;
        mPaint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return mOpacity;
    }
}
