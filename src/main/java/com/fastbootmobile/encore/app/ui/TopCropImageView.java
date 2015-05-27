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
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ImageView that crops and align the image at the top instead of a vertical center
 */
public class TopCropImageView extends ImageView {
    public TopCropImageView(Context context) {
        super(context);
        setScaleType(ScaleType.MATRIX);
    }

    public TopCropImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setScaleType(ScaleType.MATRIX);
    }

    public TopCropImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setScaleType(ScaleType.MATRIX);
    }

    @Override
    protected boolean setFrame(int l, int t, int r, int b) {
        final Matrix matrix = getImageMatrix();
        final float width = getMeasuredWidth();
        final float height = getMeasuredHeight();
        if (getDrawable() != null) {
            final float intrinsicWidth = getDrawable().getIntrinsicWidth();
            final float intrinsicHeight = getDrawable().getIntrinsicHeight();

            final float scaleFactor = Math.max(width / intrinsicWidth, height / intrinsicHeight);

            matrix.setScale(scaleFactor, scaleFactor, 0, 0);
            setImageMatrix(matrix);
        }

        return super.setFrame(l, t, r, b);
    }
}