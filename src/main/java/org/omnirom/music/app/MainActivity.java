package org.omnirom.music.app;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.Toast;

import com.williammora.snackbar.Snackbar;

import org.omnirom.music.app.fragments.AutomixFragment;
import org.omnirom.music.app.fragments.DspProvidersFragment;
import org.omnirom.music.app.fragments.HistoryFragment;
import org.omnirom.music.app.fragments.ListenNowFragment;
import org.omnirom.music.app.fragments.MySongsFragment;
import org.omnirom.music.app.fragments.NavigationDrawerFragment;
import org.omnirom.music.app.fragments.PlaylistListFragment;
import org.omnirom.music.app.fragments.RecognitionFragment;
import org.omnirom.music.app.ui.PlayingBarView;
import org.omnirom.music.cast.CastModule;
import org.omnirom.music.art.ImageCache;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderConnection;
import org.omnirom.music.utils.Utils;

import java.util.List;

public class MainActivity extends AppActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private static final String TAG = "MainActivity";

    public static final int SECTION_LISTEN_NOW = 1;
    public static final int SECTION_MY_SONGS   = 2;
    public static final int SECTION_PLAYLISTS  = 3;
    public static final int SECTION_AUTOMIX    = 4;
    public static final int SECTION_RECOGNITION= 5;
    public static final int SECTION_HISTORY    = 6;
    public static final int SECTION_NOW_PLAYING= 7;
    public static final int SECTION_DRIVE_MODE = 8;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    private PlayingBarView mPlayingBarLayout;

    private boolean mRestoreBarOnBack;

    private CastModule mCastModule;

    private Handler mHandler;

    private int mCurrentFragmentIndex = -1;

    private MenuItem mOfflineMenuItem;

    private ProviderConnection mConfiguringProvider;

    private int mOrientation;

    private Toolbar mToolbar;


    public MainActivity() {
        mHandler = new Handler();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!WelcomeActivity.hasDoneWelcomeWizard(this)) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            startActivity(intent);
            finish();
        } else {
            setContentView(R.layout.activity_main);

            mToolbar = (Toolbar) findViewById(R.id.toolbar);
            setSupportActionBar(mToolbar);

            mNavigationDrawerFragment = (NavigationDrawerFragment)
                    getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
            mTitle = getTitle();

            // Set up the drawer.
            mNavigationDrawerFragment.setUp(
                    R.id.navigation_drawer,
                    (DrawerLayout) findViewById(R.id.drawer_layout));


            // Setup the playing bar click listener
            mPlayingBarLayout = (PlayingBarView) findViewById(R.id.playingBarLayout);
            mPlayingBarLayout.setWrapped(true, false);

            // Setup Cast button
            mCastModule = new CastModule(getApplicationContext());

            // Look for un-configured plugins in a second
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    lookForUnconfiguredProviders();
                }
            }, 1000);
        }
    }

    public Toolbar getToolbar() {
        return mToolbar;
    }

    private void lookForUnconfiguredProviders() {
        List<ProviderConnection> providers = PluginsLookup.getDefault().getAvailableProviders();
        for (final ProviderConnection conn : providers) {
            try {
                if (conn.getBinder() != null && !conn.getBinder().isSetup()
                        && conn.getConfigurationActivity() != null) {
                    notifyUnconfiguredProvider(conn);
                    break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot get " + conn + " status", e);
            }
        }
    }

    private void notifyUnconfiguredProvider(final ProviderConnection conn) {
        showSnackBar(getString(R.string.plugin_not_configured_snackbar, conn.getProviderName()),
                getString(R.string.configure),
                new Snackbar.ActionClickListener() {
                    @Override
                    public void onActionClicked() {
                        Intent i = new Intent();
                        i.setClassName(conn.getPackage(), conn.getConfigurationActivity());
                        try {
                            mConfiguringProvider = conn;
                            startActivity(i);
                        } catch (SecurityException e) {
                            Log.e(TAG, "Cannot start: Is your activity not exported?");
                            Toast.makeText(MainActivity.this,
                                    "Cannot start: Make sure you set 'exported=true' flag on your settings activity.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    public void showSnackBar(String message, String button, Snackbar.ActionClickListener listener) {
        Snackbar.with(getApplicationContext())
                .type(Snackbar.SnackbarType.MULTI_LINE)
                .text(message)
                .actionLabel(button)
                .actionListener(listener)
                .duration(Snackbar.SnackbarDuration.LENGTH_VERY_LONG)
                .show(this);
    }

    public boolean isPlayBarVisible() {
        return mPlayingBarLayout.isVisible();
    }

    @Override
    public void onBackPressed() {
        if (!mPlayingBarLayout.isWrapped()) {
            mPlayingBarLayout.setWrapped(true);
        } else {
            if (mCurrentFragmentIndex < 0) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCurrentFragmentIndex = 0;
                        restoreActionBar();
                    }
                });
            }
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        PluginsLookup.getDefault().requestUpdatePlugins();

        mPlayingBarLayout.onResume();

        if (mConfiguringProvider != null) {
            IMusicProvider provider = mConfiguringProvider.getBinder();
            if (provider != null) {
                try {
                    if (provider.isSetup()) {
                        provider.login();
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Remote exception while trying to login configured provider", e);
                }
            } else {
                Log.w(TAG, "Configured provider is null!");
            }
        }
    }

    @Override
    protected void onPause() {
        mPlayingBarLayout.onPause();
        super.onPause();
    }

    @Override
    protected void onStart() {
        mCastModule.onStart();
        super.onStart();
    }


    @Override
    protected void onStop() {
        mCastModule.onStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }

        // Release services connections if playback isn't happening
        PluginsLookup.getDefault().releasePlaybackServiceIfPossible();
        ImageCache.getDefault().evictAll();
        System.gc();

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mOrientation != newConfig.orientation) {
            mOrientation = newConfig.orientation;
        }
    }

    @Override
    public boolean onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        boolean result = true;
        try {
            if (position + 1 != SECTION_DRIVE_MODE && position + 1 != SECTION_NOW_PLAYING) {
                mCurrentFragmentIndex = position;
            }

            final String fragmentTag = ""+position+"_"+mOrientation;

            Fragment newFrag = null;
            switch (position + 1) {
                case SECTION_LISTEN_NOW:
                    newFrag = ListenNowFragment.newInstance();
                    break;
                case SECTION_PLAYLISTS:
                    newFrag = PlaylistListFragment.newInstance(true);
                    break;
                case SECTION_MY_SONGS:
                    newFrag = MySongsFragment.newInstance();
                    break;
                case SECTION_AUTOMIX:
                    newFrag = AutomixFragment.newInstance();
                    break;
                case SECTION_RECOGNITION:
                    newFrag = RecognitionFragment.newInstance();
                    break;
                case SECTION_HISTORY:
                    newFrag = HistoryFragment.newInstance();
                    break;
                case SECTION_NOW_PLAYING:
                    startActivity(new Intent(this, PlaybackQueueActivity.class));
                    break;
                case SECTION_DRIVE_MODE:
                    startActivity(new Intent(this, DriveModeActivity.class));
                    break;
            }

            if (newFrag != null) {
                showFragment(newFrag, false, fragmentTag);
                result = true;
            } else {
                result = false;
            }
        } catch (IllegalStateException e) {
            // The app is pausing
        }

        return result;
    }

    public void showFragment(Fragment f, boolean addToStack, String tag) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0 && !addToStack) {
            fragmentManager.popBackStack();
            if (mRestoreBarOnBack) {
                mRestoreBarOnBack = false;
            }
        }

        FragmentTransaction ft = fragmentManager.beginTransaction();
        if (addToStack) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE);
            ft.addToBackStack(f.toString());
        } else {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        ft.replace(R.id.container, f, tag);
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
            case SECTION_RECOGNITION:
                mTitle = getString(R.string.title_section_recognition);
                break;
            case SECTION_HISTORY:
                mTitle = getString(R.string.section_history);
                break;
        }
    }

    public void setContentShadowTop(final float pxTop) {
        View actionBarShadow = findViewById(R.id.action_bar_shadow);
        if (actionBarShadow != null) {
            actionBarShadow.setTranslationY(pxTop + mToolbar.getMeasuredHeight());
        } else {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    setContentShadowTop(pxTop);
                }
            });
        }
    }

    public float getContentShadowTop() {
        View actionBarShadow = findViewById(R.id.action_bar_shadow);
        if (actionBarShadow != null) {
            return actionBarShadow.getTranslationY();
        } else {
            return 0;
        }
    }

    public void restoreActionBar() {
        if (mToolbar != null) {
            mToolbar.setTitle(mTitle);
        }
    }

    public void toggleOfflineMode() {
        mOfflineMenuItem.setChecked(!mOfflineMenuItem.isChecked());
        ProviderAggregator.getDefault().notifyOfflineMode(mOfflineMenuItem.isChecked());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.main, menu);
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);

            SearchView searchView = (SearchView) menu.findItem(R.id.action_search)
                    .getActionView();
            searchView.setSearchableInfo(searchManager
                    .getSearchableInfo(getComponentName()));

            // Setup cast button on 4.2+
            MenuItem castMenu = menu.findItem(R.id.action_cast);
            if (Utils.hasJellyBeanMR1()) {
                MediaRouteActionProvider mediaRouteActionProvider =
                        (MediaRouteActionProvider) MenuItemCompat.getActionProvider(castMenu);
                mediaRouteActionProvider.setRouteSelector(mCastModule.getSelector());
                castMenu.setVisible(true);
            } else {
                Log.w(TAG, "Api too low to show cast action");
                castMenu.setVisible(false);
            }

            // Offline mode
            mOfflineMenuItem = menu.findItem(R.id.action_offline_mode);
            ProviderAggregator aggregator = ProviderAggregator.getDefault();
            if (aggregator.hasNetworkConnectivity()) {
                mOfflineMenuItem.setChecked(aggregator.isOfflineMode());
            } else {
                mOfflineMenuItem.setEnabled(false);
            }

            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;

            case R.id.action_sound_effects:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                intent.putExtra("DSP", true);
                startActivity(intent);
                break;

            case R.id.action_offline_mode:
                toggleOfflineMode();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
