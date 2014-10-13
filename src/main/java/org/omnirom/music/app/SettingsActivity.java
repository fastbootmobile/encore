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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.support.v4.app.FragmentManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.balysv.material.drawable.menu.MaterialMenuDrawable;
import com.balysv.material.drawable.menu.MaterialMenuView;

import org.omnirom.music.app.fragments.SettingsFragment;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Activity showing a {@link org.omnirom.music.app.fragments.SettingsFragment} to configure the app
 */
public class SettingsActivity extends FragmentActivity {
    private static final String TAG = "SettingsActivity";
    public static final String TAG_FRAGMENT = "fragment_inner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        FragmentManager fm = getSupportFragmentManager();
        SettingsFragment activeFragment = (SettingsFragment) fm.findFragmentByTag(TAG_FRAGMENT);

        if (activeFragment == null) {
            activeFragment = new SettingsFragment();
            fm.beginTransaction()
                    .add(R.id.container, activeFragment, TAG_FRAGMENT)
                    .commit();
        }

        // Setup L-style action bar
        ActionBar actionBar = getActionBar();
        assert actionBar != null;
        actionBar.setHomeButtonEnabled(false);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayShowTitleEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setCustomView(R.layout.action_bar);
        MaterialMenuView toggle = (MaterialMenuView) actionBar.getCustomView().findViewById(R.id.action_bar_menu);
        toggle.setState(MaterialMenuDrawable.IconState.CHECK);
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                    //finishAfterTransition();
                } else {
                    finish();
                }
            }
        });

        // Change the music volume here as well
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
    }
}
