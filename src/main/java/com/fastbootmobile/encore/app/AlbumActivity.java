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
import android.app.SharedElementCallback;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.fastbootmobile.encore.app.fragments.AlbumViewFragment;
import com.fastbootmobile.encore.app.fragments.PlaylistChooserFragment;
import com.fastbootmobile.encore.framework.PlaybackProxy;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.List;
import java.util.Map;

/**
 * Activity for viewing an album details through
 * {@link com.fastbootmobile.encore.app.fragments.AlbumViewFragment}
 */
public class AlbumActivity extends AppActivity {
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ALBUM_HERO = "album_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    public static final int BACK_DELAY = 300;

    private AlbumViewFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;
    private Handler mHandler;
    private Toolbar mToolbar;
    private boolean mIsEntering;

    /**
     * Creates a proper intent to open this activity
     * @param context The original context
     * @param hero The hero image bitmap
     * @param albumRef The reference of the album to view
     * @param backColor The back color of the header bar
     * @return An intent to open this activity
     */
    public static Intent craftIntent(Context context, Bitmap hero, String albumRef,
                                     ProviderIdentifier provider, int backColor) {
        Intent intent = new Intent(context, AlbumActivity.class);

        intent.putExtra(EXTRA_ALBUM, albumRef);
        intent.putExtra(EXTRA_PROVIDER, provider.serialize());
        intent.putExtra(EXTRA_BACKGROUND_COLOR, backColor);
        Utils.queueBitmap(BITMAP_ALBUM_HERO, hero);

        return intent;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        mHandler = new Handler();

        // Load or restore the fragment
        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (AlbumViewFragment) fm.findFragmentByTag(TAG_FRAGMENT);

        if (savedInstanceState == null) {
            mHero = Utils.dequeueBitmap(BITMAP_ALBUM_HERO);
            mInitialIntent = getIntent().getExtras();
        } else {
            mHero = Utils.dequeueBitmap(BITMAP_ALBUM_HERO);
            mInitialIntent = savedInstanceState.getBundle(EXTRA_RESTORE_INTENT);
        }

        if (mActiveFragment == null) {
            mActiveFragment = new AlbumViewFragment();
            fm.beginTransaction()
                    .add(R.id.container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
        }

        mActiveFragment.setArguments(mHero, mInitialIntent);

        // Remove the activity title as we don't want it here
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setTitle("");
        }

        mIsEntering = true;

        if (Utils.hasLollipop()) {
            setEnterSharedElementCallback(new SharedElementCallback() {
                @Override
                public void onMapSharedElements(List<String> names, Map<String, View> sharedElements) {
                    View imageHeader = mActiveFragment.getHeroImageView();
                    if (imageHeader != null) {
                        sharedElements.put("itemImage", imageHeader);
                    }

                    View albumName = mActiveFragment.findViewById(R.id.tvAlbumName);
                    if (albumName != null) {
                        final int cx = albumName.getMeasuredWidth() / 4;
                        final int cy = albumName.getMeasuredHeight() / 2;
                        final int duration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
                        final int radius = Utils.getEnclosingCircleRadius(albumName, cx, cy);

                        try {
                            if (mIsEntering) {
                                albumName.setVisibility(View.INVISIBLE);
                                Utils.animateCircleReveal(albumName, cx, cy, 0, radius, duration, 300);
                            } else {
                                albumName.setVisibility(View.VISIBLE);
                                Utils.animateCircleReveal(albumName, cx, cy, radius, 0, duration, 0);
                            }
                        } catch (IllegalStateException ignore) {
                            // Animation was cancelled before animation mapping
                        }
                    }
                }
            });
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(EXTRA_RESTORE_INTENT, mInitialIntent);
        Utils.queueBitmap(BITMAP_ALBUM_HERO, mHero);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.album, menu);

        // Keep in tune with the XML!
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Runnable updateMenuArtist = new Runnable() {
            @Override
            public void run() {
                String artistRef = mActiveFragment.getArtist();
                MenuItem item = menu.getItem(2);

                if (artistRef != null) {
                    final Artist artist = aggregator.retrieveArtist(artistRef, mActiveFragment.getAlbum().getProvider());
                    if (artist != null) {
                        item.setTitle(getString(R.string.more_from, artist.getName()));
                        item.setVisible(true);
                    }
                } else {
                    // We don't have the tracks for that album yet, try again.
                    mHandler.postDelayed(this, 1000);
                    item.setVisible(false);
                }
            }
        };
        updateMenuArtist.run();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.menu_add_to_queue) {
            Album album = mActiveFragment.getAlbum();
            if (album != null && album.isLoaded()) {
                PlaybackProxy.queueAlbum(album, false);
            } else {
                Utils.shortToast(this, R.string.toast_album_not_loaded_yet);
            }
            return true;
        } else if (id == R.id.menu_add_to_playlist) {
            Album album = mActiveFragment.getAlbum();
            if (album != null && album.isLoaded()) {
                PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(album);
                fragment.show(getSupportFragmentManager(), album.getRef());
            } else {
                Utils.shortToast(this, R.string.toast_album_not_loaded_yet);
            }
        } else if (id == R.id.menu_more_from_artist) {
            String artistRef = mActiveFragment.getArtist();
            Intent intent = ArtistActivity.craftIntent(this, null, artistRef,
                    mActiveFragment.getAlbum().getProvider(),
                    getResources().getColor(R.color.default_album_art_background));
            startActivity(intent);
        } else if (id == android.R.id.home) {
            supportFinishAfterTransition();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        mIsEntering = false;

        if (Utils.hasLollipop()) {
            mActiveFragment.notifyReturnTransition();
        }

        super.onBackPressed();
    }

}
