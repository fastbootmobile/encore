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

package org.omnirom.music.framework;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;

/**
 * Ref-counted aware Bitmap Drawable
 */
public class RecyclingBitmapDrawable extends BitmapDrawable {
    private Bitmap mBitmap;

    public RecyclingBitmapDrawable(Resources res, RefCountedBitmap bitmap) {
        super(res, bitmap.get().copy(bitmap.get().getConfig(), false));
        mBitmap = getBitmap();
    }

    @Override
    protected void finalize() throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mBitmap.recycle();
        }
        super.finalize();
    }
}
