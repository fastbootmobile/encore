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
import android.util.Log;

/**
 * Ref-counted aware Bitmap Drawable
 */
public class RecyclingBitmapDrawable extends BitmapDrawable {
    private Bitmap mBitmap;

    public static RecyclingBitmapDrawable from(Resources res, RefCountedBitmap rcd) {
        rcd.acquire();
        final Bitmap source = rcd.get();
        RecyclingBitmapDrawable output = null;

        if (source != null) {
            Bitmap copy = source.copy(source.getConfig(), false);
            output = new RecyclingBitmapDrawable(res, copy);
        }

        rcd.release();

        return output;
    }

    private RecyclingBitmapDrawable(Resources res, Bitmap bitmap) {
        super(res, bitmap);
        mBitmap = bitmap;
    }

    public void release() {
        mBitmap.recycle();
    }
}
