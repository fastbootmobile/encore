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

import com.fastbootmobile.encore.app.fragments.PlaylistViewFragment;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.utils.Utils;

import java.util.List;
import java.util.Map;

/**
 * Activity allowing user to view a Playlist contents through
 * a {@link com.fastbootmobile.encore.app.fragments.PlaylistViewFragment}
 */
public class PlaylistActivity extends AppActivity {
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final int BACK_DELAY = ArtistActivity.BACK_DELAY;
    public static final String BITMAP_PLAYLIST_HERO = "playlist_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private Bundle mInitialIntent; // TODO: Test rotation
    private PlaylistViewFragment mActiveFragment;
    private Handler mHandler = new Handler();
    private Toolbar mToolbar;
    private boolean mBackPending = false;
    private boolean mIsEntering;

    /**
     * Creates an intent starting this activity with the provided parameters
     * @param context The active context
     * @param playlist The playlist to watch
     * @return An intent to start this activity
     */
    public static Intent craftIntent(Context context, Playlist playlist, Bitmap hero) {
        Intent intent = new Intent(context, PlaylistActivity.class);
        intent.putExtra(PlaylistViewFragment.KEY_PLAYLIST, playlist.getRef());
        Utils.queueBitmap(BITMAP_PLAYLIST_HERO, hero);
        return intent;
    }

    /**
     * Creates an intent starting this activity with the provided parameters
     * @param context The active context
     * @param playlistRef The reference of the playlist to watch
     * @return An intent to start this activity
     */
    public static Intent craftIntent(Context context, String playlistRef, Bitmap hero) {
        Intent intent = new Intent(context, PlaylistActivity.class);
        intent.putExtra(PlaylistViewFragment.KEY_PLAYLIST, playlistRef);
        Utils.queueBitmap(BITMAP_PLAYLIST_HERO, hero);
        return intent;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_playlist);

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (PlaylistViewFragment) fm.findFragmentByTag(TAG_FRAGMENT);
        if (savedInstance == null) {
            mInitialIntent = getIntent().getExtras();
        } else {
            mInitialIntent = savedInstance.getBundle(EXTRA_RESTORE_INTENT);
        }

        if (mActiveFragment == null) {
            mActiveFragment = new PlaylistViewFragment();
            fm.beginTransaction()
                    .add(R.id.playlist_container, mActiveFragment, TAG_FRAGMENT)
                    .commit();
            mActiveFragment.setArguments(mInitialIntent);
        }

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

                        if (albumName.isAttachedToWindow()) {
                            if (mIsEntering) {
                                albumName.setVisibility(View.INVISIBLE);
                                Utils.animateCircleReveal(albumName, cx, cy, 0, radius,
                                        duration, 300);
                            } else {
                                albumName.setVisibility(View.VISIBLE);
                                Utils.animateCircleReveal(albumName, cx, cy, radius, 0,
                                        duration, 0);
                            }
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
    public boolean onCreateOptionsMenu(final Menu menu) {
        mActiveFragment.onCreateOptionsMenu(menu, getMenuInflater());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!mActiveFragment.onOptionsItemSelected(item)) {
            if (item.getItemId() == android.R.id.home) {
                onBackPressed();
                return true;
            } else {
                return super.onOptionsItemSelected(item);
            }
        } else {
            return true;
        }
    }

    @Override
    public void onBackPressed() {
        if (Utils.hasLollipop()) {
            mIsEntering = false;
            mActiveFragment.notifyReturnTransition();
        }
        super.onBackPressed();
    }
}
