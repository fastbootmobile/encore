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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import com.fastbootmobile.encore.app.BuildConfig;
import com.fastbootmobile.encore.model.BoundEntity;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.IArtCallback;
import com.fastbootmobile.encore.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.List;

/**
 * Class creating a composite image for playlist cover art
 */
public class PlaylistArtBuilder {
    private static final String TAG = "PlaylistArtBuilder";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private Bitmap mPlaylistComposite;
    private final List<RecyclingBitmapDrawable> mPlaylistSource = new ArrayList<>();
    private Paint mPlaylistPaint;
    private List<AlbumArtTask> mCompositeTasks;
    private List<BoundEntity> mCompositeRequests;
    private int mNumComposite;
    private Handler mHandler;
    private Handler mMainHandler;
    private HandlerThread mHandlerThread;
    private IArtCallback mCallback;
    private boolean mDone;

    private Runnable mTimeoutWatchdog = new Runnable() {
        @Override
        public void run() {
            if (DEBUG) Log.w(TAG, "Watchdog kicking " + mPlaylistSource.size() + " images");

            if (mPlaylistComposite != null && mPlaylistSource.size() > 0) {
                try {
                    mCallback.onArtLoaded(mPlaylistComposite.copy(Bitmap.Config.ARGB_8888, false));
                } catch (RemoteException ignored) {
                }
            }
        }
    };

    private Runnable mUpdatePlaylistCompositeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mPlaylistComposite == null || !mPlaylistComposite.isRecycled() && !mDone) {
                makePlaylistComposite();
            }
        }
    };

    private AlbumArtHelper.AlbumArtListener mCompositeListener = new AlbumArtHelper.AlbumArtListener() {
        @Override
        public void onArtLoaded(RecyclingBitmapDrawable output, BoundEntity request) {
            if (!mCompositeRequests.contains(request) || mDone) {
                return;
            }

            if (output != null) {
                synchronized (mPlaylistSource) {
                    mPlaylistSource.add(output);
                    if (mPlaylistSource.size() < mNumComposite) {
                        mHandler.removeCallbacks(mUpdatePlaylistCompositeRunnable);
                        mHandler.postDelayed(mUpdatePlaylistCompositeRunnable, 200);
                    } else {
                        mHandler.removeCallbacks(mUpdatePlaylistCompositeRunnable);
                        mHandler.post(mUpdatePlaylistCompositeRunnable);
                    }
                }
            }
        }
    };

    public PlaylistArtBuilder() {
        mMainHandler = new Handler(Looper.getMainLooper());

        mHandlerThread = new HandlerThread("PlaylistArtBuilder");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public void freeMemory() {
        if (mPlaylistComposite != null) {
            mPlaylistComposite.recycle();
            mPlaylistComposite = null;
        }
        synchronized (mPlaylistSource) {
            mPlaylistSource.clear();
        }
        if (mCompositeTasks != null) {
            for (AlbumArtTask task : mCompositeTasks) {
                task.cancel(true);
            }
            mCompositeTasks.clear();
            mCompositeTasks = null;
        }
        mHandlerThread.interrupt();
    }


    private void makePlaylistComposite() {
        if (mPlaylistComposite == null) {
            mPlaylistComposite = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888);
        }

        if (mPlaylistPaint == null) {
            mPlaylistPaint = new Paint();
        }

        Canvas canvas = new Canvas(mPlaylistComposite);
        final int numImages = mPlaylistSource.size();
        final int compositeWidth = mPlaylistComposite.getWidth();
        final int compositeHeight = mPlaylistComposite.getHeight();

        if (numImages == 1) {
            // If we expected only one image, return this result
            if (mNumComposite == 1) {
                mDone = true;
                mHandler.removeCallbacks(mTimeoutWatchdog);
                try {
                    mCallback.onArtLoaded(mPlaylistSource.get(0).getBitmap());
                } catch (RemoteException ignored) {
                }
                return;
            }
        } else if (numImages == 2 || numImages == 3) {
            int i = 0;
            synchronized (mPlaylistSource) {
                for (RecyclingBitmapDrawable item : mPlaylistSource) {
                    Bitmap itemBmp = item.getBitmap();

                    Rect src = new Rect(0, 0, itemBmp.getWidth(), itemBmp.getHeight());
                    Rect dst = new Rect(i * compositeWidth / numImages,
                            0,
                            i * compositeWidth / numImages + compositeWidth,
                            compositeHeight);

                    canvas.drawBitmap(itemBmp, src, dst, mPlaylistPaint);
                    ++i;
                }
            }
        } else {
            for (int i = 0; i < 4; ++i) {
                if (mPlaylistSource.size() > i) {
                    RecyclingBitmapDrawable item = mPlaylistSource.get(i);
                    Bitmap itemBmp = item.getBitmap();

                    int row = (int) Math.floor(i / 2);
                    int col = (i % 2);

                    Rect src = new Rect(0, 0, itemBmp.getWidth(), itemBmp.getHeight());
                    Rect dst = new Rect(col * compositeWidth / 2,
                            row * compositeHeight / 2,
                            col * compositeWidth / 2 + compositeWidth / 2,
                            row * compositeHeight / 2 + compositeHeight / 2);

                    canvas.drawBitmap(itemBmp, src, dst, mPlaylistPaint);
                }
            }
        }

        if (DEBUG) Log.d(TAG, "Got image " + numImages + "/" + mNumComposite);

        if (numImages == mNumComposite) {
            mDone = true;
            mHandler.removeCallbacks(mTimeoutWatchdog);
            try {
                mCallback.onArtLoaded(mPlaylistComposite.copy(Bitmap.Config.ARGB_8888, false));
            } catch (RemoteException ignored) {
            }
        }
    }

    public void start(Resources res, Playlist playlist, IArtCallback callback) {
        if (DEBUG) Log.d(TAG, "Starting to build playlist art for " + playlist.getName());

        if (playlist.getSongsCount() == 0) {
            Log.d(TAG, "Playlist " + playlist.getName() + " has no tracks, skipping art building");
            mDone = true;
            try {
                callback.onArtLoaded(null);
            } catch (RemoteException ignore) {
            }
            return;
        }

        mDone = false;
        mCallback = callback;

        if (mCompositeTasks == null) {
            mCompositeTasks = new ArrayList<>();
        } else {
            // Cancel the current tasks
            for (AlbumArtTask task : mCompositeTasks) {
                task.cancel(true);
            }
            mCompositeTasks.clear();
        }

        // Load 4 songs if possible and compose them into one picture
        mPlaylistSource.clear();
        mCompositeRequests = new ArrayList<>();
        mNumComposite = Math.min(4, playlist.getSongsCount());
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        for (int i = 0; i < mNumComposite; ++i) {
            String entry = playlist.songsList().get(i);
            Song song = aggregator.retrieveSong(entry, playlist.getProvider());

            mCompositeRequests.add(song);
            mCompositeTasks.add(AlbumArtHelper.retrieveAlbumArt(res, mCompositeListener, song, 600, false));
        }

        mHandler.postDelayed(mTimeoutWatchdog, 8000);
    }
}
