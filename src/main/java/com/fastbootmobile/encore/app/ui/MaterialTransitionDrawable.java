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

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.fastbootmobile.encore.art.RecyclingBitmapDrawable;

/**
 * <p>
 * Class that allows drawable transitions in a way that fits Google's Material Design specifications
 * (see <a href="http://www.google.com/design/spec/patterns/imagery-treatment.html">Material Design
 * pattern 'Imagery Treatment'</a>).
 * </p>
 */
public class MaterialTransitionDrawable extends Drawable {
    private static final String TAG = "MaterialTransDrawable";

    public static final long DEFAULT_DURATION = 1000;
    public static final long SHORT_DURATION = 300;

    private BitmapDrawable mBaseDrawable;
    private RecyclingBitmapDrawable mTargetDrawable;
    private BitmapDrawable mOfflineDrawable;
    private final AccelerateDecelerateInterpolator mInterpolator;
    private long mStartTime;
    private boolean mAnimating;
    private long mDuration = DEFAULT_DURATION;
    private ColorMatrix mColorMatSaturation;
    private Paint mPaint;
    private boolean mShowOfflineOverdraw;
    private long mOfflineStartTime;
    private ColorFilter mExtColorFilter;
    private final Object mDrawLock = new Object();

    public MaterialTransitionDrawable(BitmapDrawable offlineDrawable, BitmapDrawable base) {
        this(offlineDrawable);
        mBaseDrawable = base;
    }

    public MaterialTransitionDrawable(BitmapDrawable offlineDrawable) {
        mInterpolator = new AccelerateDecelerateInterpolator();
        mAnimating = false;
        mShowOfflineOverdraw = false;
        mColorMatSaturation = new ColorMatrix();
        mPaint = new Paint();
        mOfflineDrawable = offlineDrawable;
    }

    public BitmapDrawable getFinalDrawable() {
        synchronized (mDrawLock) {
            if (mTargetDrawable != null) {
                return mTargetDrawable;
            } else {
                return mBaseDrawable;
            }
        }
    }

    public void setTransitionDuration(long durationMillis) {
        synchronized (mDrawLock) {
            mDuration = durationMillis;
        }
    }

    public void setImmediateTo(BitmapDrawable drawable) {
        synchronized (mDrawLock) {
            // Cancel animation
            mAnimating = false;
            mTargetDrawable = null;
            mShowOfflineOverdraw = false;

            // Set new drawable as base and draw it
            if (mBaseDrawable != null && mBaseDrawable instanceof RecyclingBitmapDrawable) {
                ((RecyclingBitmapDrawable) mBaseDrawable).setIsDisplayed(false);
            }

            mBaseDrawable = drawable;
            mBaseDrawable.setBounds(getBounds());

            if (mBaseDrawable instanceof RecyclingBitmapDrawable) {
                ((RecyclingBitmapDrawable) mBaseDrawable).setIsDisplayed(true);
            }
        }
        invalidateSelf();
    }

    public void transitionTo(final RecyclingBitmapDrawable drawable) {
        synchronized (mDrawLock) {
            if (drawable != mTargetDrawable) {
                mTargetDrawable = drawable;
                mTargetDrawable.setBounds(getBounds());

                mStartTime = -1;
                mAnimating = true;
            }
        }
    }

