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

package org.omnirom.music.app.ui;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.dd.CircularAnimatedDrawable;

import org.omnirom.music.app.R;

/**
 * Animated drawable giving native Material animations between Play, Pause and Stop state
 */
public class PlayPauseDrawable extends Drawable {

    private static final String TAG = "PlayPauseDrawable";

    public static final int SHAPE_PAUSE = 0;
    public static final int SHAPE_STOP = 1;
    public static final int SHAPE_PLAY = 2;

    private static final float TRANSITION_DURATION = 300;
    private static final float PAUSE_TRIM_RATIO = 0.12f;

    private int mHalfPadding;
    private int mCurrentShape;
    private int mRequestShape;
    private Path mPath;
    private Paint mPaint;
    private boolean mInitialDrawDone;
    private AccelerateDecelerateInterpolator mTransitionInterpolator;
    private long mTransitionAccumulator;
    private long mLastTransitionTick;
    private boolean mIsBuffering;
    private int mYOffset;
    private CircularAnimatedDrawable mBufferingDrawable;

    private Resources mResources;

    /**
     * Default constructor
     * @param res A valid Resources handle
     */
    public PlayPauseDrawable(Resources res) {
        mResources = res;
        setPaddingDp(42);
        mCurrentShape = mRequestShape = -1;
        mPath = new Path();
        mPaint = new Paint();

        mPaint.setColor(0xCCFFFFFF);
        mPaint.setStyle(Paint.Style.FILL);

        mInitialDrawDone = false;
        mTransitionInterpolator = new AccelerateDecelerateInterpolator();
    }

    /**
     * Sets the drawing color of the drawable
     * @param color The color
     */
    public void setColor(int color) {
        mPaint.setColor(color);
    }

    /**
     * Sets the drawable padding, in DP
     * @param paddingDp The padding value, in DP
     */
    public void setPaddingDp(int paddingDp) {
        setPadding(mResources.getDimensionPixelSize(R.dimen.one_dp) * paddingDp) ;
    }

    /**
     * Sets the drawable padding, in pixels
     * @param padding The padding value, in pixels
     */
    public void setPadding(int padding) {
        mHalfPadding = padding / 2;
    }

    /**
     * Sets the Y-axis offset of the drawable
     * @param offset The offset
     */
    public void setYOffset(int offset) {
        mYOffset = offset;
    }

    /**
     * Sets the shape that the drawable should morph into
     * @param shape One of {@link #SHAPE_PLAY}, {@link #SHAPE_PAUSE}, {@link #SHAPE_STOP}
     */
    public void setShape(int shape) {
        if (mRequestShape != shape) {
            mRequestShape = shape;
            mTransitionAccumulator = 0;
            mLastTransitionTick = System.currentTimeMillis();
            invalidateSelf();
        }
    }

    /**
     * Sets whether or not an indeterminate progress indicator should be displayed, indicating
     * that the playback is pending buffering.
     * @param buffering true to display the indicator, false otherwise
     */
    public void setBuffering(boolean buffering) {
        mIsBuffering = buffering;
        invalidateSelf();
    }

    /**
     * @return The currently visible shape
     */
    public int getCurrentShape() {
        return mCurrentShape;
    }

    /**
     * @return The latest shape that has been requested
     */
    public int getRequestedShape() {
        return mRequestShape;
    }

    private float getProgress() {
        float raw = mTransitionAccumulator / TRANSITION_DURATION;
        return Math.max(0.0f, Math.min(raw, 1.0f));
    }

    private void shapeInitialPath() {
        mInitialDrawDone = true;

        mPath.reset();

        switch (mRequestShape) {
            case SHAPE_PAUSE:
                transitionStopToPause(1.0f);
                break;

            case SHAPE_PLAY:
                transitionStopToPlay(1.0f);
                break;

            case SHAPE_STOP:
                transitionStopToPause(0.0f);
                break;
        }

        mCurrentShape = mRequestShape;
    }

    private void transitionPlayToStop(final float progress) {
        final int width = getBounds().width();
        final int height = getBounds().height();
        final int halfHeight = height / 2;

        // Animation from play to stop: Play rotates 90°, point split at the tip (on the right)
        mPath.reset();

        // Make the play triangle, with the "fourth" point at the tip moving towards making a
        // square (they split progressively)
        mPath.moveTo(mHalfPadding, mHalfPadding);
        mPath.lineTo(width - mHalfPadding, halfHeight - halfHeight * progress + mHalfPadding * progress);
        mPath.lineTo(width - mHalfPadding, halfHeight + halfHeight * progress - mHalfPadding * progress);
        mPath.lineTo(mHalfPadding, height - mHalfPadding);

        // Rotate it
        Matrix matrix = new Matrix();
        RectF bounds = new RectF();
        mPath.computeBounds(bounds, true);
        matrix.postRotate(90.0f * progress, (bounds.right + bounds.left) / 2, (bounds.bottom + bounds.top) / 2);
        mPath.transform(matrix);
    }

