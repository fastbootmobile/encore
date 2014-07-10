package org.omnirom.music.app;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.util.Log;

import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PlaybackCallbackImpl;
import org.omnirom.music.framework.PluginsLookup;

import java.io.File;
import java.io.IOException;

public class OmniMusic extends Application {
    private static final String TAG = "OmniMusic";

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup the plugins system
        PluginsLookup.getDefault().initialize(getApplicationContext());

        // Setup network cache
        try {
            File httpCacheDir = new File(getCacheDir(), "http");
            long httpCacheSize = 100 * 1024 * 1024; // 100 MiB
            HttpResponseCache.install(httpCacheDir, httpCacheSize);
        } catch (IOException e) {
            Log.w(TAG, "HTTP response cache installation failed", e);
        }

        // Setup image cache
        AlbumArtCache.getDefault().initialize(getApplicationContext());
        ImageCache.getDefault().initialize(getApplicationContext());
    }

}
