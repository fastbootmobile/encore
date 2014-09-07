package org.omnirom.music.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import org.omnirom.music.app.R;
import org.omnirom.music.app.Utils;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.AlbumArtHelper;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Guigui on 16/07/2014.
 */
public class AlbumArtImageView extends SquareImageView implements AlbumArtHelper.AlbumArtListener {

    private static final String TAG = "AlbumArtImageView";
    private static final int DELAY_BEFORE_START = 300;

    private Handler mHandler;
    private OnArtLoadedListener mOnArtLoadedListener;
    private AlbumArtHelper.AlbumArtTask mTask;
    private BoundEntity mRequestedEntity;
    private MaterialTransitionDrawable mDrawable;
    private boolean mCrossfade;


    public AlbumArtImageView(Context context) {
        super(context);
        initialize();
    }

    public AlbumArtImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public AlbumArtImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        // Set the placeholder art first-hand
        mHandler = new Handler();
        if (isInEditMode()) {
            setImageDrawable(getResources().getDrawable(R.drawable.album_placeholder));
        } else {
            mDrawable = new MaterialTransitionDrawable(getContext(),
                    (BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder));
            setImageDrawable(mDrawable);
        }
    }

    private void forceDrawableReload() {
        // Our drawable is likely changing bounds. From ImageView source code, it seems
        // like there's no way for the drawable to tell the view "hey, I resized", so we
        // manually trigger "updateDrawable()" (a private method in ImageView) by setting
        // the drawable again when we know it changes
        setImageDrawable(null);
        setImageDrawable(mDrawable);
    }

    public void setDefaultArt() {
        mDrawable.setImmediateTo((BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder));
        forceDrawableReload();
    }

    public void setOnArtLoadedListener(OnArtLoadedListener listener) {
        mOnArtLoadedListener = listener;
    }

    /**
     * When the AlbumArtImageView is in crossfade mode, the album art won't go through the default
     * placeholder state before moving into the next album art, but will crossfade to the next
     * art directly
     * @param crossfade true to enable crossfade mode
     */
    public void setCrossfade(boolean crossfade) {
        mCrossfade = crossfade;
    }

    /**
     * Returns the last requested entity
     * @return A BoundEntity (a song, album, artist) that was previously requested
     */
    public BoundEntity getRequestedEntity() {
        return mRequestedEntity;
    }

    public void loadArtForSong(final Song song) {
        loadArtImpl(song);
    }

    public void loadArtForAlbum(final Album album) {
        loadArtImpl(album);
    }

    public void loadArtForArtist(final Artist artist) {
        loadArtImpl(artist);
    }

    public void loadArtForPlaylist(final Playlist playlist) {
        String mainArtistRef = Utils.getMainArtist(playlist);
        if (mainArtistRef != null) {
            Artist artist = ProviderAggregator.getDefault().retrieveArtist(mainArtistRef,
                    playlist.getProvider());
            loadArtImpl(artist);
        } else {
            setDefaultArt();
        }
    }

    private void loadArtImpl(final BoundEntity ent) {
        if (ent == null || ent.equals(mRequestedEntity)) {
            // Nothing to do, we are displaying the proper thing already
            return;
        } else if (!mCrossfade) {
            setDefaultArt();
        }

        if (mTask != null) {
            mTask.cancel(true);
        }

        // We delay the loading slightly to make sure we don't uselessly load an image that is
        // being quickly flinged through (the requested entity will change in-between as the
        // view is recycled). We don't wait however in crossfade mode as we expect to see the
        // image as soon as possible.
        mRequestedEntity = ent;

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (mRequestedEntity.equals(ent)) {
                    mTask = AlbumArtHelper.retrieveAlbumArt(getContext(),
                            AlbumArtImageView.this, ent);
                }
            }
        };

        // When we're crossfading, we're assuming we want the image directly
        if (mCrossfade) {
            mHandler.post(runnable);
        } else {
            mHandler.postDelayed(runnable, DELAY_BEFORE_START);
        }
    }


    @Override
    public void onArtLoaded(Bitmap output, BoundEntity request) {
        // If we have an actual result, display it!
        if (output != null) {
            BitmapDrawable drawable = new BitmapDrawable(getResources(), output);
            if (drawable.getBitmap() != null)
            mDrawable.transitionTo(getResources(), drawable);
            forceDrawableReload();

            if (mOnArtLoadedListener != null) {
                mOnArtLoadedListener.onArtLoaded(this, drawable);
            }
        }
    }

    public interface OnArtLoadedListener {
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable);
    }

}
