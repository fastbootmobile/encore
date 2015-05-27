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

package com.fastbootmobile.encore.service;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;

import com.fastbootmobile.encore.api.echonest.AutoMixBucket;
import com.fastbootmobile.encore.api.echonest.AutoMixManager;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Artist;
import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.providers.ProviderAggregator;
import com.fastbootmobile.encore.receivers.RemoteControlReceiver;
import com.fastbootmobile.encore.utils.AvrcpUtils;

/**
 * Class handling the lockscreen/remote metadata system on Lollipop and above
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class RemoteMetadataManagerv21 extends MediaSession.Callback implements IRemoteMetadataManager {
    private static final String TAG = "RemoteMetadataManager";
    private static final String SESSION_NAME = "OmniMusic";

    private PlaybackService mService;
    private MediaSession mMediaSession;
    private MediaMetadata.Builder mBuilder;
    private PlaybackState.Builder mStateBuilder;
    private BitmapDrawable mPreviousAlbumArt;

    private Runnable mThumbsUpRunnable = new Runnable() {
        @Override
        public void run() {
            AutoMixBucket bucket = AutoMixManager.getDefault().getCurrentPlayingBucket();
            if (bucket != null) {
                try {
                    bucket.notifyLike();
                } catch (EchoNestException e) {
                    Log.e(TAG, "Cannot notify of like event");
                }
            }
        }
    };

    RemoteMetadataManagerv21(PlaybackService service) {
        mService = service;
    }

    @Override
    public void setup() {
        mMediaSession = new MediaSession(mService, SESSION_NAME);
        mMediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
        mMediaSession.setCallback(this);

        PendingIntent receiverIntent = PendingIntent.getService(mService, 0,
                new Intent(mService, RemoteControlReceiver.class), 0);
        mMediaSession.setMediaButtonReceiver(receiverIntent);

        mBuilder = new MediaMetadata.Builder();
        mStateBuilder = new PlaybackState.Builder();
        mStateBuilder.setActions(getActionsFlags(false));
    }

    private long getActionsFlags(boolean hasNext) {
        long actions = 0;
        actions |= PlaybackState.ACTION_PLAY_PAUSE;
        actions |= PlaybackState.ACTION_SET_RATING;
        actions |= PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (hasNext) {
            actions |= PlaybackState.ACTION_SKIP_TO_NEXT;
        }

        return actions;
    }

    @Override
    public void release() {
        mMediaSession.release();
        mMediaSession = null;
    }

    @Override
    public void setActive(final boolean active) {
        mMediaSession.setActive(active);
    }

    @Override
    public void setAlbumArt(final BitmapDrawable bmp) {
        if (mPreviousAlbumArt != bmp) {
            if (bmp != null) {
                mPreviousAlbumArt = bmp;

                mBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, mPreviousAlbumArt.getBitmap());
                if (mMediaSession != null) {
                    mMediaSession.setMetadata(mBuilder.build());
                }
            } else {
                mBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, null);
            }
        }
    }

    @Override
    public void setCurrentSong(final Song song, final boolean hasNext) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
        final Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());

        if (artist != null) {
            mBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, artist.getName());
        }
        if (album != null) {
            mBuilder.putString(MediaMetadata.METADATA_KEY_ALBUM, album.getName());
            mBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, album.getSongsCount());
        }
        mBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, song.getTitle());
        mBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, song.getDuration());

        mStateBuilder.setActions(getActionsFlags(hasNext));

        if (mMediaSession != null) {
            mMediaSession.setMetadata(mBuilder.build());
            mMediaSession.setPlaybackState(mStateBuilder.build());
        }

        AvrcpUtils.notifyMetaChanged(mService, song.getRef().hashCode(),
                artist != null ? artist.getName() : null,
                album != null ? album.getName() : null,
                song.getTitle(), mService.getQueue().size(),
                song.getDuration(), mService.getCurrentTrackPositionImpl());
    }

    @Override
    public void notifyPlaying(final long timeElapsed) {
        mStateBuilder.setState(PlaybackState.STATE_PLAYING, timeElapsed, 1.0f);

        if (mMediaSession != null) {
            mMediaSession.setPlaybackState(mStateBuilder.build());
            mMediaSession.setActive(true);
        }

        AvrcpUtils.notifyPlayStateChanged(mService, true, timeElapsed);
    }

    @Override
    public void notifyBuffering() {
        mStateBuilder.setState(PlaybackState.STATE_BUFFERING, 0, 1.0f);

        if (mMediaSession != null) {
            mMediaSession.setPlaybackState(mStateBuilder.build());
            mMediaSession.setActive(true);
        }

        AvrcpUtils.notifyPlayStateChanged(mService, true, 0);
    }

    @Override
    public void notifyPaused(final long timeElapsed) {
        mStateBuilder.setState(PlaybackState.STATE_PAUSED, timeElapsed, 1.0f);

        if (mMediaSession != null) {
            mMediaSession.setPlaybackState(mStateBuilder.build());
            mMediaSession.setActive(true);
        }

        AvrcpUtils.notifyPlayStateChanged(mService, true, 0);
    }

    @Override
    public void notifyStopped() {
        mStateBuilder.setState(PlaybackState.STATE_STOPPED, 0, 1.0f);

        if (mMediaSession != null) {
            mMediaSession.setPlaybackState(mStateBuilder.build());
            mMediaSession.setActive(false);
        }

        AvrcpUtils.notifyPlayStateChanged(mService, false, 0);
    }

    @Override
    public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
        // Buttons actions can be overridden
        return super.onMediaButtonEvent(mediaButtonIntent);
    }

    @Override
    public void onPlay() {
        mService.playImpl();
    }

    @Override
    public void onPause() {
        mService.pauseImpl();
    }

    @Override
    public void onSkipToNext() {
        mService.nextImpl();
    }

    @Override
    public void onSkipToPrevious() {
        mService.previousImpl();
    }

    @Override
    public void onFastForward() {
        mService.seekImpl(mService.getCurrentTrackPositionImpl() + 5000);
    }

    @Override
    public void onRewind() {
        mService.seekImpl(mService.getCurrentTrackPositionImpl() - 5000);
    }

    @Override
    public void onStop() {
        mService.stopImpl();
    }

    @Override
    public void onSeekTo(long pos) {
        mService.seekImpl(pos);
    }

    @Override
    public void onSetRating(Rating rating) {
        if (rating.isThumbUp() || rating.hasHeart()) {
            new Thread(mThumbsUpRunnable).start();
        }
    }
}
