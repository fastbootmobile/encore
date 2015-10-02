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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;
import android.util.LruCache;

import com.fastbootmobile.encore.app.R;
import com.fastbootmobile.encore.utils.ImageUtils;
import com.fastbootmobile.encore.utils.SettingsKeys;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Image cache in the cache directory on internal storage
 */
@SuppressWarnings("SynchronizeOnNonFinalField")
public class ImageCache {
    private static final String TAG = "ImageCache";
    private static final ImageCache INSTANCE = new ImageCache();
    private static final long EXPIRATION_TIME = TimeUnit.DAYS.toMillis(7);

    private static final boolean USE_MEMORY_CACHE = true;

    private final ArrayList<String> mEntries;
    private File mCacheDir;
    private Bitmap mDefaultArt;

    private final LruCache<String, RecyclingBitmapDrawable> mMemoryCache;
    private Set<SoftReference<Bitmap>> mReusableBitmaps;

    /**
     * @return The default instance
     */
    public static ImageCache getDefault() {
        return INSTANCE;
    }

    /**
     * Default constructor, creates an LRU cache of the specified size
     */
    public ImageCache() {
        mEntries = new ArrayList<>();

        // A third of the max heap memory, or 39MB, whichever is lowest
        final int memoryCacheSize = Math.min(30000,
                (int) (Runtime.getRuntime().maxMemory() / 1024 / 3));
        Log.d(TAG, "Maximum image cache memory: " + memoryCacheSize + " KB (maxMemory=" + (Runtime.getRuntime().maxMemory() / 1024) + "KB)");

        // We create a set of reusable bitmaps that can be
        // populated into the inBitmap field of BitmapFactory.Options. Note that the set is
        // of SoftReferences which will actually not be very effective due to the garbage
        // collector being aggressive clearing Soft/SoftReferences. A better approach
        // would be to use a strongly references bitmaps, however this would require some
        // balancing of memory usage between this set and the bitmap LruCache. It would also
        // require knowledge of the expected size of the bitmaps. From Honeycomb to JellyBean
        // the size would need to be precise, from KitKat onward the size would just need to
        // be the upper bound (due to changes in how inBitmap can re-use bitmaps).
        mReusableBitmaps =
                Collections.synchronizedSet(new HashSet<SoftReference<Bitmap>>());


        if (USE_MEMORY_CACHE) {
            mMemoryCache = new LruCache<String, RecyclingBitmapDrawable>(memoryCacheSize) {
                @Override
                protected int sizeOf(String key, RecyclingBitmapDrawable value) {
                    final int bitmapSize = ImageUtils.getBitmapSize(value) / 1024;
                    return bitmapSize == 0 ? 1 : bitmapSize;
                }

                @Override
                protected void entryRemoved(boolean evicted, String key,
                                            final RecyclingBitmapDrawable oldBitmap, RecyclingBitmapDrawable newBitmap) {
                    if (newBitmap != null) {
                        newBitmap.setIsCached(true);
                    }

                    oldBitmap.setIsCached(false);

                    synchronized (mReusableBitmaps) {
                        mReusableBitmaps.add(new SoftReference<>(oldBitmap.getBitmap()));
                    }
                }
            };
        } else {
            mMemoryCache = null;
        }
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
                if (System.currentTimeMillis() - entry.lastModified() > EXPIRATION_TIME
                        && entry.getName().contains("playlist")) {
                    // Expire playlist art regularly
                    if (!entry.delete()) {
                        // Couldn't delete the art... Let's load it anyway then
                        mEntries.add(entry.getName());
                    }
                } else {
                    mEntries.add(entry.getName());
                }
            }
        }

        mDefaultArt = ((BitmapDrawable) ctx.getResources()
                .getDrawable(R.drawable.album_placeholder)).getBitmap();

        SharedPreferences prefs = ctx.getSharedPreferences(SettingsKeys.PREF_SETTINGS, 0);
        AlbumArtCache.CREATIVE_COMMONS = prefs.getBoolean(SettingsKeys.KEY_FREE_ART, false);
    }

    /**
     * Clears the cache (both memory and disk)
     */
    public void clear() {
        if (USE_MEMORY_CACHE) {
            synchronized (mMemoryCache) {
                mMemoryCache.evictAll();
            }
        }

        synchronized (mEntries) {
            mEntries.clear();
        }

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
        AlbumArtHelper.clearAlbumArtRequests();
        if (USE_MEMORY_CACHE) {
            synchronized (mMemoryCache) {
                mMemoryCache.evictAll();
            }
        }
        mReusableBitmaps.clear();
    }

    /**
     * Returns whether or not the provided key is currently available in memory
     * @param key The key to check
     * @return true if the image is available in memory
     */
    public boolean hasInMemory(final String key) {
        if (USE_MEMORY_CACHE) {
            RecyclingBitmapDrawable bmp;
            bmp = mMemoryCache.get(sanitizeKey(key));
            return bmp != null;
        } else {
            return false;
        }
    }

    /**
     * Returns whether or not the provided key is currently available on disk
     * @param key The key to check
     * @return true if the image is cached on the disk (well, flash storage)
     */
    public boolean hasOnDisk(final String key) {
        synchronized (mEntries) {
            return mEntries.contains(sanitizeKey(key));
        }
    }

    /**
     * Returns the image from the cache (either memory or disk)
     * @param key The key of the image to get
     * @return A bitmap corresponding to the key, or null if it's not in the cache
     */
    public RecyclingBitmapDrawable get(final Resources res , final String key, final int reqSz) {
        if (key == null) {
            return null;
        }

        final String cleanKey = sanitizeKey(key);

        boolean contains;
        synchronized (mEntries) {
            contains = mEntries.contains(cleanKey);
        }

        if (contains) {
            RecyclingBitmapDrawable item;
            synchronized (mMemoryCache) {
                // Check if we have it in memory
                item = USE_MEMORY_CACHE ? mMemoryCache.get(cleanKey + '_' + reqSz) : null;
            }

            if (item == null) {
                final String filePath = mCacheDir.getAbsolutePath() + "/" + cleanKey;

                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(filePath, opts);

                opts.inJustDecodeBounds = false;
                ImageUtils.addInBitmapOptions(opts, this, reqSz, opts.outWidth, opts.outHeight);

                try {
                    Bitmap bmp = BitmapFactory.decodeFile(filePath, opts);
                    if (bmp != null) {
                        item = new RecyclingBitmapDrawable(res, bmp);

                        if (USE_MEMORY_CACHE) {
                            mMemoryCache.put(cleanKey + '_' + reqSz, item);
                        }
                    } else {
                        mEntries.remove(cleanKey);
                        File f = new File(filePath);
                        if (!f.delete()) {
                            Log.e(TAG, "Cannot delete corrupted art at " + filePath);
                        }
                    }
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, "OutOfMemory when decoding input file", e);
                    return null;
                }
            }

            return item;
        } else {
            return null;
        }
    }

    /**
     * @param options - BitmapFactory.Options with out* options populated
     * @return Bitmap that case be used for inBitmap
     */
    public Bitmap getBitmapFromReusableSet(BitmapFactory.Options options) {
        Bitmap bitmap = null;

        if (mReusableBitmaps != null && !mReusableBitmaps.isEmpty()) {
            synchronized (mReusableBitmaps) {
                final Iterator<SoftReference<Bitmap>> iterator = mReusableBitmaps.iterator();
                Bitmap item;

                while (iterator.hasNext()) {
                    item = iterator.next().get();

                    if (null != item && item.isMutable()) {
                        // Check to see it the item can be used for inBitmap
                        if (ImageUtils.canUseForInBitmap(item, options)) {
                            bitmap = item;

                            // Remove from reusable set so it can't be used again
                            iterator.remove();
                            break;
                        }
                    } else {
                        // Remove from the set if the reference has been cleared.
                        iterator.remove();
                    }
                }
            }
        }

        return bitmap;
    }

    /**
     * Stores the image as JPEG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     */
    public RecyclingBitmapDrawable put(final Resources res, final String key, final Bitmap bmp) {
        RecyclingBitmapDrawable rcb = new RecyclingBitmapDrawable(res, bmp);
        put(res, key, rcb, false);
        return rcb;
    }

    /**
     * Stores the image as JPEG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     */
    public void put(final Resources res, final String key, final RecyclingBitmapDrawable bmp) {
        put(res, key, bmp, false);
    }

    /**
     * Stores the image as either JPEG or PNG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     * @param asPNG True to store as PNG, false to store as JPEG
     */
    public RecyclingBitmapDrawable put(final Resources res, final String key, Bitmap bmp, final boolean asPNG) {
        RecyclingBitmapDrawable rcb = new RecyclingBitmapDrawable(res, bmp);
        put(res, key, rcb, asPNG);
        return rcb;
    }

    /**
     * Stores the image as either JPEG or PNG in the cache
     * @param key The key of the image to put
     * @param bmp The bitmap to put (or null to put the default art)
     * @param asPNG True to store as PNG, false to store as JPEG
     */
    public void put(final Resources res, final String key, RecyclingBitmapDrawable bmp, final boolean asPNG) {
        boolean isDefaultArt = false;

        final String cleanKey = sanitizeKey(key);

        if (bmp == null) {
            bmp = new RecyclingBitmapDrawable(res, mDefaultArt.copy(mDefaultArt.getConfig(), false));
            isDefaultArt = true;
        }

        if (USE_MEMORY_CACHE) {
            mMemoryCache.put(cleanKey, bmp);
        }

        if (!isDefaultArt) {
            try {
                FileOutputStream out = new FileOutputStream(mCacheDir.getAbsolutePath() + "/" + cleanKey);
                Bitmap bitmap = bmp.getBitmap();

                boolean shouldRecycle = false;
                final float maxSize = 800;

                if (bitmap.getWidth() > maxSize && bitmap.getHeight() > maxSize) {
                    float ratio = (bitmap.getWidth() < bitmap.getHeight()) ?
                            bitmap.getWidth() / maxSize : bitmap.getHeight() / maxSize;
                    final int sWidth = (int) (bitmap.getWidth() / ratio);
                    final int sHeight = (int) (bitmap.getHeight() / ratio);

                    bitmap = Bitmap.createScaledBitmap(bitmap, sWidth, sHeight, true);
                    shouldRecycle = true;

                    Log.d(TAG, "Rescaled to " + sWidth + "x" + sHeight);
                }

                bitmap.compress(asPNG ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG, 90, out);
                out.close();

                if (shouldRecycle) {
                    // Scaled image will be used on reload
                    bitmap.recycle();
                }
            } catch (IOException e) {
                Log.e(TAG, "Unable to write the file to cache", e);
            }

            synchronized (mEntries) {
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
