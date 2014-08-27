package org.omnirom.music.app;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.util.Log;

import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;

import java.io.File;
import java.io.IOException;

public class OmniMusic extends Application {
    private static final String TAG = "OmniMusic";

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup the plugins system
        ProviderAggregator.getDefault().setContext(getApplicationContext());
        PluginsLookup.getDefault().initialize(getApplicationContext());

        // Setup network cache
        /**
         * Note about the cache and EchoNest: The HTTP cache would sometimes cache request
         * we didn't want (such as status query for Taste Profile update). We're using
         * a hacked jEN library that doesn't cache these requests.
         */
        try {
            final File httpCacheDir = new File(getCacheDir(), "http");
            final long httpCacheSize = 100 * 1024 * 1024; // 100 MiB
            final HttpResponseCache cache = HttpResponseCache.install(httpCacheDir, httpCacheSize);

            Log.i(TAG, "HTTP Cache size: " + cache.size() / 1024 / 1024 + "MB");
        } catch (IOException e) {
            Log.w(TAG, "HTTP response cache installation failed", e);
        }

        // Setup image cache
        AlbumArtCache.getDefault().initialize(getApplicationContext());
        ImageCache.getDefault().initialize(getApplicationContext());

        // Setup Automix system
        AutoMixManager.getDefault().initialize(getApplicationContext());
    }

}
