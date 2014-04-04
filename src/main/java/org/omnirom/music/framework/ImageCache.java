package org.omnirom.music.framework;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileNotFoundException;
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

    public static ImageCache getDefault() {
        return INSTANCE;
    }

    public ImageCache() {
        mEntries = new ArrayList<String>();
    }

    public void initialize(Context ctx) {
        mCacheDir = new File(ctx.getCacheDir(), "albumart");
        mCacheDir.mkdir();

        File[] entries = mCacheDir.listFiles();
        if (entries != null) {
            for (File entry : entries) {
                mEntries.add(entry.getName());
                Log.e(TAG, "Entry: " + entry.getName());
            }
        }
    }

    public Bitmap get(final String key) {
        boolean contains;
        synchronized (this) {
            contains = mEntries.contains(key);
        }

        if (contains) {
            return BitmapFactory.decodeFile(mCacheDir.getAbsolutePath() + "/" + key);
        } else {
            return null;
        }
    }

    public void put(final String key, final Bitmap bmp) {
        try {
            FileOutputStream out = new FileOutputStream(mCacheDir.getAbsolutePath() + "/" + key);
            bmp.compress(Bitmap.CompressFormat.WEBP, 90, out);
            out.close();
        } catch (IOException e) {
            Log.e(TAG, "Unable to write the file to cache", e);
        }

        synchronized (this) {
            mEntries.add(key);
        }
    }
}
