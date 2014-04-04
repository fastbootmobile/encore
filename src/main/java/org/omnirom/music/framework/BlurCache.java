package org.omnirom.music.framework;

import android.graphics.Bitmap;
import android.util.LruCache;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache for blurred images
 */
public class BlurCache {

    private static BlurCache INSTANCE = new BlurCache();

    private LruCache<String, Bitmap> mCache;

    private BlurCache() {
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        mCache = new LruCache<String, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public static BlurCache getDefault() {
        return INSTANCE;
    }

    public void put(String key, Bitmap bmp) {
        mCache.put(key, bmp);
    }

    public Bitmap get(String key) {
        return mCache.get(key);
    }
}
