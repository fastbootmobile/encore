package org.omnirom.music.framework;

import android.graphics.Bitmap;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache for blurred images
 */
public class BlurCache {

    private static BlurCache INSTANCE = new BlurCache();

    private Map<String, SoftReference<Bitmap>> mCache;

    private BlurCache() {
        mCache = new HashMap<String, SoftReference<Bitmap>>();
    }

    public static BlurCache getDefault() {
        return INSTANCE;
    }

    public void put(String key, Bitmap bmp) {
        mCache.put(key, new SoftReference<Bitmap>(bmp));
    }

    public Bitmap get(String key) {
        SoftReference<Bitmap> entry = mCache.get(key);
        if (entry != null) {
            return entry.get();
        } else {
            return null;
        }
    }
}