    private void transitionStopToPause(float progress) {
        final int width = getBounds().width();
        final int height = getBounds().height();
        final int halfWidth = height / 2;

        mPath.reset();

        // We glue two half-square together, which we then split and slightly trim (10%)
        mPath.addRect(mHalfPadding,
                mHalfPadding,
                halfWidth - halfWidth * PAUSE_TRIM_RATIO * progress,
                height - mHalfPadding,
                Path.Direction.CW);

        mPath.addRect(halfWidth + halfWidth * PAUSE_TRIM_RATIO * progress,
                mHalfPadding,
                width - mHalfPadding,
                height - mHalfPadding,
                Path.Direction.CW);


        /*
        Another look (centering the pause bars instead of just splitting in the middle):
        Set PAUSE_TRIM_RATIO to 0.14f

        mPath.addRect(mHalfPadding + halfWidth * PAUSE_TRIM_RATIO * progress,
        mHalfPadding,
                halfWidth - halfWidth * PAUSE_TRIM_RATIO * progress,
                height - mHalfPadding,
                Path.Direction.CW);

        mPath.addRect(halfWidth + halfWidth * PAUSE_TRIM_RATIO * progress,
                mHalfPadding,
                width - mHalfPadding - halfWidth * PAUSE_TRIM_RATIO * progress,
                height - mHalfPadding,
                Path.Direction.CW);
         */
    }

    private void transitionPlayToPause(float progress) {
        // Same as Play to Stop for half of the animation, then we split the rectangle in two to
        // make the pause shape
        if (progress < 0.75f) {
            transitionPlayToStop(progress * (1.0f / 0.75f));
        } else {
            float localProgress = (progress - 0.75f) * (1.0f / 0.25f);
            transitionStopToPause(localProgress);
        }
    }

    private void transitionPauseToPlay(float progress) {
        transitionPlayToPause(1.0f - progress);
    }

    private void transitionPauseToStop(float progress) {
        transitionStopToPause(1.0f - progress);
    }

    private void transitionStopToPlay(float progress) {
        transitionPlayToStop(1.0f - progress);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.translate(0, -mYOffset);

        if (!mInitialDrawDone) {
            shapeInitialPath();
        }

        if (mCurrentShape != mRequestShape) {
            final float progress = mTransitionInterpolator.getInterpolation(getProgress());

            // Play to Pause
            if (mCurrentShape == SHAPE_PLAY && mRequestShape == SHAPE_PAUSE) {
                transitionPlayToPause(progress);
            }
            // Play to Stop
            else if (mCurrentShape == SHAPE_PLAY && mRequestShape == SHAPE_STOP) {
                transitionPlayToStop(progress);
            }
            // Pause to Play
            else if (mCurrentShape == SHAPE_PAUSE && mRequestShape == SHAPE_PLAY) {
                transitionPauseToPlay(progress);
            }
            // Pause to Stop
            else if (mCurrentShape == SHAPE_PAUSE && mRequestShape == SHAPE_STOP) {
                transitionPauseToStop(progress);
            }
            // Stop to Pause
            else if (mCurrentShape == SHAPE_STOP && mRequestShape == SHAPE_PAUSE) {
                transitionStopToPause(progress);
            }
            // Stop to Play
            else if (mCurrentShape == SHAPE_STOP && mRequestShape == SHAPE_PLAY) {
                transitionStopToPlay(progress);
            }
            else {
                Log.e(TAG, "Unhandled transition from " + mCurrentShape + " to " + mRequestShape);
            }

            if (progress >= 1.0f) {
                mCurrentShape = mRequestShape;
            } else {
                mTransitionAccumulator += System.currentTimeMillis() - mLastTransitionTick;
                mLastTransitionTick = System.currentTimeMillis();
            }
        }

        canvas.drawPath(mPath, mPaint);

        if (mIsBuffering) {
            if (mBufferingDrawable == null) {
                Rect bounds = getBounds();
                mBufferingDrawable = new CircularAnimatedDrawable(0xFFFFFFFF, 8.0f);
                mBufferingDrawable.setBounds((bounds.left + mHalfPadding),
                        (bounds.top + mHalfPadding),
                        (bounds.right - mHalfPadding),
                        (bounds.bottom - mHalfPadding));
                mBufferingDrawable.setCallback(getCallback());
                mBufferingDrawable.start();
            }
            mBufferingDrawable.draw(canvas);
        }

        if (mCurrentShape != mRequestShape || mIsBuffering) {
            // Invalidate ourselves to redraw the next animation frame
            invalidateSelf();
        }

        canvas.translate(0, mYOffset);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int i) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return 255;
    }
}
