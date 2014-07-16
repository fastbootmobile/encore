package org.omnirom.music.app.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;

import org.omnirom.music.app.R;
import org.omnirom.music.framework.AlbumArtCache;
import org.omnirom.music.framework.ImageCache;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;

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
                    Log.e("XPLOD", "WILL RETRY, A QUERY IS RUNNING FOR IT");
                    output.retry = true;
                    return output;
                } else {
                    Log.e("XPLOD", "NO QUERY FOR THAT ALBUM");
                    artCache.notifyQueryRunning(mEntity);
                }

                // The image isn't loaded, if we don't have the artKey either, load both
                if (artKey == null) {
                    StringBuffer urlBuffer = new StringBuffer();
                    if (mEntity instanceof Album) {
                        artKey = AlbumArtCache.getDefault().getArtKey((Album) mEntity, urlBuffer);
                    } else if (mEntity instanceof Song) {
                        artKey = AlbumArtCache.getDefault().getArtKey((Song) mEntity, urlBuffer);
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
                setImageDrawable(result.drawable);

                if (mOnArtLoadedListener != null) {
                    mOnArtLoadedListener.onArtLoaded(AlbumArtImageView.this, result.drawable);
                }
            } else if (result != null && result.retry) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.e("XPLOD", "WE WILL REVISIT");
                        mTask = new BackgroundTask();
                        mTask.executeOnExecutor(THREAD_POOL_EXECUTOR, result.request);
                    }
                }, 100);
            }
        }
    }

    private Handler mHandler;
    private OnArtLoadedListener mOnArtLoadedListener;
    private BackgroundTask mTask;

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
        setImageResource(R.drawable.album_placeholder);
        mHandler = new Handler();
    }

    public void setOnArtLoadedListener(OnArtLoadedListener listener) {
        mOnArtLoadedListener = listener;
    }

    public void loadArtForSong(Song song) {
        if (mTask != null) {
            mTask.cancel(true);
        }
        mTask = new BackgroundTask();
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, song);
    }

    public void loadArtForAlbum(Album album) {
        if (mTask != null) {
            mTask.cancel(true);
        }
        mTask = new BackgroundTask();
        mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, album);
    }
}
