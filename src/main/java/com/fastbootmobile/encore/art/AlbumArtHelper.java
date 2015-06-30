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
import android.os.Build;
import android.support.annotation.NonNull;

import com.fastbootmobile.encore.model.BoundEntity;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper class allowing to easily download and fetch an album art or artist art
 */
public class AlbumArtHelper {
    private static final String TAG = "AlbumArtHelper";

    private static final int CORE_POOL_SIZE = 2;
    private static final int PRI_CORE_POOL_SIZE = 2;
    private static final int MAXIMUM_POOL_SIZE = 6;
    private static final int PRI_MAXIMUM_POOL_SIZE = 3;
    private static final int KEEP_ALIVE = 10;
    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "Art AsyncTask #" + mCount.getAndIncrement());
        }
    };
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>(64);
    private static final BlockingQueue<Runnable> sPriorityPoolWorkQueue =
            new LinkedBlockingQueue<>(32);

    static final Executor ART_POOL_EXECUTOR
            = new ThreadPoolExecutor(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPoolWorkQueue, sThreadFactory);
    static final Executor PRIORITY_ART_POOL_EXECUTOR
            = new ThreadPoolExecutor(PRI_CORE_POOL_SIZE, PRI_MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, sPriorityPoolWorkQueue, sThreadFactory);

    public interface AlbumArtListener {
        void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request);
    }

    public static class BackgroundResult {
        public BoundEntity request;
        public RecyclingBitmapDrawable bitmap;
        public boolean retry;
        public AlbumArtListener listener;
        public int size;
    }

    public static AlbumArtTask retrieveAlbumArt(Resources res, AlbumArtListener listener,
                                                BoundEntity request, int size, boolean immediate) {
        AlbumArtRequest requestStructure = new AlbumArtRequest();
        requestStructure.entity = request;
        requestStructure.listener = listener;
        requestStructure.requestedSize = size;
        requestStructure.res = res;
        AlbumArtTask task = new AlbumArtTask();

        // On Android 4.2+, we use our custom executor. Android 4.1 and below uses the predefined
        // pool, as the custom one causes the app to just crash without any kind of error message
        // for no reason (at least in the emulator).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (immediate) {
                if (sPriorityPoolWorkQueue.remainingCapacity() == 0) {
                    // Release a previous work to free up space in the queue
                    sPriorityPoolWorkQueue.remove(sPriorityPoolWorkQueue.iterator().next());
                }
                task.executeOnExecutor(PRIORITY_ART_POOL_EXECUTOR, requestStructure);
            } else {
                if (sPoolWorkQueue.remainingCapacity() == 0) {
                    // Release a previous work to free up space in the queue
                    sPoolWorkQueue.remove(sPoolWorkQueue.iterator().next());
                }
                task.executeOnExecutor(ART_POOL_EXECUTOR, requestStructure);
            }
        } else {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, requestStructure);
        }

        return task;
    }

    public static void clearAlbumArtRequests() {
        sPriorityPoolWorkQueue.clear();
        sPoolWorkQueue.clear();
    }


    static class AlbumArtRequest {
        Resources res;
        AlbumArtHelper.AlbumArtListener listener;
        int requestedSize;
        BoundEntity entity;
    }
}
