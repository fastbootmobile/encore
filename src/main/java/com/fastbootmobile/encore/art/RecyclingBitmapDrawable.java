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

package com.fastbootmobile.encore.art;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;

/**
 * A BitmapDrawable that keeps track of whether it is being displayed or cached.
 * When the drawable is no longer being displayed or cached,
 * {@link android.graphics.Bitmap#recycle() recycle()} will be called on this drawable's bitmap.
 */
public class RecyclingBitmapDrawable extends BitmapDrawable {
    static final String TAG = "RecyclingBitmapDrawable";

    private int mCacheRefCount = 0;
    private int mDisplayRefCount = 0;

    private boolean mHasBeenDisplayed;

    public RecyclingBitmapDrawable(Resources res, Bitmap bitmap) {
        super(res, bitmap);
    }

    /**
     * Notify the drawable that the displayed state has changed. Internally a
     * count is kept so that the drawable knows when it is no longer being
     * displayed.
     *
     * @param isDisplayed - Whether the drawable is being displayed or not
     */
    public void setIsDisplayed(boolean isDisplayed) {
        synchronized (this) {
            if (isDisplayed) {
                mDisplayRefCount++;
                mHasBeenDisplayed = true;
            } else {
                mDisplayRefCount--;
            }
        }

        // Check to see if recycle() can be called
        checkState();
    }

    /**
     * Notify the drawable that the cache state has changed. Internally a count
     * is kept so that the drawable knows when it is no longer being cached.
     *
     * @param isCached - Whether the drawable is being cached or not
     */
    public void setIsCached(boolean isCached) {
        synchronized (this) {
            if (isCached) {
                mCacheRefCount++;
            } else {
                mCacheRefCount--;
            }
        }

        // Check to see if recycle() can be called
        checkState();
    }

    private synchronized void checkState() {
        // DISABLED FOR NOW - LEAVING DEFAULT BITMAPDRAWABLE BEHAVIOR, SINCE AS OF
        // HONEYCOMB THIS SHOULD BE RECYCLED AUTOMATICALLY (SHOULD)
        if (false) {
            // If the drawable cache and display ref counts = 0, and this drawable
            // has been displayed, then recycle
            if (mCacheRefCount <= 0 && mDisplayRefCount <= 0 && mHasBeenDisplayed
                    && hasValidBitmap()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "No longer being used or cached so recycling. "
                            + toString());
                }

                getBitmap().recycle();
            }
        }
    }

    private synchronized boolean hasValidBitmap() {
        Bitmap bitmap = getBitmap();
        return bitmap != null && !bitmap.isRecycled();
    }

}
