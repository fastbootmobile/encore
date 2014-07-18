package org.omnirom.music.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.fragments.AlbumViewFragment;
import org.omnirom.music.app.fragments.ArtistFragment;
import org.omnirom.music.model.Album;

public class AlbumActivity extends Activity {

    private static final String TAG = "AlbumActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ALBUM_HERO = "album_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private AlbumViewFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;

    public static Intent craftIntent(Context context, Bitmap hero, Album album, int backColor) {
        Intent intent = new Intent(context, AlbumActivity.class);

        intent.putExtra(AlbumActivity.EXTRA_ALBUM, album);
        intent.putExtra(AlbumActivity.EXTRA_BACKGROUND_COLOR, backColor);
        Utils.queueBitmap(BITMAP_ALBUM_HERO, hero);

        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_artist);

        FragmentManager fm = getFragmentManager();
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
        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBundle(EXTRA_RESTORE_INTENT, mInitialIntent);
        Utils.queueBitmap(BITMAP_ALBUM_HERO, mHero);
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
