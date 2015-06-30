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
