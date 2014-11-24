package org.omnirom.music.framework;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

/**
 * Created by Guigui on 24/11/2014.
 */
public class RecyclingBitmapDrawable extends BitmapDrawable {
    private RefCountedBitmap mBitmap;

    public RecyclingBitmapDrawable(Resources res, RefCountedBitmap bitmap) {
        super(res, bitmap.get());
        mBitmap = bitmap;
        mBitmap.acquire();
    }

    public RefCountedBitmap getRefBitmap() {
        return mBitmap;
    }

    @Override
    protected void finalize() throws Throwable {
        mBitmap.release();
        super.finalize();
    }
}
