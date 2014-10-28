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

    /**
     * @return The default instance
     */
    public static ImageCache getDefault() {
        return INSTANCE;
    }

    /**
     * Default constructor, creates an LRU cache of 1/8th the max memory
     */
    public ImageCache() {
        mEntries = new ArrayList<String>();

        final int maxMemory = (int) Runtime.getRuntime().maxMemory();
        Log.e(TAG, "ImageCache size: " + (maxMemory / 12 / 1024) + " MB");

        mMemoryCache = new LruCache<String, Bitmap>(maxMemory / 12) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    /**
     * Initializes the memory cache. Creates the cache directory and load the existing entries.
     * @param ctx A valid context
     */
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

    /**
     * Clears the cache (both memory and disk)
     */
    public void clear() {
        mMemoryCache.evictAll();
        mEntries.clear();

        File[] cacheFiles = mCacheDir.listFiles();
        for (File file : cacheFiles) {
            if (!file.delete()) {
                Log.e(TAG, "Cannot delete " + file.getPath());
            }
        }
    }

    /**
     * Returns whether or not the provided key is currently available in memory
     * @param key The key to check
     * @return true if the image is available in memory
     */
    public boolean hasInMemory(final String key) {
        synchronized (this) {
            return mMemoryCache.get(key) != null;
        }
    }

    /**
     * Returns whether or not the provided key is currently available on disk
     * @param key The key to check
     * @return true if the image is cached on the disk (well, flash storage)
     */
    public boolean hasOnDisk(final String key) {
        synchronized (this) {
            return mEntries.contains(key);
        }
    }

    /**
     * Returns the image from the cache (either memory or disk)
     * @param key The key of the image to get
     * @return A bitmap corresponding to the key, or null if it's not in the cache
     */
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
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
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

    /**
     * Stores the image as WEBP in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     */
    public void put(final String key, final Bitmap bmp) {
        put(key, bmp, false);
    }

    /**
     * Stores the image as either WEBP or PNG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     * @param asPNG True to store as PNG, false to store as WEBP
     */
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
