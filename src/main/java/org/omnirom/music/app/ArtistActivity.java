package org.omnirom.music.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.FragmentManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.animation.DecelerateInterpolator;

import org.omnirom.music.app.fragments.ArtistFragment;

public class ArtistActivity extends Activity {

    private static final String TAG = "ArtistActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ARTIST = "artist";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ARTIST_HERO = "artist_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private ArtistFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        FragmentManager fm = getFragmentManager();
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
        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);

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
                ValueAnimator anim =
                        ViewAnimationUtils.createCircularReveal(fab, cx, cy, 0, finalRadius);
                anim.setInterpolator(new DecelerateInterpolator());
                anim.start();

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
        });
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
        getMenuInflater().inflate(R.menu.artist, menu);
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
            finishAfterTransition();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
