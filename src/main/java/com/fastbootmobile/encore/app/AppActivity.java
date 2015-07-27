/*
 * Copyright (C) 2015 Fastboot Mobile, LLC.
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

package com.fastbootmobile.encore.app;

import android.content.Context;
import android.media.AudioManager;
import android.support.v7.app.AppCompatActivity;

import com.fastbootmobile.encore.art.ImageCache;
import com.fastbootmobile.encore.framework.PluginsLookup;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Base activity used by all the activities of this app
 */
public abstract class AppActivity  extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
    }


    @Override
    protected void onResume() {
        super.onResume();

        // Connect to the playback service
        PluginsLookup.getDefault().incPlaybackUsage();

        // We control the Music volume stream
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onPause() {
        PluginsLookup.getDefault().decPlaybackUsage();
        ImageCache.getDefault().evictAll();

        super.onPause();
    }
}