    public void setShowOfflineOverdraw(boolean show) {
        if (mShowOfflineOverdraw != show) {
            synchronized (mDrawLock) {
                mShowOfflineOverdraw = show;
                mOfflineStartTime = SystemClock.uptimeMillis();
            }
            invalidateSelf();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        synchronized (mDrawLock) {
            if (mBaseDrawable != null & !mAnimating) {
                mBaseDrawable.setBounds(bounds);
            }
            if (mTargetDrawable != null) {
                mTargetDrawable.setBounds(bounds);
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        synchronized (mDrawLock) {
            if (mAnimating) {
                if (mStartTime < 0) {
                    mStartTime = SystemClock.uptimeMillis();
                }

                final float rawProgress = Math.min(1.0f,
                        ((float) (SystemClock.uptimeMillis() - mStartTime)) / ((float) mDuration));

                // As per the Material Design spec, animation goes into 3 steps. Ranging from 0 to 100,
                // opacity is full at 50, exposure (gamma + black output) at 75, and saturation at 100.
                // For performance, we only do the saturation and opacity transition
                final float inputOpacity = Math.min(1.0f, rawProgress * (1.0f / 0.5f));
                // final float inputExposure = Math.min(1.0f, rawProgress * (1.0f / 0.75f));

                final float progressOpacity = mInterpolator.getInterpolation(inputOpacity);
                // final float progressExposure = 1.0f - mInterpolator.getInterpolation(inputExposure);
                final float progressSaturation = mInterpolator.getInterpolation(rawProgress);

                if (mBaseDrawable != null) {
                    drawTranslatedBase(canvas);
                }

                if (mExtColorFilter == null) {
                    mColorMatSaturation.setSaturation(progressSaturation);
                    ColorMatrixColorFilter colorMatFilter = new ColorMatrixColorFilter(mColorMatSaturation);
                    mPaint.setAlpha((int) (progressOpacity * 255.0f));
                    mPaint.setColorFilter(colorMatFilter);
                } else {
                    mPaint.setColorFilter(mExtColorFilter);
                }

                if (!mTargetDrawable.getBitmap().isRecycled()) {
                    try {
                        canvas.drawBitmap(mTargetDrawable.getBitmap(), 0, 0, mPaint);
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Couldn't write target bitmap!");
                    }
                }

                if (rawProgress >= 1.0f) {
                    mAnimating = false;
                    if (mBaseDrawable != null && mBaseDrawable instanceof RecyclingBitmapDrawable) {
                        ((RecyclingBitmapDrawable) mBaseDrawable).setIsDisplayed(false);
                    }
                    mBaseDrawable = mTargetDrawable;
                } else {
                    invalidateSelf();
                }
            } else if (mBaseDrawable != null) {
                if (!mBaseDrawable.getBitmap().isRecycled()) {
                    try {
                        mBaseDrawable.draw(canvas);
                    } catch (Exception e) {
                        Log.w(TAG, "Couldn't draw base drawable: " + e.getMessage());
                    }
                }
            }

            if (mShowOfflineOverdraw) {
                int alpha = (int) Math.min(160, (SystemClock.uptimeMillis() - mOfflineStartTime) / 4);
                canvas.drawColor(0x00888888 | ((alpha & 0xFF) << 24));

                mPaint.setAlpha(alpha * 255 / 160);

                canvas.drawBitmap(mOfflineDrawable.getBitmap(),
                        getBounds().centerX() - mOfflineDrawable.getIntrinsicWidth() / 2,
                        getBounds().centerY() - mOfflineDrawable.getIntrinsicHeight() / 2,
                        mPaint);

                if (alpha != 160) {
                    invalidateSelf();
                }
            }
        }
    }

    private void drawTranslatedBase(Canvas canvas) {
        // Pad the base drawable to be at the center of the target size
        final float targetWidth = mTargetDrawable.getIntrinsicWidth();
        final float targetHeight = mTargetDrawable.getIntrinsicHeight();
        Rect baseBounds = mBaseDrawable.getBounds();
        final float baseWidth = baseBounds.width();
        final float baseHeight = baseBounds.height();

        canvas.save();

        float scaling;
        if (targetWidth > targetHeight) {
            scaling = Math.max(targetWidth / baseWidth, targetHeight / baseHeight);
        } else {
            scaling = Math.min(targetWidth / baseWidth, targetHeight / baseHeight);
        }

        final float scaledBaseWidth = scaling * baseWidth;
        final float scaledBaseHeight = scaling * baseHeight;

        canvas.translate((targetWidth - scaledBaseWidth) * 0.5f,
                (targetHeight - scaledBaseHeight) * 0.5f);
        canvas.scale(scaling, scaling);

        if (!mBaseDrawable.getBitmap().isRecycled()) {
            mBaseDrawable.draw(canvas);
        }
        canvas.restore();
    }

    @Override
    public int getIntrinsicHeight() {
        if (mAnimating && mTargetDrawable != null) {
            return mTargetDrawable.getIntrinsicHeight();
        } else if (mBaseDrawable != null) {
            return mBaseDrawable.getIntrinsicHeight();
        } else {
            return super.getIntrinsicHeight();
        }
    }

    @Override
    public int getIntrinsicWidth() {
        if (mAnimating && mTargetDrawable != null) {
            return mTargetDrawable.getIntrinsicWidth();
        } else if (mBaseDrawable != null) {
            return mBaseDrawable.getIntrinsicWidth();
        } else {
            return super.getIntrinsicWidth();
        }
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mExtColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return 255;
    }
}