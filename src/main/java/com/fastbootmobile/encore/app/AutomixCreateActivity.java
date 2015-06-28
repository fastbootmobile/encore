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

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.fastbootmobile.encore.api.echonest.AutoMixBucket;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.app.ui.FilteredMultiSelectListPreference;
import com.fastbootmobile.encore.framework.PluginsLookup;
import com.fastbootmobile.encore.providers.IMusicProvider;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderConnection;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.List;
import java.util.Set;

import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Activity presenting the settings to create an AutoMix Bucket
 */
public class AutomixCreateActivity extends PreferenceActivity {

    private static final String TAG = "AutomixCreateActivity";

    public static final String EXTRA_MODE = "mode";
    public static final int MODE_DYNAMIC = 0;
    public static final int MODE_STATIC = 1;

    private static final String KEY_NAME = "automix_bucket_name";
    private static final String KEY_STYLES = "automix_styles";
    private static final String KEY_MOODS = "automix_moods";
    private static final String KEY_TASTE = "automix_use_taste";
    private static final String KEY_ADVENTUROUS = "automix_adventurous";
    private static final String KEY_SONG_TYPES = "automix_song_types";
    private static final String KEY_SPEECHINESS = "automix_target_speechiness";
    private static final String KEY_ENERGY = "automix_target_energy";
    private static final String KEY_FAMILIAR = "automix_target_familiarity";

    private AutoMixManager mAutoMixManager = AutoMixManager.getDefault();
    private int mMode;
    private ProgressDialog mProgressDialog;
    private Toolbar mToolbar;

