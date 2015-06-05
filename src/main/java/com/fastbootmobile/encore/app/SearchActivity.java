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

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.provider.SearchRecentSuggestions;
import android.support.v4.app.FragmentManager;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.SearchView;
import android.widget.Toast;

import com.fastbootmobile.encore.app.fragments.SearchFragment;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.SearchSuggestionProvider;

/**
 * Activity allowing display of search results through
 * a {@link com.fastbootmobile.encore.app.fragments.SearchFragment}
 */
public class SearchActivity extends AppActivity {
    private static final String TAG = "SearchActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";
    private SearchFragment mActiveFragment;
    private Handler mHandler;
    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        mHandler = new Handler();

        supportRequestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_search);

        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (SearchFragment) fm.findFragmentByTag(TAG_FRAGMENT);
        if (mActiveFragment == null) {
            mActiveFragment = new SearchFragment();
            fm.beginTransaction()
                    .add(R.id.search_container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }

        handleIntent(getIntent());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.search, menu);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                .getActionView();
        searchView.setSearchableInfo(searchManager
                .getSearchableInfo(getComponentName()));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                supportFinishAfterTransition();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(final Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            if (!intent.hasExtra(SearchManager.QUERY)) {
                Toast.makeText(this, "Invalid search query: missing query", Toast.LENGTH_SHORT).show();
            } else {
                final String query = intent.getStringExtra(SearchManager.QUERY).trim();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mActiveFragment.resetResults();
                        mActiveFragment.setArguments(query);
                        ProviderAggregator.getDefault().startSearch(query);
                    }
                }, 200);

                SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                        SearchSuggestionProvider.AUTHORITY, SearchSuggestionProvider.MODE);
                suggestions.saveRecentQuery(query, null);
            }
        }
    }
}
