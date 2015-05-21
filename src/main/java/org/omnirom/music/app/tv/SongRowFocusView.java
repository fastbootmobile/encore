package org.omnirom.music.app.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.omnirom.music.utils.Utils;

public class SongRowFocusView extends View {
    private final Paint mPaint;
    private final RectF mRoundRectF = new RectF();
    private final int mRoundRectRadius;

    public SongRowFocusView(Context ctx) {
        super(ctx);
        mPaint = createPaint(ctx);
        mRoundRectRadius = getRoundRectRadius(ctx);
    }

    public SongRowFocusView(Context ctx, AttributeSet attrSet) {
        super(ctx, attrSet);
        mPaint = createPaint(ctx);
        mRoundRectRadius = getRoundRectRadius(ctx);
    }

    public SongRowFocusView(Context ctx, AttributeSet attrSet, int style) {
        super(ctx, attrSet, style);
        mPaint = createPaint(ctx);
        mRoundRectRadius = getRoundRectRadius(ctx);
    }

    private Paint createPaint(Context ctx) {
        Paint localPaint = new Paint();
        localPaint.setColor(0xFFFF0000);
        return localPaint;
    }

    private int getRoundRectRadius(Context ctx) {
        return Utils.dpToPx(ctx.getResources(), 3);
    }

    protected void onDraw(Canvas paramCanvas) {
        super.onDraw(paramCanvas);
        int i = (2 * this.mRoundRectRadius - getHeight()) / 2;
        this.mRoundRectF.set(0.0F, -i, getWidth(), i + getHeight());
        paramCanvas.drawRoundRect(this.mRoundRectF, this.mRoundRectRadius, this.mRoundRectRadius, this.mPaint);
    }
}
