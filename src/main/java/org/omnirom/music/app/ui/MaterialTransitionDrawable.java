
package org.omnirom.music.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.renderscript.RenderScript;
import android.view.animation.AccelerateDecelerateInterpolator;

import org.omnirom.music.app.renderscript.GrayscaleRS;

/**
 * <p>
 * Class that allows drawable transitions in a way that fits Google's Material Design specifications
 * (see <a href="http://www.google.com/design/spec/patterns/imagery-treatment.html">Material Design
 * pattern 'Imagery Treatment'</a>).
 * </p>
 */
public class MaterialTransitionDrawable extends Drawable {

    private static final String TAG = "MaterialTransitionDrawable";
    public static final long DEFAULT_DURATION = 1000;

    private BitmapDrawable mBaseDrawable;
    private BitmapDrawable mTargetDrawable;
    private BitmapDrawable mTargetGrayDrawable;
    private final AccelerateDecelerateInterpolator mInterpolator;
    private long mStartTime;
    private boolean mAnimating;
    private long mDuration = DEFAULT_DURATION;
    private Handler mHandler;

    private static final Object mGrayscalerSync = new Object();
    private static GrayscaleRS mGrayscaler;


    public MaterialTransitionDrawable(Context ctx, Bitmap base) {
        this(ctx, new BitmapDrawable(ctx.getResources(), base));
    }

    public MaterialTransitionDrawable(Context ctx, BitmapDrawable base) {
        this(ctx);
        mBaseDrawable = base;
        invalidateSelf();
    }

    public MaterialTransitionDrawable(Context ctx) {
        mInterpolator = new AccelerateDecelerateInterpolator();
        mAnimating = false;
        mHandler = new Handler();

        synchronized (mGrayscalerSync) {
            // We use a shared Grayscale script to avoid allocating multiple time the RenderScript
            // context.
            if (mGrayscaler == null) {
                RenderScript renderScript = RenderScript.create(ctx);
                mGrayscaler = new GrayscaleRS(renderScript);
            }
        }
    }

    public BitmapDrawable getFinalDrawable() {
        if (mTargetDrawable != null) {
            return mTargetDrawable;
        } else {
            return mBaseDrawable;
        }
    }

    public void setTransitionDuration(long durationMillis) {
        mDuration = durationMillis;
    }

    public void setImmediateTo(BitmapDrawable drawable) {
        // Cancel animation
        mAnimating = false;
        mTargetDrawable = null;

        // Set new drawable as base and draw it
        mBaseDrawable = drawable;
        mBaseDrawable.setBounds(getBounds());
        invalidateSelf();
    }

    public void transitionTo(Resources res, Bitmap bitmap) {
        transitionTo(res, new BitmapDrawable(res, bitmap));
    }

    public void transitionTo(final Resources res, final BitmapDrawable drawable) {
        if (drawable != mTargetDrawable) {
            mTargetDrawable = drawable;
            mTargetGrayDrawable = null;

            // The gray drawable will be generated in a separate thread to avoid hogging the UI
            // thread when calling transitionTo from it
            new Thread() {
                public void run() {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    final Bitmap bmp = drawable.getBitmap();
                    if (bmp != null && bmp.getWidth() > 0 && bmp.getHeight() > 0
                            && bmp.getConfig() != null) {
                        BitmapDrawable grayDrawable = new BitmapDrawable(res, grayscaleBitmap(bmp));
                        synchronized (MaterialTransitionDrawable.this) {
                            mTargetGrayDrawable = grayDrawable;
                            mTargetGrayDrawable.setBounds(getBounds());

                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    invalidateSelf();
                                }
                            });
                        }
                    }
                }
            }.start();


            mTargetDrawable.setBounds(getBounds());

            mStartTime = -1;
            mAnimating = true;
        }
    }


    private Bitmap grayscaleBitmap(Bitmap bmpOriginal) {
        return mGrayscaler.apply(bmpOriginal);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);

        if (mBaseDrawable != null & !mAnimating) {
            mBaseDrawable.setBounds(bounds);
        }
        if (mTargetDrawable != null) {
            mTargetDrawable.setBounds(bounds);
        }
        if (mTargetGrayDrawable != null) {
            mTargetGrayDrawable.setBounds(bounds);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mAnimating && mTargetGrayDrawable != null) {
            if (mStartTime < 0) {
                mStartTime = SystemClock.uptimeMillis();
            }

            final float rawProgress = Math.min(1.0f,
                    ((float) (SystemClock.uptimeMillis() - mStartTime)) / ((float) mDuration));

            // As per the Material Design spec, animation goes into 3 steps. Ranging from 0 to 100,
            // opacity is full at 50, exposure (gamma + black output) at 75, and saturation at 100.
            // TODO: Use a real shader for the real full effect. For now, we fade in the grayscale
            // TODO: then the full image. That's not quite exactly what the guideline says, but
            // TODO: close enough.
            final float inputOpacity = Math.min(1.0f, rawProgress * (1.0f / 0.5f));
            //final float inputExposure = Math.min(1.0f, rawProgress * (1.0f / 0.75f));

            final float progressOpacity = mInterpolator.getInterpolation(inputOpacity);
            //final float progressExposure = mInterpolator.getInterpolation(inputExposure);
            final float progressSaturation = mInterpolator.getInterpolation(rawProgress);

            if (mBaseDrawable != null) {
                drawTranslatedBase(canvas);
            }

            mTargetGrayDrawable.setAlpha((int) (255 * progressOpacity));
            mTargetGrayDrawable.draw(canvas);

            mTargetDrawable.setAlpha((int) (255 * progressSaturation));
            mTargetDrawable.draw(canvas);

            if (rawProgress >= 1.0f) {
                mAnimating = false;

                mBaseDrawable = mTargetDrawable;

                // Release the grayscale bitmap, we don't need it anymore from now on
                mTargetGrayDrawable.getBitmap().recycle();
                mTargetGrayDrawable = null;
            } else {
                invalidateSelf();
            }
        } else if (mAnimating && mBaseDrawable != null) {
            // While grayscale is generating, we might already get the new dimensions for the
            // target (non grayscale) drawable. In that case, we draw the base drawable in the
            // transformed canvas
            drawTranslatedBase(canvas);
        } else if (mBaseDrawable != null) {
            mBaseDrawable.draw(canvas);
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

        float scaling = Math.min(targetWidth / baseWidth, targetHeight / baseHeight);

        final float scaledBaseWidth = scaling * baseWidth;
        final float scaledBaseHeight = scaling * baseHeight;

        canvas.translate((targetWidth - scaledBaseWidth) * 0.5f,
                (targetHeight - scaledBaseHeight) * 0.5f);
        canvas.scale(scaling, scaling);

        mBaseDrawable.draw(canvas);
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

    }

    @Override
    public int getOpacity() {
        return 255;
    }
}