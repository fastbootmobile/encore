package org.omnirom.music.app;

import android.app.Activity;
import android.support.v4.app.FragmentManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.widget.SearchView;

import org.omnirom.music.app.fragments.PlaylistViewFragment;
import org.omnirom.music.app.fragments.SearchFragment;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.providers.ProviderConnection;

/**
 * Created by h4o on 28/07/2014.
 */
public class PlaylistActivity extends FragmentActivity {
    private String TAG = "PlaylistActivity";
    private String TAG_FRAGMENT = "fragment_inner";
    private PlaylistViewFragment mActiveFragment;
    //private SearchFragment mActiveFragment;
    private Bundle mInitialIntent;
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    public static Intent craftIntent(Context context,Playlist playlist) {
        Intent intent = new Intent(context, PlaylistActivity.class);

        intent.putExtra(PlaylistViewFragment.KEY_PLAYLIST, playlist);

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstance){
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_playlist);
        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (PlaylistViewFragment) fm.findFragmentByTag(TAG_FRAGMENT);
        if(savedInstance == null){
            mInitialIntent = getIntent().getExtras();
        } else {
            mInitialIntent = savedInstance.getBundle(EXTRA_RESTORE_INTENT);
        }
        Log.d(TAG, "retrieved intent");
        if(mActiveFragment == null ){
            mActiveFragment = new PlaylistViewFragment();
            fm.beginTransaction()
                    .add(R.id.playlist_container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }
        mActiveFragment.setArguments(mInitialIntent);
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

}
