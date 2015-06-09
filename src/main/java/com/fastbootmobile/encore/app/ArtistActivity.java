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

import android.animation.Animator;
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
import android.view.ViewAnimationUtils;
import android.view.animation.DecelerateInterpolator;

import com.fastbootmobile.encore.app.fragments.ArtistFragment;
import com.fastbootmobile.encore.providers.ProviderIdentifier;
import com.fastbootmobile.encore.utils.Utils;

import java.util.List;
import java.util.Map;

/**
 * Activity to view an Artist tracks, similar, etc. through
 * an {@link com.fastbootmobile.encore.app.fragments.ArtistFragment}
 */
public class ArtistActivity extends AppActivity {
    private static final String TAG = "ArtistActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_PROVIDER = "provider";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ARTIST_HERO = "artist_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    public static final int BACK_DELAY = 400;

    private Bundle mInitialIntent;
    private Bitmap mHero;
    private ArtistFragment mActiveFragment;
    private Handler mHandler;
    private Toolbar mToolbar;
    private boolean mIsEntering;

    /**
     * Creates a proper intent to open this activity
     *
     * @param ctx       The current context
     * @param hero      The hero (artist image) bitmap
     * @param artistRef The reference of the artist
     * @param provider  The provider of the artist
     * @param color     The back color of the header bar
     * @return An intent that will open this activity
     */
    public static Intent craftIntent(Context ctx, Bitmap hero, String artistRef,
                                     ProviderIdentifier provider, int color) {
        Intent intent = new Intent(ctx, ArtistActivity.class);

        intent.putExtra(ArtistActivity.EXTRA_ARTIST, artistRef);
        intent.putExtra(ArtistActivity.EXTRA_PROVIDER, provider);
        intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR, color);

        Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, hero);

        return intent;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        mHandler = new Handler();

        FragmentManager fm = getSupportFragmentManager();
        mActiveFragment = (ArtistFragment) fm.findFragmentByTag(TAG_FRAGMENT);

        if (savedInstanceState == null) {
            mHero = Utils.dequeueBitmap(BITMAP_ARTIST_HERO);
            mInitialIntent = getIntent().getExtras();
        } else {
            mHero = Utils.dequeueBitmap(BITMAP_ARTIST_HERO);
            mInitialIntent = savedInstanceState.getBundle(EXTRA_RESTORE_INTENT);
        }

        if (mActiveFragment == null) {
            mActiveFragment = new ArtistFragment();
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
                    View fab = mActiveFragment.findViewById(R.id.fabPlay);
                    fab.setVisibility(View.VISIBLE);

                    // get the center for the clipping circle
                    int cx = fab.getMeasuredWidth() / 2;
                    int cy = fab.getMeasuredHeight() / 2;

                    // get the final radius for the clipping circle
                    final int finalRadius = fab.getWidth();

                    // create and start the animator for this view
                    // (the start radius is zero)
                    Animator anim;
                    if (mIsEntering) {
                        anim = ViewAnimationUtils.createCircularReveal(fab, cx, cy, 0, finalRadius);
                    } else {
                        anim = ViewAnimationUtils.createCircularReveal(fab, cx, cy, finalRadius, 0);
                    }
                    anim.setInterpolator(new DecelerateInterpolator());
                    anim.start();

                    fab.setTranslationX(-fab.getMeasuredWidth() / 4.0f);
                    fab.setTranslationY(-fab.getMeasuredHeight() / 4.0f);
                    fab.animate().translationX(0.0f).translationY(0.0f)
                            .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

                    final View artistName = mActiveFragment.findViewById(R.id.tvArtist);
                    if (artistName != null) {
                        cx = artistName.getMeasuredWidth() / 4;
                        cy = artistName.getMeasuredHeight() / 2;
                        final int duration = getResources().getInteger(android.R.integer.config_mediumAnimTime);
                        final int radius = Utils.getEnclosingCircleRadius(artistName, cx, cy);

                        if (mIsEntering) {
                            artistName.setVisibility(View.INVISIBLE);
                            Utils.animateCircleReveal(artistName, cx, cy, 0, radius,
                                    duration, 300);
                        } else {
                            artistName.setVisibility(View.VISIBLE);
                            Utils.animateCircleReveal(artistName, cx, cy, radius, 0,
                                    duration, 0);
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
        Utils.queueBitmap(BITMAP_ARTIST_HERO, mHero);
    }


    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (Utils.hasLollipop()) {
            mIsEntering = false;
            mActiveFragment.scrollToTop();
            mActiveFragment.notifyClosing();
        }
        super.onBackPressed();
    }
}
