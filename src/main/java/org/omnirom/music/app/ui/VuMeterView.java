package org.omnirom.music.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * VUMeter view
 */
public class VuMeterView extends View {

    private Paint mGreenPaint;
    private Paint mRedPaint;
    private float mTargetAmplitude = -72.0f;
    private float mCurrentAmplitude = -72.0f;
    private RectF mBounds;


    public VuMeterView(Context context) {
        super(context);
        init(context);
    }

    public VuMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        mGreenPaint = new Paint();
        // #669900
        mGreenPaint.setARGB(255, 102, 153, 0);
        mGreenPaint.setStyle(Paint.Style.FILL);

        mRedPaint = new Paint();
        // #CC0000
        mRedPaint.setARGB(255, 204, 0, 0);
        mRedPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mBounds = new RectF(0, 0, w, h);
    }

    /**
     * Sets the displayed amplitude of this meter. Note that the values must be normalized at 0dB,
     * that the red part starts at -3dB, and that the minimum displayed value is -48dB.
     *
     * @param amplitude The amplitude, in dB
     */
    public void setAmplitude(float amplitude) {
        mTargetAmplitude = amplitude;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mTargetAmplitude < -48) return;

        final float percentageRed = 3.0f/48.0f;
        final float percentageGreen = 1.0f - percentageRed;

        final float maxGreenHeight = mBounds.height() * percentageGreen;
        final float maxRedHeight = mBounds.height() * percentageRed;

        float deltaAmplitude = mTargetAmplitude - mCurrentAmplitude;
        mCurrentAmplitude += deltaAmplitude / 4.0f;

        /**
         * From -48 to -3 ==> green
         * 3 ==> -45 to 0
         */
        if (mTargetAmplitude >= -3.0f) {
            // left, top, right, bottom, paint
            // at 0 ==> maxRedHeight - 0
            // at -3 ==> maxRedHeight - maxRedHeight
            canvas.drawRect(mBounds.left, mBounds.top + maxRedHeight * -(mCurrentAmplitude / 3.0f),
                    mBounds.right, mBounds.bottom, mRedPaint);
            canvas.drawRect(mBounds.left, mBounds.top + maxRedHeight,
                    mBounds.right, mBounds.bottom, mGreenPaint);

        } else {
            canvas.drawRect(mBounds.left, mBounds.top + maxRedHeight + maxGreenHeight * -((mCurrentAmplitude + 3.0f) / 45.0f),
                    mBounds.right, mBounds.bottom, mGreenPaint);
        }

        if (Math.abs(mTargetAmplitude - mCurrentAmplitude) > 0.01f) {
            // Redraw again for a smooth bar
            invalidate();
        }
    }
}
