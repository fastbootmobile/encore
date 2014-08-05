package org.omnirom.music.app;

import android.app.Activity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.widget.SearchView;

import org.omnirom.music.app.fragments.AlbumViewFragment;
import org.omnirom.music.app.fragments.SearchFragment;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderConnection;

/**
 * Created by h4o on 22/07/2014.
 */
public class SearchActivity extends FragmentActivity {
    private String TAG = "SearchActivity";
    private String TAG_FRAGMENT = "fragment_inner";
    private SearchFragment mActiveFragment;
    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        Log.d(TAG,"searched intent");
        setContentView(R.layout.activity_search);
        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment =(SearchFragment) fm.findFragmentByTag(TAG_FRAGMENT);
        if(mActiveFragment == null){
            mActiveFragment = new SearchFragment();
            fm.beginTransaction()
                    .add(R.id.search_container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }
        handleIntent(getIntent());
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
            String query = intent.getStringExtra(SearchManager.QUERY);
            for(ProviderConnection providerConnection : PluginsLookup.getDefault().getAvailableProviders()){
                try {
                    providerConnection.getBinder().startSearch(query);
                }catch (Exception e){

                }
            }

        }

    }
}
