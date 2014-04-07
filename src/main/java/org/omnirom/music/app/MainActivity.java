package org.omnirom.music.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.drawable.BitmapDrawable;
import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.omnirom.music.app.fragments.AbstractRootFragment;
import org.omnirom.music.app.fragments.NavigationDrawerFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.SettingsFragment;
import org.omnirom.music.app.ui.KenBurnsView;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.framework.PlaybackCallbackImpl;
import org.omnirom.music.framework.PlaybackState;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.ProviderAggregator;

import java.io.File;
import java.io.IOException;


public class MainActivity extends Activity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String TAG = "MainActivity";

    public static final int SECTION_LISTEN_NOW = 1;
    public static final int SECTION_MY_SONGS   = 2;
    public static final int SECTION_PLAYLISTS  = 3;
    public static final int SECTION_AUTOMIX    = 4;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    private PlaybackState mPlaybackState;

    public MainActivity() {
        mPlaybackState = new PlaybackState();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout), getTheme());

        // Setup action bar animation.
        KenBurnsView kbView = (KenBurnsView) findViewById(R.id.actionBarBackground);
        kbView.addBitmap(((BitmapDrawable) getResources().getDrawable(R.drawable.test_cover_imagine_dragons)).getBitmap());
        kbView.addBitmap(((BitmapDrawable) getResources().getDrawable(R.drawable.test_cover_rhcp)).getBitmap());

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) kbView.getLayoutParams();
        lp.height = Utils.getActionBarHeight(getTheme(), getResources());
        kbView.setLayoutParams(lp);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PluginsLookup.getDefault().connectPlayback(new PlaybackCallbackImpl(mPlaybackState));
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }

        // Release services connections
        PluginsLookup.getDefault().tearDown();
        ProviderAggregator.getDefault().getCache().purgeSongCache();

        super.onStop();
    }

    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        try {
            FragmentManager fragmentManager = getFragmentManager();
            Fragment newFrag;
            switch (position+1) {
                case SECTION_PLAYLISTS:
                    newFrag = PlaylistListFragment.newInstance();
                    break;

                default:
                    newFrag = PlaceholderFragment.newInstance(position + 1);
                    break;
            }

            showFragment(newFrag, false);
        } catch (IllegalStateException e) {
            // The app is pausing
        }
    }

    @Override
    public void onSettingsButtonPressed() {
        showFragment(SettingsFragment.newInstance(), true);
        mTitle = getString(R.string.action_settings);
    }

    public void showFragment(Fragment f, boolean addToStack) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0 && !addToStack) {
            fragmentManager.popBackStack();
        }

        FragmentTransaction ft = fragmentManager.beginTransaction();
        if (addToStack) {
            ft.setCustomAnimations(R.animator.slide_in_left, R.animator.slide_out_left, R.animator.slide_in_right, R.animator.slide_out_right);
            ft.addToBackStack(f.toString());
        } else {
            ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
        }
        ft.replace(R.id.container, f);
        ft.commit();
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case SECTION_LISTEN_NOW:
                mTitle = getString(R.string.title_section_listen_now);
                break;
            case SECTION_MY_SONGS:
                mTitle = getString(R.string.title_section_my_songs);
                break;
            case SECTION_PLAYLISTS:
                mTitle = getString(R.string.title_section_playlists);
                break;
            case SECTION_AUTOMIX:
                mTitle = getString(R.string.title_section_automix);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    public PlaybackState getPlaybackState() {
        return mPlaybackState;
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends AbstractRootFragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public PlaceholderFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static PlaceholderFragment newInstance(int sectionNumber) {
            PlaceholderFragment fragment = new PlaceholderFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            setupSearchBox(rootView);
            return rootView;
        }

        @Override
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            ((MainActivity) activity).onSectionAttached(
                    getArguments().getInt(ARG_SECTION_NUMBER));
        }
    }

}
