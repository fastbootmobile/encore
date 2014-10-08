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
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.Menu;
import android.view.Window;
import android.widget.SearchView;

import org.omnirom.music.app.fragments.SearchFragment;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderConnection;

import java.util.List;

/**
 * Activity allowing display of search results through
 * a {@link org.omnirom.music.app.fragments.SearchFragment}
 */
public class SearchActivity extends FragmentActivity {
    private static final String TAG = "SearchActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";
    private SearchFragment mActiveFragment;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.activity_search);

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (SearchFragment) fm.findFragmentByTag(TAG_FRAGMENT);
        if (mActiveFragment == null) {
            mActiveFragment = new SearchFragment();
            fm.beginTransaction()
                    .add(R.id.search_container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }
        handleIntent(getIntent());

        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                .getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));

        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY).trim();

            if (mActiveFragment != null) {
                mActiveFragment.resetResults();
            }

            List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
            for (ProviderConnection providerConnection : providers) {
                try {
                    final IMusicProvider binder = providerConnection.getBinder();
                    if (binder != null) {
                        binder.startSearch(query);
                    } else {
                        Log.e(TAG, "Null binder, cannot search on " + providerConnection.getIdentifier());
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot run search on a provider", e);
                }
            }

        }

    }
}
