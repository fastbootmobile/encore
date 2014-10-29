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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import org.omnirom.music.model.BoundEntity;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.IArtCallback;
import org.omnirom.music.providers.ProviderAggregator;

import java.util.ArrayList;
import java.util.List;

/**
 * Class creating a composite image for playlist cover art
 */
public class PlaylistArtBuilder {
    private Bitmap mPlaylistComposite;
    private List<RefCountedBitmap> mPlaylistSource;
    private Paint mPlaylistPaint;
    private List<AlbumArtHelper.AlbumArtTask> mCompositeTasks;
    private List<BoundEntity> mCompositeRequests;
    private int mNumComposite;
    private Handler mHandler;
    private IArtCallback mCallback;
    private boolean mDone;

    private Runnable mTimeoutWatchdog = new Runnable() {
        @Override
        public void run() {
            Log.e("PlaylistArtBUilder", "Watchdog kicking " + mNumComposite + " images");
            if (mPlaylistComposite != null) {
                try {
                    mCallback.onArtLoaded(mPlaylistComposite.copy(Bitmap.Config.ARGB_8888, false));
                } catch (RemoteException ignored) {
                }
            }

            mDone = true;
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
        public void onArtLoaded(RefCountedBitmap output, BoundEntity request) {
            if (!mCompositeRequests.contains(request) || mPlaylistSource == null || mDone) {
                return;
            }

            if (output != null) {
                output.acquire();
                mPlaylistSource.add(output);
                if (mPlaylistSource.size() < 4) {
                    mHandler.removeCallbacks(mUpdatePlaylistCompositeRunnable);
                    mHandler.postDelayed(mUpdatePlaylistCompositeRunnable, 200);
                } else {
                    mHandler.removeCallbacks(mUpdatePlaylistCompositeRunnable);
                    mHandler.post(mUpdatePlaylistCompositeRunnable);
                }
            }
        }
    };

    public PlaylistArtBuilder() {
        mHandler = new Handler(Looper.getMainLooper());
    }

    public void freeMemory() {
        if (mPlaylistComposite != null) {
            mPlaylistComposite.recycle();
            mPlaylistComposite = null;
        }
        if (mPlaylistSource != null) {
            for (RefCountedBitmap bmp : mPlaylistSource) {
                bmp.release();
            }
            mPlaylistSource.clear();
            mPlaylistSource = null;
        }
        if (mCompositeTasks != null) {
            for (AlbumArtHelper.AlbumArtTask task : mCompositeTasks) {
                task.cancel(true);
            }
            mCompositeTasks.clear();
            mCompositeTasks = null;
        }
    }


    private synchronized void makePlaylistComposite() {
        if (mPlaylistComposite == null) {
            mPlaylistComposite = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888);
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
                    mCallback.onArtLoaded(mPlaylistSource.get(0).get());
                } catch (RemoteException ignored) {
                }
                return;
            }
        } else if (numImages == 2 || numImages == 3) {
            int i = 0;
            for (RefCountedBitmap item : mPlaylistSource) {
                item.acquire();
                Bitmap itemBmp = item.get();

                Rect src = new Rect(0, 0, itemBmp.getWidth(), itemBmp.getHeight());
                Rect dst = new Rect(i * compositeWidth / numImages,
                        0,
                        i * compositeWidth / numImages + compositeWidth,
                        compositeHeight);

                canvas.drawBitmap(itemBmp, src, dst, mPlaylistPaint);
                item.release();
                ++i;
            }
        } else {
            for (int i = 0; i < 4; ++i) {
                RefCountedBitmap item = mPlaylistSource.get(i);
                item.acquire();
                Bitmap itemBmp = item.get();

                int row = (int) Math.floor(i / 2);
                int col = (i % 2);

                Rect src = new Rect(0, 0, itemBmp.getWidth(), itemBmp.getHeight());
                Rect dst = new Rect(col * compositeWidth / 2,
                        row * compositeHeight / 2,
                        col * compositeWidth / 2 + compositeWidth / 2,
                        row * compositeHeight / 2 + compositeHeight / 2);

                canvas.drawBitmap(itemBmp, src, dst, mPlaylistPaint);
                item.release();
            }
        }

        Log.e("PlaylistArt", "Got image " + numImages + "/" + mNumComposite);

        if (numImages == mNumComposite) {
            mDone = true;
            mHandler.removeCallbacks(mTimeoutWatchdog);
            try {
                mCallback.onArtLoaded(mPlaylistComposite.copy(Bitmap.Config.ARGB_8888, false));
            } catch (RemoteException ignored) {
            }
        }
    }

    public void start(Playlist playlist, IArtCallback callback) {
        mDone = false;
        mCallback = callback;

        if (mCompositeTasks == null) {
            mCompositeTasks = new ArrayList<AlbumArtHelper.AlbumArtTask>();
        } else {
            // Cancel the current tasks
            for (AlbumArtHelper.AlbumArtTask task : mCompositeTasks) {
                task.cancel(true);
            }
            mCompositeTasks.clear();
        }

        if (mPlaylistSource != null) {
            for (RefCountedBitmap bmp : mPlaylistSource) {
                bmp.release();
            }
        }

        // Load 4 songs if possible and compose them into one picture
        mPlaylistSource = new ArrayList<RefCountedBitmap>();
        mCompositeRequests = new ArrayList<BoundEntity>();
        mNumComposite = Math.min(4, playlist.getSongsCount());
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();

        for (int i = 0; i < mNumComposite; ++i) {
            String entry = playlist.songsList().get(i);
            Song song = aggregator.retrieveSong(entry, playlist.getProvider());
            mCompositeTasks.add(AlbumArtHelper.retrieveAlbumArt( mCompositeListener, song, false));
            mCompositeRequests.add(song);
        }

        mHandler.postDelayed(mTimeoutWatchdog, 5000);
    }
}
