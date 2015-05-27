/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Rahat Ahmed
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package mbanje.kurt.fabbutton;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.fastbootmobile.encore.app.R;


public class ProgressRingView extends View implements FabUtil.OnFabValueCallback {
    private static final String TAG = ProgressRingView.class.getSimpleName();

    private Paint mProgressPaint;
    private int mSize = 0;
    private RectF mBounds;
    private float mBoundsPadding = 0.14f;
    private int mViewRadius;
    private float mRingWithRatio = 0.14f; //of a possible 1f;
    private boolean mIndeterminate = false, mAutoStartAnim;
    private boolean mWasIndeterminate;
    private float mProgress, mMaxProgress, mIndeterminateSweep, mIndeterminateRotateOffset;
    private int mRingWidth, mMidRingWidth, mAnimDuration;
    private int mProgressColor = Color.BLACK;


    // Animation related stuff
    private float mStartAngle;
    private float mActualProgress;
    private ValueAnimator mStartAngleRotate;
    private ValueAnimator mProgressAnimation;
    private AnimatorSet mIndeterminateAnimator;

    private CircleImageView.OnFabViewListener fabViewListener;

    public ProgressRingView(Context context) {
        super(context);
        init(null, 0);
    }

    public ProgressRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ProgressRingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    protected void init(AttributeSet attrs, int defStyle) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CircleImageView, defStyle, 0);
        mProgress = a.getFloat(R.styleable.CircleImageView_android_progress, 0f);
        mProgressColor = a.getColor(R.styleable.CircleImageView_fbb_progressColor, mProgressColor);
        mMaxProgress = a.getFloat(R.styleable.CircleImageView_android_max, 100f);
        mIndeterminate = a.getBoolean(R.styleable.CircleImageView_android_indeterminate, false);
        mAutoStartAnim = a.getBoolean(R.styleable.CircleImageView_fbb_autoStart, true);
        mAnimDuration = a.getInteger(R.styleable.CircleImageView_android_indeterminateDuration, 4000);
        mRingWithRatio = a.getFloat(R.styleable.CircleImageView_fbb_progressWidthRatio, mRingWithRatio);
        a.recycle();
        mProgressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mProgressPaint.setColor(mProgressColor);
        mProgressPaint.setStyle(Paint.Style.STROKE);
        mProgressPaint.setStrokeCap(Paint.Cap.BUTT);
        if (mAutoStartAnim) {
            startAnimation();
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mSize = Math.min(w, h);
        mViewRadius = mSize / 2;
        setRingWidth(-1, true);
    }

    public void setRingWithRatio(float ringWithRatio) {
        this.mRingWithRatio = ringWithRatio;
    }

    public void setAutostartanim(boolean autostartanim) {
        this.mAutoStartAnim = autostartanim;
    }

    public void setFabViewListener(CircleImageView.OnFabViewListener fabViewListener) {
        this.fabViewListener = fabViewListener;
    }

    public void setRingWidth(int width, boolean original) {
        if (original) {
            mRingWidth = Math.round((float) mViewRadius * mRingWithRatio);
        } else {
            mRingWidth = width;
        }
        mMidRingWidth = mRingWidth / 2;
        mProgressPaint.setStrokeWidth(mRingWidth);
        updateBounds();
    }

    private void updateBounds() {
        mBounds = new RectF(mMidRingWidth, mMidRingWidth, mSize - mMidRingWidth, mSize - mMidRingWidth);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        // Draw the arc
        float sweepAngle = isInEditMode() ? (mProgress / mMaxProgress * 360) : (mActualProgress / mMaxProgress * 360);
        if (!mIndeterminate) {
            canvas.drawArc(mBounds, mStartAngle, sweepAngle, false, mProgressPaint);
        } else {
            canvas.drawArc(mBounds, mStartAngle + mIndeterminateRotateOffset, mIndeterminateSweep, false, mProgressPaint);
        }
    }


    /**
     * Sets the mProgress of the mProgress bar.
     *
     * @param currentProgress the current mProgress you want to set
     */
    public void setProgress(final float currentProgress) {
        this.mProgress = currentProgress;
        // Reset the determinate animation to approach the new mProgress
        if (!mIndeterminate && mWasIndeterminate) {
            mWasIndeterminate = false;
            if (mProgressAnimation != null && mProgressAnimation.isRunning()) {
                mProgressAnimation.cancel();
            }
            mProgressAnimation = FabUtil.createProgressAnimator(this, mActualProgress, currentProgress, this);
            mProgressAnimation.start();
        } else {
            mActualProgress = currentProgress;
        }

        invalidate();

    }


    public void setMaxProgress(float maxProgress) {
        this.mMaxProgress = maxProgress;
    }

    public float getMaxProgress() {
        return this.mMaxProgress;
    }

    public void setIndeterminate(boolean indeterminate) {
        if (mIndeterminate == indeterminate) {
            return;
        }

        this.mIndeterminate = indeterminate;
        if (indeterminate) {
            mWasIndeterminate = true;
        }
        resetAnimation();
        invalidate();
    }

    public void setAnimDuration(int animDuration) {
        this.mAnimDuration = animDuration;
    }


    public void setProgressColor(int progressColor) {
        this.mProgressColor = progressColor;
        mProgressPaint.setColor(progressColor);
    }


    /**
     * Starts the mProgress bar animation.
     * (This is an alias of resetAnimation() so it does the same thing.)
     */
    public void startAnimation() {
        resetAnimation();
    }


    public void stopAnimation(boolean hideProgress) {
        if (mStartAngleRotate != null && mStartAngleRotate.isRunning()) {
            mStartAngleRotate.cancel();
        }
        if (mProgressAnimation != null && mProgressAnimation.isRunning()) {
            mProgressAnimation.cancel();
        }
        if (mIndeterminateAnimator != null && mIndeterminateAnimator.isRunning()) {
            mIndeterminateAnimator.cancel();
        }
        if (hideProgress) {
            setRingWidth(0, false);
        } else {
            setRingWidth(0, true);
        }
        invalidate();
    }

    /**
     * Resets the animation.
     */
    public void resetAnimation() {
        stopAnimation(false);
        // Determinate animation
        if (!mIndeterminate) {
            // The cool 360 swoop animation at the start of the animation
            mStartAngle = -90f;
            mStartAngleRotate = FabUtil.createStartAngleAnimator(this, -90f, 270f, this);
            mStartAngleRotate.start();
            // The linear animation shown when mProgress is updated
            mActualProgress = 0f;
            mProgressAnimation = FabUtil.createProgressAnimator(this, mActualProgress, mProgress, this);
            mProgressAnimation.start();
        } else { // Indeterminate animation
            mStartAngle = -90f;
            mIndeterminateSweep = FabUtil.INDETERMINANT_MIN_SWEEP;
            // Build the whole AnimatorSet
            mIndeterminateAnimator = new AnimatorSet();
            AnimatorSet prevSet = null, nextSet;
            for (int k = 0; k < FabUtil.ANIMATION_STEPS; k++) {
                nextSet = FabUtil.createIndeterminateAnimator(this, k, mAnimDuration, this);
                AnimatorSet.Builder builder = mIndeterminateAnimator.play(nextSet);
                if (prevSet != null) {
                    builder.after(prevSet);
                }
                prevSet = nextSet;
            }

            // Listen to end of animation so we can infinitely loop
            mIndeterminateAnimator.addListener(new AnimatorListenerAdapter() {
                boolean wasCancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    wasCancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!wasCancelled) {
                        resetAnimation();
                    }
                }
            });
            mIndeterminateAnimator.start();
        }
    }

    @Override
    public void onIndeterminateValuesChanged(float indeterminateSweep, float indeterminateRotateOffset, float startAngle, float progress) {
        if (indeterminateSweep != -1) {
            this.mIndeterminateSweep = indeterminateSweep;
        }
        if (indeterminateRotateOffset != -1) {
            this.mIndeterminateRotateOffset = indeterminateRotateOffset;
        }
        if (startAngle != -1) {
            this.mStartAngle = startAngle;
        }
        if (progress != -1) {
            this.mActualProgress = progress;
            if (Math.round(mActualProgress) == 100 && fabViewListener != null) {
                fabViewListener.onProgressCompleted();
            }
        }
    }
}
