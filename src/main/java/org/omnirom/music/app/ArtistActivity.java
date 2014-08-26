package org.omnirom.music.app;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.omnirom.music.app.adapters.ArtistsAdapter;
import org.omnirom.music.app.fragments.ArtistFragment;
import org.omnirom.music.app.ui.AlbumArtImageView;

public class ArtistActivity extends FragmentActivity {

    private static final String TAG = "ArtistActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ARTIST_HERO = "artist_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private ArtistFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;

    public static Intent craftIntent(Context ctx, Bitmap hero, String artistRef, int color) {
        Intent intent = new Intent(ctx, ArtistActivity.class);

        intent.putExtra(ArtistActivity.EXTRA_ARTIST, artistRef);
        intent.putExtra(ArtistActivity.EXTRA_BACKGROUND_COLOR, color);

        Utils.queueBitmap(ArtistActivity.BITMAP_ARTIST_HERO, hero);

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

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
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle("");
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            /*
            getWindow().getEnterTransition().addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    View fab = mActiveFragment.findViewById(R.id.fabPlay);
                    fab.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    View fab = mActiveFragment.findViewById(R.id.fabPlay);
                    fab.setVisibility(View.VISIBLE);

                    // get the center for the clipping circle
                    int cx = fab.getMeasuredWidth() / 2;
                    int cy = fab.getMeasuredHeight() / 2;

                    // get the final radius for the clipping circle
                    final int finalRadius = fab.getWidth();

                    // create and start the animator for this view
                    // (the start radius is zero)
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                        ValueAnimator anim =
                                ViewAnimationUtils.createCircularReveal(fab, cx, cy, 0, finalRadius);
                        anim.setInterpolator(new DecelerateInterpolator());
                        anim.start();
                    }

                    fab.setTranslationX(-fab.getMeasuredWidth() / 4.0f);
                    fab.setTranslationY(-fab.getMeasuredHeight() / 4.0f);
                    fab.animate().translationX(0.0f).translationY(0.0f)
                            .setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime))
                            .setInterpolator(new DecelerateInterpolator())
                            .start();

                    getWindow().getEnterTransition().removeListener(this);
                }

                @Override
                public void onTransitionCancel(Transition transition) {

                }

                @Override
                public void onTransitionPause(Transition transition) {

                }

                @Override
                public void onTransitionResume(Transition transition) {

                }
            });*/
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(EXTRA_RESTORE_INTENT, mInitialIntent);
        Utils.queueBitmap(BITMAP_ARTIST_HERO, mHero);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.artist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == android.R.id.home) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
                // finishAfterTransition();
            } else {
                finish();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
