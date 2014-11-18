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

package org.omnirom.music.service;

import android.content.Intent;
import android.graphics.Bitmap;
import android.media.session.PlaybackState;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.echonest.api.v4.EchoNestException;

import org.omnirom.music.api.echonest.AutoMixBucket;
import org.omnirom.music.api.echonest.AutoMixManager;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Artist;
import org.omnirom.music.model.Song;
import org.omnirom.music.providers.ProviderAggregator;

/**
 * Class handling the lockscreen/remote metadata system
 */
class RemoteMetadataManager extends MediaSessionCompat.Callback {
    private static final String TAG = "RemoteMetadataManager";
    private static final String SESSION_NAME = "OmniMusic";

    private PlaybackService mService;
    private MediaSessionCompat mMediaSession;
    private Bitmap mPreviousAlbumArt;

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

    RemoteMetadataManager(PlaybackService service) {
        mService = service;
    }

    void setup() {
        mMediaSession = new MediaSessionCompat(mService, SESSION_NAME);
        mMediaSession.setCallback(this);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    void release() {
        mMediaSession.release();
        mMediaSession = null;
    }

    void setActive(final boolean active) {
        mMediaSession.setActive(active);
    }

    void setAlbumArt(final Bitmap bmp) {
        if (mPreviousAlbumArt != null) {
            mPreviousAlbumArt.recycle();
            mPreviousAlbumArt = null;
        }

        if (bmp != null) {
            mPreviousAlbumArt = bmp.copy(bmp.getConfig(), false);

            MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder()
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, mPreviousAlbumArt);
            mMediaSession.setMetadata(builder.build());
        }
    }

    void setCurrentSong(final Song song) {
        final ProviderAggregator aggregator = ProviderAggregator.getDefault();
        final Artist artist = aggregator.retrieveArtist(song.getArtist(), song.getProvider());
        final Album album = aggregator.retrieveAlbum(song.getAlbum(), song.getProvider());

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        if (artist != null) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist.getName());
        }
        if (album != null) {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album.getName());
            builder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, album.getSongsCount());
        }
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle());
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.getDuration());

        mMediaSession.setMetadata(builder.build());
    }

    void notifyPlaying(final long timeElapsed) {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(PlaybackStateCompat.STATE_PLAYING, timeElapsed, 1.0f);
        mMediaSession.setPlaybackState(builder.build());
        mMediaSession.setActive(true);
    }

    void notifyBuffering() {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(PlaybackStateCompat.STATE_BUFFERING, 0, 1.0f);
        mMediaSession.setPlaybackState(builder.build());
        mMediaSession.setActive(true);
    }

    void notifyPaused(final long timeElapsed) {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(PlaybackStateCompat.STATE_PAUSED, timeElapsed, 1.0f);
        mMediaSession.setPlaybackState(builder.build());
        mMediaSession.setActive(true);
    }

    void notifyStopped() {
        PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
        builder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 1.0f);
        mMediaSession.setPlaybackState(builder.build());
        mMediaSession.setActive(false);
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
    public void onSetRating(RatingCompat rating) {
        if (rating.isThumbUp() || rating.hasHeart()) {
            new Thread(mThumbsUpRunnable).start();
        }
    }
}
