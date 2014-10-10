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

package org.omnirom.music.framework;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.util.Log;

import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IArtCallback;
import org.omnirom.music.providers.IMusicProvider;
import org.omnirom.music.providers.ProviderAggregator;
import org.omnirom.music.providers.ProviderCache;
import org.omnirom.music.providers.ProviderConnection;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class allowing to easily download and fetch an album art or artist art
 */
public class AlbumArtHelper {
    private static final String TAG = "AlbumArtHelper";

    private static final int DELAY_BEFORE_RETRY = 60;
    private static final int CORE_POOL_SIZE = 4;
    private static final int MAXIMUM_POOL_SIZE = 256;
    private static final int KEEP_ALIVE = 5;
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "Art AsyncTask #" + mCount.getAndIncrement());
        }
    };
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(10);
    private static final Executor ART_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);


    public interface AlbumArtListener {
        public void onArtLoaded(Bitmap output, BoundEntity request);
    }

    public static class BackgroundResult {
        public BoundEntity request;
        public Bitmap bitmap;
        public boolean retry;
    }

    /**
     * AsyncTask downloading the album art
     */
    public static class AlbumArtTask extends AsyncTask<BoundEntity, Void, BackgroundResult> {
        private BoundEntity mEntity;
        private Context mContext;
        private AlbumArtListener mListener;
        private Handler mHandler;
        private IArtCallback mProviderArtCallback;

        private AlbumArtTask(Context ctx, AlbumArtListener listener) {
            mContext = ctx;
            mListener = listener;
            mHandler = new Handler();
            mProviderArtCallback = new IArtCallback.Stub() {
                @Override
                public void onArtLoaded(final Bitmap bitmap) throws RemoteException {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onArtLoaded(bitmap, mEntity);
                        }
                    });
                }
            };
        }

        @Override
        protected BackgroundResult doInBackground(BoundEntity... params) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            BackgroundResult output = new BackgroundResult();
            mEntity = params[0];
            output.request = mEntity;

            if (mEntity == null) {
                return null;
            }

            final ProviderCache cache = ProviderAggregator.getDefault().getCache();

            Bitmap bmp = null;

            // First, we try to get it from the provider
            boolean provided = false;
            ProviderConnection connection = PluginsLookup.getDefault().getProvider(mEntity.getProvider());
            try {
                if (connection != null) {
                    IMusicProvider provider = connection.getBinder();
                    if (provider != null) {
                        if (mEntity instanceof Album) {
                            provided = provider.getAlbumArt((Album) mEntity, mProviderArtCallback);
                        } else if (mEntity instanceof Song) {
                            provided = provider.getSongArt((Song) mEntity, mProviderArtCallback);
                        } else if (mEntity instanceof Artist) {
                            provided = provider.getArtistArt((Artist) mEntity, mProviderArtCallback);
                        } else if (mEntity instanceof Playlist) {
                            provided = provider.getPlaylistArt((Playlist) mEntity, mProviderArtCallback);
                        }
                    }
                }
            } catch (RemoteException e) {
                // Fallback to MB
                Log.e(TAG, "Remote exception while looking up art from provider", e);
            }

            // If the provider couldn't provide an image, get it from MusicBrainz
            if (!provided) {
                Bitmap cachedImage = null;
                String artKey;

                // Load the art key based on what we need
                if (mEntity instanceof Album) {
                    artKey = cache.getAlbumArtKey((Album) mEntity);
                } else if (mEntity instanceof Song) {
                    artKey = cache.getSongArtKey((Song) mEntity);
                } else if (mEntity instanceof Artist) {
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
                            artKey = AlbumArtCache.getDefault().getArtKey((Artist) mEntity, urlBuffer);
                        }

                        artUrl = urlBuffer.toString();
                    }

                    // We now have the art key, download the actual image if it's not the default art
                    if (artKey != null && !artKey.equals(AlbumArtCache.DEFAULT_ART)) {
                        bmp = AlbumArtCache.getOrDownloadArt(artKey, artUrl, null);
                    }
                }

                // We now have a bitmap to display, so let's put it!
                output.bitmap = bmp;
                output.retry = false;

                // Cache the image
                if (mEntity instanceof Album) {
                    cache.putAlbumArtKey((Album) mEntity, artKey);
                } else if (mEntity instanceof Song) {
                    cache.putSongArtKey((Song) mEntity, artKey);
                } else if (mEntity instanceof Artist) {
                    cache.putArtistArtKey((Artist) mEntity, artKey);
                }

                // In all cases, we tell that this entity is loaded
                artCache.notifyQueryStopped(mEntity);
            }

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

            if (result != null && result.retry) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        AlbumArtTask task = new AlbumArtTask(mContext, mListener);
                        try {
                            task.executeOnExecutor(ART_POOL_EXECUTOR, result.request);
                        } catch (RejectedExecutionException e) {
                            Log.w(TAG, "Request restart has been denied", e);
                        }
                    }
                }, DELAY_BEFORE_RETRY);
            } else if (result != null) {
                mListener.onArtLoaded(result.bitmap, result.request);
            }
        }
    }

    public static AlbumArtTask retrieveAlbumArt(Context ctx, AlbumArtListener listener,
                                                BoundEntity request) {
        AlbumArtTask task = new AlbumArtTask(ctx, listener);

        // On Android 4.2+, we use our custom executor. Android 4.1 and below uses the predefined
        // pool, as the custom one causes the app to just crash without any kind of error message
        // for no reason (at least in the emulator).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            task.executeOnExecutor(ART_POOL_EXECUTOR, request);
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, request);
        }

        return task;
    }
}
