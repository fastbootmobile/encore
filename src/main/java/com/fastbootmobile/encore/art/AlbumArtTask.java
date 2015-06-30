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
import android.os.AsyncTask;
import android.os.Process;
import android.util.Log;

import com.fastbootmobile.encore.model.BoundEntity;

import java.util.concurrent.RejectedExecutionException;

/**
 * AsyncTask downloading the album art
 */
public class AlbumArtTask extends AsyncTask<AlbumArtHelper.AlbumArtRequest, Void, AlbumArtHelper.BackgroundResult> {
    private static final String TAG = "AlbumArtTask";

    private static final int DELAY_BEFORE_RETRY = 500;
    private static final Object sPauseWorkLock = new Object();
    private static boolean sPauseWork = false;

    private RecyclingBitmapDrawable mArtBitmap;
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

    @Override
    protected AlbumArtHelper.BackgroundResult doInBackground(AlbumArtHelper.AlbumArtRequest... params) {
        android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
        AlbumArtHelper.AlbumArtRequest request = params[0];

        // Wait here if work is paused and the task is not cancelled
        synchronized (sPauseWorkLock) {
            while (sPauseWork && !isCancelled()) {
                try {
                    sPauseWorkLock.wait();
                } catch (InterruptedException ignored) {}
            }
        }

        if (request.entity == null || isCancelled()) {
            return null;
        }

        AlbumArtHelper.BackgroundResult output = new AlbumArtHelper.BackgroundResult();
        output.request = request.entity;
        output.listener = request.listener;
        output.size = request.requestedSize;

        final AlbumArtCache artCache = AlbumArtCache.getDefault();

        if (artCache.isQueryRunning(request.entity)) {
            output.retry = true;
        } else {
            // Notify other potential tasks we're processing this entity
            artCache.notifyQueryRunning(request.entity);

            // Get from the cache
            synchronized (this) {
                if (AlbumArtCache.getDefault().getArt(request.res, request.entity,
                        request.requestedSize, mCacheListener)) {
                    // Wait for the result
                    if (mArtBitmap == null) {
                        try {
                            wait(6000);
                        } catch (InterruptedException e) {
                            // Interrupted, cancel
                            cancel(true);
                            return output;
                        }
                    }
                }
            }

            // In all cases, we tell that this entity is loaded
            artCache.notifyQueryStopped(request.entity);

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
    protected void onCancelled(AlbumArtHelper.BackgroundResult result) {
        final AlbumArtCache artCache = AlbumArtCache.getDefault();
        if (result != null) {
            artCache.notifyQueryStopped(result.request);

            if (result.listener != null) {
                result.listener.onArtLoaded(null, result.request);
            }
        }
    }

    @Override
    protected void onPostExecute(final AlbumArtHelper.BackgroundResult result) {
        super.onPostExecute(result);

        if (!isCancelled() && result != null && result.listener != null) {
            if (result.retry) {
                // We retry to get it in a bit
                final AlbumArtTask task = new AlbumArtTask();
                AlbumArtHelper.AlbumArtRequest request = new AlbumArtHelper.AlbumArtRequest();
                request.listener = result.listener;
                request.entity = result.request;
                request.requestedSize = result.size;
                request.res = Resources.getSystem();

                try {
                    task.executeOnExecutor(AlbumArtHelper.ART_POOL_EXECUTOR, request);
                } catch (RejectedExecutionException e) {
                    Log.w(TAG, "Request restart has been denied", e);
                }
            } else {
                result.listener.onArtLoaded(result.bitmap, result.request);
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
