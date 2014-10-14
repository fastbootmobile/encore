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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.util.LruCache;

import org.omnirom.music.app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Image cache in the cache directory on internal storage
 */
public class  ImageCache {
    private static final String TAG = "ImageCache";
    private static final ImageCache INSTANCE = new ImageCache();

    private ArrayList<String> mEntries;
    private File mCacheDir;
    private Bitmap mDefaultArt;

    private LruCache<String, Bitmap> mMemoryCache;

    public static ImageCache getDefault() {
        return INSTANCE;
    }

    public ImageCache() {
        mEntries = new ArrayList<String>();

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        mMemoryCache = new LruCache<String, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public void initialize(Context ctx) {
        mCacheDir = new File(ctx.getCacheDir(), "albumart");
        if (!mCacheDir.exists() && !mCacheDir.mkdir()) {
            Log.e(TAG, "Cannot mkdir the cache dir " + mCacheDir.getPath());
        }

        File[] entries = mCacheDir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                mEntries.add(entry.getName());
            }
        }

        mDefaultArt = ((BitmapDrawable) ctx.getResources().getDrawable(R.drawable.album_placeholder)).getBitmap();

    }

    public boolean hasInMemory(final String key) {
        synchronized (this) {
            return mMemoryCache.get(key) != null;
        }
    }

    public boolean hasOnDisk(final String key) {
        synchronized (this) {
            return mEntries.contains(key);
        }
    }

    public Bitmap get(final String key) {
        if (key == null) {
            return null;
        }

        boolean contains;
        synchronized (this) {
            contains = mEntries.contains(key);
        }

        if (contains) {
            // Check if we have it in memory
            Bitmap item = mMemoryCache.get(key);
            if (item == null) {
                item = BitmapFactory.decodeFile(mCacheDir.getAbsolutePath() + "/" + key);
                if (item != null) {
                    mMemoryCache.put(key, item);
                }
            }

            return item;
        } else {
            return null;
        }
    }


    public void put(final String key, final Bitmap bmp) {
        put(key, bmp, false);
    }

    public void put(final String key, Bitmap bmp, final boolean asPNG) {
        boolean isDefaultArt = false;

        if (bmp == null) {
            bmp = mDefaultArt;
            isDefaultArt = true;
        }

        mMemoryCache.put(key, bmp);

        if (!isDefaultArt) {
            try {
                FileOutputStream out = new FileOutputStream(mCacheDir.getAbsolutePath() + "/" + key);
                bmp.compress(asPNG ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.WEBP, 90, out);
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "Unable to write the file to cache", e);
            }

            synchronized (this) {
                mEntries.add(key);
            }
        }
    }
}
