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

    private Bitmap mBitmap;
    private int mCount;
    private Handler mHandler;
    private List<String> mStackAcquire;
    private List<String> mStackRelease;

    private Runnable mEvictRunnable = new Runnable() {
        @Override
        public void run() {
            mBitmap.recycle();
            mBitmap = null;
        }
    };

    public RefCountedBitmap(Bitmap bitmap) {
        mBitmap = bitmap;
        mCount = 0;
        mHandler = new Handler(Looper.getMainLooper());
        mStackAcquire = new ArrayList<String>();
        mStackRelease = new ArrayList<String>();
    }

    public void acquire() {
        if (mBitmap == null) {
            throw new IllegalStateException("Trying to acquire a null bitmap");
        }

        synchronized (mBitmap) {
            mCount++;
        }
        mHandler.removeCallbacks(mEvictRunnable);
        mStackAcquire.add(Arrays.toString(Thread.currentThread().getStackTrace()));
    }

    public void release() {
        if (mBitmap == null) {
            return;
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
            throw new IllegalStateException("Cannot get recycled bitmap");
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