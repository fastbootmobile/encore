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

package com.fastbootmobile.encore.art;

import android.content.res.Resources;
import android.os.*;
import android.os.Process;
import android.util.Log;

import com.fastbootmobile.encore.model.BoundEntity;

import java.util.concurrent.RejectedExecutionException;

/**
 * AsyncTask downloading the album art
 */
public class AlbumArtTask extends AsyncTask<BoundEntity, Void, AlbumArtHelper.BackgroundResult> {
    private static final String TAG = "AlbumArtTask";

    private static final int DELAY_BEFORE_RETRY = 500;
    private static final Object sPauseWorkLock = new Object();
    private static boolean sPauseWork = false;

    private BoundEntity mEntity;
    private AlbumArtHelper.AlbumArtListener mListener;
    private Handler mHandler;
    private RecyclingBitmapDrawable mArtBitmap;
    private Resources mResources;
    private int mRequestedSize;
    private AlbumArtCache.IAlbumArtCacheListener mCacheListener = new AlbumArtCache.IAlbumArtCacheListener() {
        @Override
        public void onArtLoaded(BoundEntity ent, RecyclingBitmapDrawable result) {

            synchronized (AlbumArtTask.this) {
                if (!isCancelled()) {
                    mArtBitmap = result;
                }
                AlbumArtTask.this.notify();
            }
        }
    };

    AlbumArtTask(Resources res, AlbumArtHelper.AlbumArtListener listener, int requestedSize) {
        mListener = listener;
        mHandler = new Handler(Looper.getMainLooper());
        mResources = res;
        mRequestedSize = requestedSize;
    }

    @Override
    protected AlbumArtHelper.BackgroundResult doInBackground(BoundEntity... params) {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
        mEntity = params[0];

        // Wait here if work is paused and the task is not cancelled
        synchronized (sPauseWorkLock) {
            while (sPauseWork && !isCancelled()) {
                try {
                    sPauseWorkLock.wait();
                } catch (InterruptedException ignored) {}
            }
        }

        if (mEntity == null || isCancelled()) {
            return null;
        }

        AlbumArtHelper.BackgroundResult output = new AlbumArtHelper.BackgroundResult();
        output.request = mEntity;

        final AlbumArtCache artCache = AlbumArtCache.getDefault();

        if (artCache.isQueryRunning(mEntity)) {
            output.retry = true;
        } else {
            // Notify other potential tasks we're processing this entity
            artCache.notifyQueryRunning(mEntity);

            // Get from the cache
            synchronized (this) {
                if (AlbumArtCache.getDefault().getArt(mResources, mEntity,
                        mRequestedSize,mCacheListener)) {
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
        mListener = null;
        final AlbumArtCache artCache = AlbumArtCache.getDefault();
        artCache.notifyQueryStopped(mEntity);
    }

    @Override
    protected void onPostExecute(final AlbumArtHelper.BackgroundResult result) {
        super.onPostExecute(result);

        if (!isCancelled() && mListener != null) {
            if (result != null && result.retry) {
                final AlbumArtTask task = new AlbumArtTask(mResources, mListener, mRequestedSize);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            task.executeOnExecutor(AlbumArtHelper.ART_POOL_EXECUTOR, result.request);
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


    public static void setPauseWork(boolean pause) {
        synchronized (sPauseWorkLock) {
            sPauseWork = pause;
            if (!sPauseWork) {
                sPauseWorkLock.notifyAll();
            }
        }
    }
}