    private final Runnable mInvalidSettingsError = new Runnable() {
        @Override
        public void run() {
            mProgressDialog.dismiss();
            Utils.shortToast(AutomixCreateActivity.this,
                    R.string.bucket_settings_invalid);
        }
    };
    private final Runnable mInvalidMinimalSettings = new Runnable() {
        @Override
        public void run() {
            mProgressDialog.dismiss();
            Utils.shortToast(AutomixCreateActivity.this,
                    R.string.automix_minimal_settings_error);
        }
    };
    private final Runnable mInvalidNameError = new Runnable() {
        @Override
        public void run() {
            mProgressDialog.dismiss();
            Utils.shortToast(AutomixCreateActivity.this,
                    R.string.automix_bucket_name_error);
        }
    };
    private final Runnable mCreationDone = new Runnable() {
        @Override
        public void run() {
            mProgressDialog.dismiss();
            if (mMode == MODE_STATIC) {
                Toast.makeText(AutomixCreateActivity.this, "Playlist created", Toast.LENGTH_SHORT).show();
            }
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lollipop removes toolbars
        LinearLayout root = (LinearLayout)findViewById(android.R.id.list).getParent().getParent().getParent();
        mToolbar = (Toolbar) getLayoutInflater().inflate(R.layout.toolbar_automix, root, false);
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == android.R.id.home) {
                    finish();
                    return true;
                } else if (id == R.id.menu_create) {
                    createBucket();
                }

                return true;
            }
        });
        root.addView(mToolbar, 0);

        mToolbar.inflateMenu(R.menu.automix);

        // Set the title
        mMode = getIntent().getIntExtra(EXTRA_MODE, 0);
        if (mMode == MODE_STATIC) {
            mToolbar.setTitle(R.string.title_activity_automix_create_static);
        } else if (mMode == MODE_DYNAMIC) {
            mToolbar.setTitle(R.string.title_activity_automix_create_dynamic);
        }

        // Inflate preferences
        addPreferencesFromResource(R.xml.automix_create);

        // Turn on static/dynamic specific preferences
        // Taste-biased playlists only work with static playlists
        boolean hasTasteSettings = (mMode == MODE_STATIC);

        findPreference(KEY_ADVENTUROUS).setEnabled(hasTasteSettings);
        findPreference(KEY_TASTE).setEnabled(hasTasteSettings);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(new CalligraphyContextWrapper(newBase));
    }

    private void createBucket() {
        // TODO: Remove the progress dialog and show progress and status in the main Automix
        // fragment to avoid locking the user out of the app for multiple seconds
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setMessage(
                getString(mMode == MODE_DYNAMIC ?
                        R.string.bucket_creating_dialog : R.string.playlist_creating_dialog)
        );
        mProgressDialog.show();

        new Thread() {
            public void run() {
                String name = getPrefStringValue(KEY_NAME);
                String[] styles = getPrefStringArray(KEY_STYLES);
                String[] moods = getPrefStringArray(KEY_MOODS);
                boolean taste = getPrefBool(KEY_TASTE);
                float advent = getPrefFloat(KEY_ADVENTUROUS);
                String[] songtype = getPrefStringArray(KEY_SONG_TYPES);
                float speech = getPrefFloat(KEY_SPEECHINESS);
                float energy = getPrefFloat(KEY_ENERGY);
                float familiar = getPrefFloat(KEY_FAMILIAR);

                if (name == null || name.trim().isEmpty()) {
                    runOnUiThread(mInvalidNameError);
                } else if (styles.length == 0 && moods.length == 0
                        && (mMode == MODE_DYNAMIC || !taste)) {
                    runOnUiThread(mInvalidMinimalSettings);
                } else {
                    Log.i(TAG, "Creating bucket (mode=" + mMode + ")...");


                    if (mMode == MODE_DYNAMIC) {
                        AutoMixBucket bucket = mAutoMixManager.createBucket(name, styles, moods, taste, advent,
                                songtype, speech, energy, familiar);

                        if (bucket.isPlaylistSessionError()) {
                            runOnUiThread(mInvalidSettingsError);
                        } else {
                            Log.d(TAG, "Bucket created!");
                            runOnUiThread(mCreationDone);
                        }
                    } else {
                        AutoMixBucket bucket = mAutoMixManager.createStaticBucket(name, styles, moods, taste, advent,
                                songtype, speech, energy, familiar);

                        List<String> songRefs = bucket.generateStaticPlaylist();
                        if (songRefs != null) {
                            ProviderAggregator aggregator = ProviderAggregator.getDefault();
                            ProviderIdentifier providerId = aggregator.getRosettaStoneIdentifier(aggregator.getPreferredRosettaStonePrefix());
                            if (providerId != null) {
                                ProviderConnection conn = PluginsLookup.getDefault().getProvider(providerId);
                                if (conn != null) {
                                    IMusicProvider binder = conn.getBinder();
                                    if (binder != null) {
                                        try {
                                            String playlistRef = binder.addPlaylist(bucket.getName());

                                            for (String songRef : songRefs) {
                                                binder.addSongToPlaylist(songRef, playlistRef, providerId);
                                            }

                                            Intent intent = PlaylistActivity.craftIntent(AutomixCreateActivity.this,
                                                    aggregator.retrievePlaylist(playlistRef, providerId),
                                                    null);
                                            startActivity(intent);
                                            runOnUiThread(mCreationDone);
                                        } catch (RemoteException e) {
                                            Log.e(TAG, "Error in provider while creating playlist", e);
                                        }
                                    }
                                }
                            }
                        } else {
                            runOnUiThread(mInvalidSettingsError);
                        }
                    }
                }
            }
        }.start();
    }

    private String getPrefStringValue(String key) {
        PreferenceManager p = getPreferenceManager();
        return ((EditTextPreference) p.findPreference(key)).getText();
    }

    private String[] getPrefStringArray(String key) {
        PreferenceManager p = getPreferenceManager();
        Preference pref = p.findPreference(key);
        Set<String> stringSet;

        if (pref instanceof MultiSelectListPreference) {
            stringSet = ((MultiSelectListPreference) p.findPreference(key)).getValues();
        } else if (pref instanceof FilteredMultiSelectListPreference) {
            stringSet = ((FilteredMultiSelectListPreference) p.findPreference(key)).getValues();
        } else {
            throw new RuntimeException("Invalid type of preference for a string array");
        }

        return stringSet.toArray(new String[stringSet.size()]);
    }

    private float getPrefFloat(String key) {
        PreferenceManager p = getPreferenceManager();
        return Float.parseFloat(((ListPreference) p.findPreference(key)).getValue());
    }

    private boolean getPrefBool(String key) {
        PreferenceManager p = getPreferenceManager();
        return ((CheckBoxPreference) p.findPreference(key)).isChecked();
    }
}
