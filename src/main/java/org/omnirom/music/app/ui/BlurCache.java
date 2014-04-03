package org.omnirom.music.app.ui;

import android.graphics.Bitmap;

import java.util.HashMap;
import java.util.Map;

/**
 * Cache for blurred images
 */
public class BlurCache {

    private static BlurCache INSTANCE = new BlurCache();

    private Map<String, Bitmap> mCache;

    private BlurCache() {
        mCache = new HashMap<String, Bitmap>();
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
