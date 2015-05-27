/*
 * Copyright (C) 2014 Fastboot Mobile, LLC.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See
 * the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, see <http://www.gnu.org/licenses>.
 */

package com.fastbootmobile.encore.app.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Non-filled circular progress drawable
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
        final float paddedStrokeWidth = (mPaint.getStrokeWidth() + mPadding * 2.0f) / 2.0f;

        int size = Math.min(bounds.height(), bounds.width());

        mRect.set(0, 0, size, size);
        mRect.offset(bounds.width() / 2 - size / 2, bounds.height() / 2 - size / 2);
        mRect.inset(paddedStrokeWidth, paddedStrokeWidth);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(0, -8.0f);
        float sweepAngle = mValue * 360.0f / mMax;
        canvas.drawArc(mRect, -90.0f, sweepAngle, false, mPaint);
        canvas.restore();
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
