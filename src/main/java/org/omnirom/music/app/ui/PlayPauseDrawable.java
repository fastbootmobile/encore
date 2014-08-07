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
import android.os.Handler;
import android.util.Log;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.omnirom.music.app.R;

/**
 * Created by xplodwild on 7/11/14.
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

    private Resources mResources;
    private Handler mHandler;


    public PlayPauseDrawable(Resources res) {
        mResources = res;
        setPaddingDp(32);
        mCurrentShape = mRequestShape = -1;
        mPath = new Path();
        mPaint = new Paint();
        mHandler = new Handler();

        mPaint.setColor(0xCCFFFFFF);
        mPaint.setStyle(Paint.Style.FILL);

        mInitialDrawDone = false;
        mTransitionInterpolator = new AccelerateDecelerateInterpolator();
    }

    public void setColor(int color) {
        mPaint.setColor(color);
    }

    public void setPaddingDp(int paddingDp) {
        setPadding(mResources.getDimensionPixelSize(R.dimen.one_dp) * paddingDp) ;
    }

    public void setPadding(int padding) {
        mHalfPadding = padding / 2;
    }

    public void setShape(int shape) {
        if (mCurrentShape != shape || mRequestShape != mCurrentShape) {
            mRequestShape = shape;
            mTransitionAccumulator = 0;
            mLastTransitionTick = System.currentTimeMillis();
            invalidateSelf();
        }
    }

    public int getCurrentShape() {
        return mCurrentShape;
    }

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
        final int width = getBounds().width();
        final int height = getBounds().height();

        switch (mRequestShape) {
            case SHAPE_PAUSE:
                mPath.addRect(mHalfPadding + width * 0.5f * PAUSE_TRIM_RATIO,
                        mHalfPadding,
                        width * 0.5f - width * 0.5f * 0.07f,
                        height - mHalfPadding,
                        Path.Direction.CW);

                mPath.addRect(width * 0.5f + width * 0.5f * PAUSE_TRIM_RATIO,
                        mHalfPadding,
                        width - mHalfPadding - width * 0.5f * PAUSE_TRIM_RATIO,
                        height - mHalfPadding,
                        Path.Direction.CW);
                break;

            case SHAPE_PLAY:
                mPath.moveTo(mHalfPadding, mHalfPadding);
                mPath.lineTo(width - mHalfPadding, height / 2);
                mPath.lineTo(mHalfPadding, height - mHalfPadding);
                break;

            case SHAPE_STOP:
                mPath.addRect(mHalfPadding, mHalfPadding, width - mHalfPadding, height - mHalfPadding, Path.Direction.CW);
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

        if (mCurrentShape != mRequestShape) {
            // Invalidate ourselves to redraw the next animation frame
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    invalidateSelf();
                }
            });
        }
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
