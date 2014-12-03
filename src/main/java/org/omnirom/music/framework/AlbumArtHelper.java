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

import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.support.annotation.NonNull;
import android.util.Log;

import org.omnirom.music.model.BoundEntity;

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

    private static final int DELAY_BEFORE_RETRY = 500;
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
            new LinkedBlockingQueue<>(10);
    private static final Executor ART_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);


    public interface AlbumArtListener {
        public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request);
    }

    public static class BackgroundResult {
        public BoundEntity request;
        public RecyclingBitmapDrawable bitmap;
        public boolean retry;
    }

    /**
     * AsyncTask downloading the album art
     */
    public static class AlbumArtTask extends AsyncTask<BoundEntity, Void, BackgroundResult> {
        private BoundEntity mEntity;
        private AlbumArtListener mListener;
        private Handler mHandler;
        private RecyclingBitmapDrawable mArtBitmap;
        private Resources mResources;
        private AlbumArtCache.IAlbumArtCacheListener mCacheListener = new AlbumArtCache.IAlbumArtCacheListener() {
            @Override
            public void onArtLoaded(BoundEntity ent, RecyclingBitmapDrawable result) {
                synchronized (AlbumArtTask.this) {
                    if (!isCancelled()) {
                        mArtBitmap = result;
                    }
                    AlbumArtTask.this.notifyAll();
                }
            }
        };

        private AlbumArtTask(Resources res, AlbumArtListener listener) {
            mListener = listener;
            mHandler = new Handler(Looper.getMainLooper());
            mResources = res;
        }

        @Override
        protected BackgroundResult doInBackground(BoundEntity... params) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            mEntity = params[0];

            if (mEntity == null || isCancelled()) {
                return null;
            }

            BackgroundResult output = new BackgroundResult();
            output.request = mEntity;

            final AlbumArtCache artCache = AlbumArtCache.getDefault();

            if (artCache.isQueryRunning(mEntity)) {
                output.retry = true;
            } else {
                // Notify other potential tasks we're processing this entity
                artCache.notifyQueryRunning(mEntity);

                // Get from the cache
                synchronized (this) {
                    if (AlbumArtCache.getDefault().getArt(mResources, mEntity, mCacheListener)) {
                        // Wait for the result
                        if (mArtBitmap == null) {
                            try {
                                wait(6000);
                            } catch (InterruptedException e) {
                                // Interrupted, cancel
                                artCache.notifyQueryStopped(mEntity);
                                return null;
                            }
                        }
                    }
                }

                // In all cases, we tell that this entity is loaded
                artCache.notifyQueryStopped(mEntity);

                // We now have a bitmap to display, so let's put it!
                if (mArtBitmap != null) {
                    output.bitmap = mArtBitmap;
                    output.retry = false;
                } else {
                    output.bitmap = null;
                    output.retry = false;
                }
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

            if (!isCancelled()) {
                if (result != null && result.retry) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            AlbumArtTask task = new AlbumArtTask(mResources, mListener);
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
    }

    public static AlbumArtTask retrieveAlbumArt(Resources res, AlbumArtListener listener,
                                                BoundEntity request, boolean immediate) {
        AlbumArtTask task = new AlbumArtTask(res, listener);

        if (!immediate) {
            // On Android 4.2+, we use our custom executor. Android 4.1 and below uses the predefined
            // pool, as the custom one causes the app to just crash without any kind of error message
            // for no reason (at least in the emulator).
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                task.executeOnExecutor(ART_POOL_EXECUTOR, request);
            } else {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, request);
            }
        } else {
            task.execute(request);
        }

        return task;
    }
}
