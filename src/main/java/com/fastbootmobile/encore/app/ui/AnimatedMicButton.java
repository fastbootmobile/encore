/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageButton;

import com.fastbootmobile.encore.app.R;

public class AnimatedMicButton extends ImageButton {
    private float mTargetLevel = 0.0f;
    private float mCurrentLevel = 0.0f;
    private CircleBounceDrawable mBounceDrawable;
    private LayerDrawable mLayeredOn;
    private LayerDrawable mLayeredOff;

    public AnimatedMicButton(Context context) {
        super(context);
        init();
    }

    public AnimatedMicButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimatedMicButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mBounceDrawable = new CircleBounceDrawable();
        final BitmapDrawable micOn = (BitmapDrawable) getResources().getDrawable(R.drawable.ic_recognition_mic);
        final BitmapDrawable micOff = (BitmapDrawable) getResources().getDrawable(R.drawable.ic_recognition_mic_off);

        mLayeredOn = new LayerDrawable(new Drawable[]{mBounceDrawable, micOn});
        mLayeredOff = new LayerDrawable(new Drawable[]{mBounceDrawable, micOff});
        setImageDrawable(mLayeredOff);
    }

    public void setActive(boolean active) {
        if (active) {
            setImageDrawable(mLayeredOn);
        } else {
            setImageDrawable(mLayeredOff);
            mTargetLevel = 0;
        }
    }

    public void setLevel(float level) {
        mTargetLevel = level;
        postInvalidate();
        mBounceDrawable.invalidateSelf();
    }

    private class CircleBounceDrawable extends Drawable {
        private int mAlpha = 255;
        private Paint mPaint;
        private AccelerateDecelerateInterpolator mInterpolator = new AccelerateDecelerateInterpolator();

        public CircleBounceDrawable() {
            mPaint = new Paint();
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();

            mPaint.setColor(0xFFDDDDDD);

            canvas.drawCircle(bounds.centerX(), bounds.centerY(),
                    bounds.height() / 1.5f + mCurrentLevel * bounds.height() / 3.0f,
                    mPaint);

            mPaint.setColor(0xFFFFFFFF);

            canvas.drawCircle(bounds.centerX(), bounds.centerY(),
                    bounds.height() / 1.5f,
                    mPaint);

            if (mCurrentLevel != mTargetLevel) {
                mCurrentLevel += (mTargetLevel - mCurrentLevel) / 4.0f;
                invalidateSelf();
            }
        }

        @Override
        public void setAlpha(int alpha) {
            mAlpha = alpha;
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
        }

        @Override
        public int getOpacity() {
            return mAlpha;
        }
    }
}
