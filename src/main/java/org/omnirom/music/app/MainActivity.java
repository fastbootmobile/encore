package org.omnirom.music.app;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.drawable.BitmapDrawable;
import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import org.omnirom.music.app.fragments.AbstractRootFragment;
import org.omnirom.music.app.fragments.MySongsFragment;
import org.omnirom.music.app.fragments.NavigationDrawerFragment;
import org.omnirom.music.app.fragments.NowPlayingFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.SettingsFragment;
import org.omnirom.music.app.ui.BlurringFrameLayout;
import org.omnirom.music.app.ui.KenBurnsView;
import org.omnirom.music.app.ui.PlayingBarView;
import org.omnirom.music.framework.PlaybackCallbackImpl;
import org.omnirom.music.framework.PlaybackState;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Song;

import org.omnirom.music.providers.MultiProviderDatabaseHelper;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.service.IPlaybackCallback;
import org.omnirom.music.service.IPlaybackService;


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

    private PlayingBarView mPlayingBarLayout;

    private boolean mRestoreBarOnBack;

    private Handler mHandler;


    public MainActivity() {
        mPlaybackState = new PlaybackState();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout), getTheme());


        // Setup the playing bar click listener
        mPlayingBarLayout = (PlayingBarView) findViewById(R.id.playingBarLayout);

        // We start it hidden. As we don't have the exact size yet, we post it for later change
        /*mPlayingBarLayout.post(new Runnable() {
            @Override
            public void run() {
                mPlayingBarLayout.setTranslationY(mPlayingBarLayout.getMeasuredHeight());
            }
        });*/
    }

    public void setPlayingBarVisible(boolean visible) {
        mPlayingBarLayout.animateVisibility(visible);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mRestoreBarOnBack) {
            setPlayingBarVisible(true);
            mRestoreBarOnBack = false;
        }
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
    protected void onDestroy() {
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }

        // Release services connections if playback isn't happening
        IPlaybackService playbackService = PluginsLookup.getDefault().getPlaybackService();
        try {
            if (!playbackService.isPlaying()) {
                PluginsLookup.getDefault().tearDown();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Cannot determine if playbackservice is running");
        }

        super.onDestroy();
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
                case SECTION_MY_SONGS :
                    newFrag = MySongsFragment.newInstance();
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
            if (mRestoreBarOnBack) {
                setPlayingBarVisible(true);
                mRestoreBarOnBack = false;
            }
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
