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

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ref-counting wrapper for recycling bitmaps when not in use
 */
public class RefCountedBitmap {
    private static final String TAG = "RefCountedBitmap";
    private static final boolean DEBUG = false;

    private Bitmap mBitmap;
    private int mCount;
    private Handler mHandler;
    private List<String> mStackAcquire;
    private List<String> mStackRelease;

    private Runnable mEvictRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mBitmap) {
                if (mCount == 0) {
                    mBitmap.recycle();
                    mBitmap = null;
                } else {
                    if (DEBUG) Log.e(TAG, "Bitmap eviction cancelled as acquire count = " + mCount);
                }
            }
        }
    };

    public RefCountedBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap cannot be null! (This is a programming" +
                    " error, slap xplodwild now)");
        }

        mBitmap = bitmap;
        mCount = 0;
        mHandler = new Handler(Looper.getMainLooper());
        mStackAcquire = new ArrayList<String>();
        mStackRelease = new ArrayList<String>();
    }

    public boolean isRecycled() {
        return mBitmap == null || mBitmap.isRecycled();
    }

    public void acquire() {
        if (mBitmap == null || mBitmap.isRecycled()) {
            Log.e(TAG, "FATAL: Trying to acquire a null or recycled bitmap - dumping acquire/release stacks");
            dumpLocks();
            throw new IllegalStateException("Trying to acquire a null bitmap.");

        }

        synchronized (mBitmap) {
            mCount++;
            mHandler.removeCallbacks(mEvictRunnable);
        }
        mStackAcquire.add(Arrays.toString(Thread.currentThread().getStackTrace()));
    }

    public void release() {
        if (mBitmap == null) {
            Log.e(TAG, "FATAL: Trying to release a null or recycled bitmap - dumping acquire/release stacks");
            dumpLocks();
            throw new IllegalStateException("Trying to release acquire a null bitmap.");
        }

        synchronized (mBitmap) {
            mCount--;

            if (mCount == 0) {
                mHandler.removeCallbacks(mEvictRunnable);
                mHandler.postDelayed(mEvictRunnable, 1000);
            }
        }
        mStackRelease.add(Arrays.toString(Thread.currentThread().getStackTrace()));
    }

    public Bitmap get() {
        if (mBitmap == null) {
            Log.e(TAG, "FATAL: Trying to get a null or recycled bitmap - dumping acquire/release stacks");
            dumpLocks();
            throw new IllegalStateException("Trying to get a null bitmap.");
        }

        synchronized (mBitmap) {
            if (mCount == 0) {
                throw new IllegalStateException("Cannot get a bitmap with refcount 0");
            }
            return mBitmap;
        }
    }

    public void dumpLocks() {
        Log.e("======", "ACQUIRE LOCKS DUMP:");
        for (String s : mStackAcquire) {
            Log.e("=======>", s);
        }
        Log.e("======", "RELEASE LOCKS DUMP:");
        for (String s : mStackRelease) {
            Log.e("=======>", s);
        }
        Log.e("======", "===================");
    }
}