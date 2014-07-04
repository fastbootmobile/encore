package org.omnirom.music.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.omnirom.music.app.R;

/**
 * Frame layout that blurs its bottom contents and put it to the provided render image view
 */
@SuppressWarnings("SynchronizeOnNonFinalField")
public class BlurringFrameLayout extends FrameLayout {

    /**
     * The resolution of the blurred bitmap will be divided by this factor
     */
    private static final int RESOLUTION_DIVIDER = 4;
    private boolean mIsInitialized = false;

    private Allocation mInput;
    private Allocation mOutput;
    private Bitmap mViewBitmap;
    private Canvas mViewCanvas;
    private Bitmap mCroppedBitmap;
    private Bitmap mCroppedBlurredBitmap;
    private Canvas mCroppedCanvas;
    private RenderScript mRenderScript;
    private ScriptIntrinsicBlur mIntrinsicBlur;
    private Handler mHandler = new Handler();
    private Rect mClipBoundsRect;
    private Rect mBmpSourceRect;
    private Rect mBmpDestRect;
    private ImageView mImageRender;
    private Paint mBitmapPaint;
    private boolean mIsBlurring = false;

    /**
     * Thread responsible of blurring the image
     */
    private final Thread mBlurThread = new Thread() {
        public void run() {
            while (!isInterrupted()) {
                synchronized (this) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mIsBlurring = true;
                }

                mInput.copyFrom(mCroppedBitmap);
                mIntrinsicBlur.forEach(mOutput);

                synchronized (mCroppedBlurredBitmap) {
                    mOutput.copyTo(mCroppedBlurredBitmap);
                }

                synchronized (this) {
                    mIsBlurring = false;
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        synchronized (mCroppedBlurredBitmap) {
                            if (mImageRender != null) {
                                mImageRender.setImageBitmap(mCroppedBlurredBitmap);
                            }
                        }
                    }
                });
            }
        }
    };

    public BlurringFrameLayout(Context context) {
        super(context);
        init();
    }

    public BlurringFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BlurringFrameLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        if (!isInEditMode()) {
            mRenderScript = RenderScript.create(getContext());
            mIntrinsicBlur = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));

            mBitmapPaint = new Paint();
            mBitmapPaint.setAntiAlias(true);
            mBitmapPaint.setDither(true);
            mBitmapPaint.setFilterBitmap(true);

            ViewTreeObserver treeObserver = getViewTreeObserver();

            if (treeObserver != null) {
                treeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        renderBlur();
                    }
                });
                treeObserver.addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                    @Override
                    public void onScrollChanged() {
                        renderBlur();
                    }
                });
            }
        }
    }

    private void renderBlur() {
        final Resources resources = getResources();
        assert resources != null;
        final int BAR_HEIGHT = resources.getDimensionPixelSize(R.dimen.playing_bar_height);
        final int BG_COLOR = resources.getColor(R.color.default_fragment_background);

        if (!mIsBlurring) {
            if (!mIsInitialized) {
                mIsInitialized = true;

                final int measuredHeight = getMeasuredHeight();
                final int measuredWidth = getMeasuredWidth();

                mViewBitmap = Bitmap.createBitmap(measuredWidth, measuredHeight,
                        Bitmap.Config.ARGB_4444);
                mViewCanvas = new Canvas(mViewBitmap);
                mCroppedBitmap = Bitmap.createBitmap(measuredWidth / RESOLUTION_DIVIDER,
                        BAR_HEIGHT / RESOLUTION_DIVIDER, Bitmap.Config.ARGB_4444);
                mCroppedBlurredBitmap = Bitmap.createBitmap(measuredWidth / RESOLUTION_DIVIDER,
                        BAR_HEIGHT / RESOLUTION_DIVIDER, Bitmap.Config.ARGB_4444);
                mCroppedCanvas = new Canvas(mCroppedBitmap);

                mClipBoundsRect = new Rect(0, measuredHeight - BAR_HEIGHT,
                        measuredWidth, measuredHeight);
                mBmpSourceRect = new Rect(0, measuredHeight - BAR_HEIGHT,
                        measuredWidth, measuredHeight);
                mBmpDestRect = new Rect(0, 0, mCroppedBitmap.getWidth(),
                        mCroppedBitmap.getHeight());

                mInput = Allocation.createFromBitmap(mRenderScript, mCroppedBitmap);
                mOutput = Allocation.createTyped(mRenderScript, mInput.getType());

                mIntrinsicBlur.setInput(mInput);
                mIntrinsicBlur.setRadius(6);

                mBlurThread.start();
            }

            // We first render the view into a full-resolution bitmap. We however only render
            // the part that interests us to speed up.
            // Is there a way to render that directly in a scaled bitmap instead of having to
            // render a fully sized bitmap with 90% emptiness?
            setClipChildren(true);
            setClipBounds(mClipBoundsRect);
            mViewCanvas.drawColor(BG_COLOR);
            draw(mViewCanvas);

            // We then draw only the interesting cropped part, scaled, into the bitmap that will be
            // blurred.
            mCroppedCanvas.drawBitmap(mViewBitmap, mBmpSourceRect, mBmpDestRect, mBitmapPaint);

            // We tint it white a bit
            // mCroppedCanvas.drawColor(0x50FFFFFF);

            synchronized (mBlurThread) {
                // And we notify the thread it can go ahead and blur it
                mBlurThread.notify();
            }

            // Reset clipping to render the view properly
            setClipChildren(false);
            setClipBounds(null);
        }
    }

    public void setImageRender(ImageView iv) {
        mImageRender = iv;
    }

}
