package org.omnirom.music.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * Created by xplodwild on 7/7/14.
 */
public class SquareImageView extends ImageView {

    public SquareImageView(Context context) {
        super(context);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // Only set a square size if we're not defining an exact size
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT
            || layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            int width = getMeasuredWidth();
            setMeasuredDimension(width, width);
        }
    }

}