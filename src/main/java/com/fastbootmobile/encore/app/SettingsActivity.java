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

package com.fastbootmobile.encore.app;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.fastbootmobile.encore.app.fragments.DspProvidersFragment;
import com.fastbootmobile.encore.app.fragments.SettingsFragment;
import com.fastbootmobile.encore.utils.Utils;

/**
 * Activity showing a {@link com.fastbootmobile.encore.app.fragments.SettingsFragment} to configure the app
 */
public class SettingsActivity extends AppActivity {
    private static final String TAG = "SettingsActivity";
    public static final String TAG_FRAGMENT = "fragment_inner";

    private Toolbar mToolbar;
    private Fragment mActiveFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = fm.findFragmentByTag(TAG_FRAGMENT);

        if (mActiveFragment == null) {
            Bundle extras = getIntent().getExtras();
            if (extras != null && extras.getBoolean("DSP", false)) {
                mActiveFragment = new DspProvidersFragment();
                setTitle(R.string.settings_dsp_config_title);
            } else {
                mActiveFragment = new SettingsFragment();
            }

            fm.beginTransaction()
                    .add(R.id.container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (!mActiveFragment.onOptionsItemSelected(item)) {
                if (Utils.hasLollipop()) {
                    finishAfterTransition();
                } else {
                    finish();
                }
            }

            return true;
        }

        return false;
    }
}
