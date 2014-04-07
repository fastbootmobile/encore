package org.omnirom.music.app.ui;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlend;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.omnirom.music.app.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by f.laurent on 21/11/13.
 * Original code by Flavien Laurant
 */
public class KenBurnsView extends FrameLayout {

    private static final String TAG = "KenBurnsView";

    private final Handler mHandler;
    private final List<Bitmap> mBitmaps;
    private ImageView[] mImageViews;
    private int mActiveImageIndex = -1;

    private final Random random = new Random();
    private int mSwapMs = 10000;
    private int mFadeInOutMs = 400;
    private boolean mHasStarted = false;

    private float maxScaleFactor = 1.5F;
    private float minScaleFactor = 1.2F;

    private final RenderScript mRS;
    private final Bitmap mTintBitmap = Bitmap.createBitmap(new int[]{0x70400000},
            1, 1, Bitmap.Config.ARGB_8888);
    private final ScriptIntrinsicBlur mBlur;
    private final ScriptIntrinsicBlend mBlend;

    private Runnable mSwapImageRunnable = new Runnable() {
        @Override
        public void run() {
            swapImage();
            mHandler.postDelayed(mSwapImageRunnable, mSwapMs - mFadeInOutMs * 2);
        }
    };

    private class AddBitmapRunnable implements Runnable {

        private Bitmap mBitmap;

        public AddBitmapRunnable(final Bitmap bmp) {
            mBitmap = bmp;
        }

        @Override
        public void run() {
            Allocation input = Allocation.createFromBitmap(mRS, mBitmap,
                    Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            Allocation output = Allocation.createTyped(mRS, input.getType());

            // Blur the image
            mBlur.setInput(input);

            mBlur.setRadius(25);
            mBlur.forEach(output);

            // Dim down images with a tint color
            input = Allocation.createFromBitmap(mRS,
                    Bitmap.createScaledBitmap(mTintBitmap,
                            mBitmap.getWidth(),
                            mBitmap.getHeight(), false));

            mBlend.forEachSrcOver(input, output);

            // We're done processing
            output.copyTo(mBitmap);

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBitmaps.add(mBitmap);
                    fillImageViews();

                    if (mBitmaps.size() == 1) {
                        swapImage();
                    }
                }
            });
        }
    }

    public KenBurnsView(Context context) {
        this(context, null);
    }

    public KenBurnsView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KenBurnsView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();
        mBitmaps = new ArrayList<Bitmap>();
        mRS = RenderScript.create(getContext());
        mBlur = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        mBlend = ScriptIntrinsicBlend.create(mRS, Element.U8_4(mRS));
    }

    public void addBitmap(final Bitmap bmp) {
        new Thread(new AddBitmapRunnable(bmp)).start();
    }

    private void swapImage() {
        if (mBitmaps.size() == 0) return;

        if(mActiveImageIndex == -1) {
            mActiveImageIndex = 1;
            animate(mImageViews[mActiveImageIndex]);
            return;
        }

        int inactiveIndex = mActiveImageIndex;
        mActiveImageIndex = (1 + mActiveImageIndex) % mImageViews.length;

        final ImageView activeImageView = mImageViews[mActiveImageIndex];
        activeImageView.setAlpha(0.0f);
        ImageView inactiveImageView = mImageViews[inactiveIndex];

        animate(activeImageView);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.setDuration(mFadeInOutMs);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(inactiveImageView, "alpha", 1.0f, 0.0f),
                ObjectAnimator.ofFloat(activeImageView, "alpha", 0.0f, 1.0f)
        );
        animatorSet.start();
    }

    private void start(View view, long duration, float fromScale, float toScale, float fromTranslationX, float fromTranslationY, float toTranslationX, float toTranslationY) {
        view.setScaleX(fromScale);
        view.setScaleY(fromScale);
        view.setTranslationX(fromTranslationX);
        view.setTranslationY(fromTranslationY);
        ViewPropertyAnimator propertyAnimator = view.animate().translationX(toTranslationX).translationY(toTranslationY).scaleX(toScale).scaleY(toScale).setDuration(duration);
        propertyAnimator.start();
    }

    private float pickScale() {
        return this.minScaleFactor + this.random.nextFloat() * (this.maxScaleFactor - this.minScaleFactor);
    }

    private float pickTranslation(int value, float ratio) {
        return value * (ratio - 1.0f) * (this.random.nextFloat() - 0.5f);
    }

    public void animate(View view) {
        float fromScale = pickScale();
        float toScale = pickScale();
        float fromTranslationX = pickTranslation(view.getWidth(), fromScale);
        float fromTranslationY = pickTranslation(view.getHeight(), fromScale);
        float toTranslationX = pickTranslation(view.getWidth(), toScale);
        float toTranslationY = pickTranslation(view.getHeight(), toScale);
        start(view, this.mSwapMs, fromScale, toScale, fromTranslationX, fromTranslationY, toTranslationX, toTranslationY);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startKenBurnsAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mHandler.removeCallbacks(mSwapImageRunnable);
    }

    private void startKenBurnsAnimation() {
        if (!mHasStarted) {
            mHasStarted = true;
            mHandler.post(mSwapImageRunnable);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View view = inflate(getContext(), R.layout.view_kenburns, this);

        mImageViews = new ImageView[2];
        mImageViews[0] = (ImageView) view.findViewById(R.id.image0);
        mImageViews[1] = (ImageView) view.findViewById(R.id.image1);
    }

    private void fillImageViews() {
        for (int i = 0; i < mImageViews.length; i++) {
            if (i >= mBitmaps.size()) break;
            mImageViews[i].setImageBitmap(mBitmaps.get(i));
        }

        if (mBitmaps.size() == 1) {
            // Fill the second imageview with the first image as well
            mImageViews[1].setImageBitmap(mBitmaps.get(0));
        }
    }
}
