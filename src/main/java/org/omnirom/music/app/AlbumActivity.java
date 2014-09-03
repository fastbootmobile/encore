package org.omnirom.music.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.media.AudioManager;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.transition.Transition;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import org.omnirom.music.app.adapters.AlbumsAdapter;
import org.omnirom.music.app.fragments.AlbumViewFragment;
import org.omnirom.music.app.fragments.ArtistFragment;
import org.omnirom.music.app.fragments.PlaylistChooserFragment;
import org.omnirom.music.framework.PluginsLookup;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.service.IPlaybackService;

import omnimusic.Plugin;

public class AlbumActivity extends FragmentActivity {

    private static final String TAG = "AlbumActivity";
    private static final String TAG_FRAGMENT = "fragment_inner";

    public static final String EXTRA_ALBUM = "album";
    public static final String EXTRA_BACKGROUND_COLOR = "background_color";
    public static final String BITMAP_ALBUM_HERO = "album_hero";
    private static final String EXTRA_RESTORE_INTENT = "restore_intent";

    private AlbumViewFragment mActiveFragment;
    private Bundle mInitialIntent;
    private Bitmap mHero;
    private Handler mHandler;

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

        mHandler = new Handler();

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
        getActionBar().setTitle("");
        getActionBar().setDisplayHomeAsUpEnabled(true);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);
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
        final ProviderCache cache = ProviderAggregator.getDefault().getCache();
        final Runnable updateMenuArtist = new Runnable() {
            @Override
            public void run() {
                String artistRef = mActiveFragment.getArtist();
                MenuItem item = menu.getItem(2);

                if (artistRef != null) {
                    item.setTitle(getString(R.string.more_from, cache.getArtist(artistRef).getName()));
                    item.setVisible(true);
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
            IPlaybackService service = PluginsLookup.getDefault().getPlaybackService();
            try {
                service.queueAlbum(mActiveFragment.getAlbum(), false);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot queue album", e);
            }
            return true;
        } else if (id == R.id.menu_add_to_playlist) {
            Album album = mActiveFragment.getAlbum();
            PlaylistChooserFragment fragment = PlaylistChooserFragment.newInstance(album);
            fragment.show(getSupportFragmentManager(), album.getRef());
        } else if (id == R.id.menu_more_from_artist) {
            String artistRef = mActiveFragment.getArtist();
            Intent intent = ArtistActivity.craftIntent(this, null, artistRef,
                    getResources().getColor(R.color.default_album_art_background));
            startActivity(intent);
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
