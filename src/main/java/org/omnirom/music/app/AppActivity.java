package org.omnirom.music.app;

import android.content.Context;
import android.media.AudioManager;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;

import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PluginsLookup;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Base activity used by all the activities of this app
 */
public abstract class AppActivity  extends ActionBarActivity {

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
        System.gc();

        super.onPause();
    }
}
