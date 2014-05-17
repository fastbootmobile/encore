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

    private static final String IMAGE_CACHE_SUFFIX = "____OM_BLUR";

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

    public void put(String key, Bitmap bmp, boolean cacheFile) {
        mCache.put(key, bmp);

        if (cacheFile) {
            ImageCache.getDefault().put(key + IMAGE_CACHE_SUFFIX, bmp);
        }
    }

    public Bitmap get(String key) {
        // If we have the blurred image in memory, return it
        Bitmap output = mCache.get(key);
        if (output != null) {
            return output;
        }

        // Else, try to see if we have it cached in a file in local storage, and add it to memory
        output = ImageCache.getDefault().get(key + IMAGE_CACHE_SUFFIX);
        if (output != null) {
            put(key, output, false);
            return output;
        }

        // Well, too bad then, we don't have it
        return null;
    }
}
