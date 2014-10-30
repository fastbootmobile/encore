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

package org.omnirom.music.app;

import android.app.Application;
import android.net.http.HttpResponseCache;
import android.util.Log;

import org.acra.ACRA;
import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;
import org.acra.sender.HttpSender;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;

import java.io.File;
import java.io.IOException;

import uk.co.chrisjenx.calligraphy.CalligraphyConfig;

// Report crashes with ACRA on our temporary endpoint
@ReportsCrashes(
        formKey = "",
        httpMethod = HttpSender.Method.PUT,
        reportType = HttpSender.Type.JSON,
        formUri = "http://nuclear.ouverta.fr/om-acra/_design/acra-storage/_update/report",
        formUriBasicAuthLogin = "reporter-omnimusic-alpha",
        formUriBasicAuthPassword = "4lqh4mu51c1337"
)
/**
 * Application structure wrapper to handle various application-wide (activity + services) properties
 */
public class OmniMusic extends Application {
    private static final String TAG = "OmniMusic";

    @Override
    public void onCreate() {
        super.onCreate();

        // Setup ACRA bug reporting
        ACRA.init(this);

        // Setup the plugins system
        ProviderAggregator.getDefault().setContext(getApplicationContext());
        PluginsLookup.getDefault().initialize(getApplicationContext());

        /**
         * Note about the cache and EchoNest: The HTTP cache would sometimes cache request
         * we didn't want (such as status query for Taste Profile update). We're using
         * a hacked jEN library that doesn't cache these requests.
         */
        // Setup network cache
        try {
            final File httpCacheDir = new File(getCacheDir(), "http");
            final long httpCacheSize = 100 * 1024 * 1024; // 100 MiB
            final HttpResponseCache cache = HttpResponseCache.install(httpCacheDir, httpCacheSize);

            Log.i(TAG, "HTTP Cache size: " + cache.size() / 1024 / 1024 + "MB");
        } catch (IOException e) {
            Log.w(TAG, "HTTP response cache installation failed", e);
        }

        // Setup image cache
        ImageCache.getDefault().initialize(getApplicationContext());

        // Setup Automix system
        AutoMixManager.getDefault().initialize(getApplicationContext());

        // Setup custom fonts
        CalligraphyConfig.initDefault("fonts/Roboto-Regular.ttf", R.attr.fontPath);
    }
}
