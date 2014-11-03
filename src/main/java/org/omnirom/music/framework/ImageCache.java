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
import android.os.Handler;
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
public class ImageCache {
    private static final String TAG = "ImageCache";
    private static final ImageCache INSTANCE = new ImageCache();

    private ArrayList<String> mEntries;
    private File mCacheDir;
    private RefCountedBitmap mDefaultArt;
    private Handler mHandler;

    private final LruCache<String, RefCountedBitmap> mMemoryCache;

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
        mHandler = new Handler();

        final int maxMemory = 10 * 1024 * 1024; // (int) Runtime.getRuntime().maxMemory();
        Log.d(TAG, "ImageCache size: " + (maxMemory / 1024 / 1024) + " MB");

        mMemoryCache = new LruCache<String, RefCountedBitmap>(maxMemory) {
            @Override
            protected int sizeOf(String key, RefCountedBitmap value) {
                return value.get().getByteCount();
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, final RefCountedBitmap oldBitmap, RefCountedBitmap newBitmap) {
                oldBitmap.release();
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

        mDefaultArt = new RefCountedBitmap(((BitmapDrawable) ctx.getResources().getDrawable(R.drawable.album_placeholder)).getBitmap());
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
     * Clears the memory cache
     */
    public void evictAll() {
        synchronized (this) {
            mMemoryCache.evictAll();
        }
    }

    /**
     * Returns whether or not the provided key is currently available in memory
     * @param key The key to check
     * @return true if the image is available in memory
     */
    public boolean hasInMemory(final String key) {
        synchronized (this) {
            RefCountedBitmap bmp = mMemoryCache.get(sanitizeKey(key));
            if (bmp != null && bmp.get().isRecycled()) {
                mMemoryCache.remove(sanitizeKey(key));
                return false;
            } else return bmp != null;
        }
    }

    /**
     * Returns whether or not the provided key is currently available on disk
     * @param key The key to check
     * @return true if the image is cached on the disk (well, flash storage)
     */
    public boolean hasOnDisk(final String key) {
        synchronized (this) {
            return mEntries.contains(sanitizeKey(key));
        }
    }

    /**
     * Returns the image from the cache (either memory or disk)
     * @param key The key of the image to get
     * @return A bitmap corresponding to the key, or null if it's not in the cache
     */
    public RefCountedBitmap get(final String key) {
        if (key == null) {
            return null;
        }

        final String cleanKey = sanitizeKey(key);

        boolean contains;
        synchronized (this) {
            contains = mEntries.contains(cleanKey);
        }

        if (contains) {
            // Check if we have it in memory
            RefCountedBitmap item = mMemoryCache.get(cleanKey);
            if (item == null) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap bmp = BitmapFactory.decodeFile(mCacheDir.getAbsolutePath() + "/" + cleanKey);
                if (bmp != null) {
                    item = new RefCountedBitmap(bmp);
                    item.acquire();
                    mMemoryCache.put(cleanKey, item);
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
    public RefCountedBitmap put(final String key, final Bitmap bmp) {
        RefCountedBitmap rcb = new RefCountedBitmap(bmp);
        put(key, rcb, false);
        return rcb;
    }

    /**
     * Stores the image as WEBP in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     */
    public void put(final String key, final RefCountedBitmap bmp) {
        put(key, bmp, false);
    }

    /**
     * Stores the image as either WEBP or PNG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     * @param asPNG True to store as PNG, false to store as WEBP
     */
    public RefCountedBitmap put(final String key, Bitmap bmp, final boolean asPNG) {
        RefCountedBitmap rcb = new RefCountedBitmap(bmp);
        put(key, rcb, asPNG);
        return rcb;
    }

    /**
     * Stores the image as either WEBP or PNG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     * @param asPNG True to store as PNG, false to store as WEBP
     */
    public void put(final String key, RefCountedBitmap bmp, final boolean asPNG) {
        boolean isDefaultArt = false;

        final String cleanKey = sanitizeKey(key);

        if (bmp == null) {
            bmp = mDefaultArt;
            isDefaultArt = true;
        }

        bmp.acquire();
        synchronized (mMemoryCache) {
            mMemoryCache.put(cleanKey, bmp);
            // Touch usage
            bmp = mMemoryCache.get(cleanKey);
        }

        if (!isDefaultArt) {
            try {
                bmp.acquire();

                FileOutputStream out = new FileOutputStream(mCacheDir.getAbsolutePath() + "/" + cleanKey);
                bmp.get().compress(asPNG ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.WEBP, 90, out);
                out.close();

                bmp.release();
            } catch (IOException e) {
                Log.e(TAG, "Unable to write the file to cache", e);
            }

            synchronized (this) {
                mEntries.add(cleanKey);
            }
        }
    }

    /**
     * Sanitizes the key to remove out unwanted characters
     * @return A sanitized copy of the key
     */
    private String sanitizeKey(String key) {
        return key.replaceAll("\\W", "_");
    }
}
