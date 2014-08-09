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
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Guigui on 16/07/2014.
 */
public class AlbumArtImageView extends SquareImageView {

    public interface OnArtLoadedListener {
        public void onArtLoaded(AlbumArtImageView view, BitmapDrawable drawable);
    }

    private class BackgroundResult {
        public BoundEntity request;
        public BitmapDrawable drawable;
        public boolean retry;
    }

    private class BackgroundTask extends AsyncTask<BoundEntity, Void, BackgroundResult> {
        private BoundEntity mEntity;

        @Override
        protected BackgroundResult doInBackground(BoundEntity... params) {
            BackgroundResult output = new BackgroundResult();
            mEntity = params[0];
            output.request = mEntity;

            if (mEntity == null) {
                return null;
            }

            final Resources res = getResources();
            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            // Prepare the placeholder/default
            BitmapDrawable drawable = (BitmapDrawable) res.getDrawable(R.drawable.album_placeholder);
            Bitmap bmp = drawable.getBitmap();

            Bitmap cachedImage = null;
            String artKey;

            // Load the art key based on what we need
            if (mEntity instanceof Album) {
                artKey = cache.getAlbumArtKey((Album) mEntity);
            } else if (mEntity instanceof Song) {
                artKey = cache.getSongArtKey((Song) mEntity);
            } else if (mEntity instanceof  Artist) {
                artKey = cache.getArtistArtKey((Artist) mEntity);
            } else {
                throw new RuntimeException("Album art entity should be a song or an album");
            }

            // If we have the key in cache already, try to see if we have it loaded in cache
            if (artKey != null) {
                cachedImage = ImageCache.getDefault().get(artKey);
            }

            // If we have it in cache, set it as the bitmap to display, otherwise load it from the
            // web provider
            final AlbumArtCache artCache = AlbumArtCache.getDefault();
            if (cachedImage != null) {
                bmp = cachedImage;
            } else {
                String artUrl = null;

                // Don't allow other views to run the same query
                if (artCache.isQueryRunning(mEntity)) {
                    // A query is already running for this entity, we'll revisit it later
                    output.retry = true;
                    return output;
                } else {
                    artCache.notifyQueryRunning(mEntity);
                }

                // The image isn't loaded, if we don't have the artKey either, load both
                if (artKey == null) {
                    StringBuffer urlBuffer = new StringBuffer();
                    if (mEntity instanceof Album) {
                        artKey = AlbumArtCache.getDefault().getArtKey((Album) mEntity, urlBuffer);
                    } else if (mEntity instanceof Song) {
                        artKey = AlbumArtCache.getDefault().getArtKey((Song) mEntity, urlBuffer);
                    } else if (mEntity instanceof Artist) {
                        artKey = AlbumArtCache.getDefault().getArtKey((Artist) mEntity,urlBuffer);
                    }

                    artUrl = urlBuffer.toString();
                }

                // We now have the art key, download the actual image if it's not the default art
                if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                    bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, bmp);
                }
            }

            // We now have a bitmap to display, so let's put it!
            output.drawable = new BitmapDrawable(res, bmp);
            output.retry = false;

            // Cache the image
            if (mEntity instanceof Album) {
                cache.putAlbumArtKey((Album) mEntity, artKey);
            } else if (mEntity instanceof Song) {
                cache.putSongArtKey((Song) mEntity, artKey);
            } else if (mEntity instanceof  Artist) {
                cache.putArtistArtKey((Artist) mEntity, artKey);
            }

            // In all cases, we tell that this entity is loaded
            artCache.notifyQueryStopped(mEntity);

            return output;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            final AlbumArtCache artCache = AlbumArtCache.getDefault();
            artCache.notifyQueryStopped(mEntity);
        }

        @Override
        protected void onPostExecute(final BackgroundResult result) {
            super.onPostExecute(result);

            // If we have an actual result, display it!
            if (result != null && result.drawable != null && !result.retry) {
                mDrawable.transitionTo(getResources(), result.drawable);
                //invalidateDrawable(mDrawable);
                //postInvalidate();


                if (mOnArtLoadedListener != null) {
                    mOnArtLoadedListener.onArtLoaded(AlbumArtImageView.this, result.drawable);
                }
            } else if (result != null && result.retry) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mTask = new BackgroundTask();
                        mTask.executeOnExecutor(ART_POOL_EXECUTOR, result.request);
                    }
                }, DELAY_BEFORE_RETRY);
            }
        }
    }

    private Handler mHandler;
    private OnArtLoadedListener mOnArtLoadedListener;
    private BackgroundTask mTask;
    private BoundEntity mRequestedEntity;
    private MaterialTransitionDrawable mDrawable;

    private static final int DELAY_BEFORE_START = 150;
    private static final int DELAY_BEFORE_RETRY = 60;
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = 256;
    private static final int KEEP_ALIVE = 5;
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "Art AsyncTask #" + mCount.getAndIncrement());
        }
    };
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(10);
    private static final Executor ART_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);

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
        mDrawable = new MaterialTransitionDrawable(getContext(),
                (BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder));
        setImageDrawable(mDrawable);
    }

    public void setDefaultArt() {
        mDrawable.setImmediateTo((BitmapDrawable) getResources().getDrawable(R.drawable.album_placeholder));
    }

    public void setOnArtLoadedListener(OnArtLoadedListener listener) {
        mOnArtLoadedListener = listener;
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

    private void loadArtImpl(final BoundEntity ent) {
        if (mTask != null) {
            mTask.cancel(true);
        }

        // We delay the loading slightly to make sure we don't uselessly load an image that is
        // being quickly flinged through (the requested entity will change in-between as the
        // view is recycled).
        mRequestedEntity = ent;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mRequestedEntity == ent) {
                    mTask = new BackgroundTask();

                    // On Android 4.2+, we use our custom executor. Android 4.1 and below uses the
                    // predefined pool, as the custom one causes the app to just crash without any
                    // kind of error message for no reason (at least in the emulator).
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        mTask.executeOnExecutor(ART_POOL_EXECUTOR, ent);
                    } else {
                        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, ent);
                    }
                }
            }
        }, DELAY_BEFORE_START);
    }
}
