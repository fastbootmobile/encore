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

package com.fastbootmobile.encore.app.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;

/**
 * ImageView that remains a square aspect ratio (if no exact size has been set)
 */
public class SquareImageView extends ImageView {

    private boolean mDisableSquared = false;

    public SquareImageView(Context context) {
        super(context);
    }

    public SquareImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SquareImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setForceDisableSquared(boolean disable) {
        mDisableSquared = disable;
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (!mDisableSquared) {
            // Only set a square size if we're not defining an exact size
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            if ((layoutParams.width == ViewGroup.LayoutParams.MATCH_PARENT
                    || layoutParams.width == ViewGroup.LayoutParams.WRAP_CONTENT)
                    && (layoutParams.height == ViewGroup.LayoutParams.MATCH_PARENT
                    || layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT)) {
                int width = getMeasuredWidth();
                setMeasuredDimension(width, width);
            }
        }
    }

}