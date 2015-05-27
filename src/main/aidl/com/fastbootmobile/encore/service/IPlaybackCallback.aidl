package com.fastbootmobile.encore.service;

import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Artist;

interface IPlaybackCallback {

    /**
     * Returns this callback's identifier
     */
    int getIdentifier();

    /**
     * Notifies the app that playback of a song started
     */
    void onSongStarted(boolean buffering, in Song s);

    /**
     * Notifies the playback position manually changed (scrobbling/seeking)
     */
    void onSongScrobble(int timeMs);

    /**
     * Notifies the playback paused
     */
    void onPlaybackPause();

    /**
     * Notifies the playback resumed
     */
    void onPlaybackResume();

    /**
     * Notifies the playback queue changed. Note that we don't call this when the first item of
     * the queue is removed because of playback moving on to the next track of the queue, but only
     * when a manual/non-logical operation is done.
     */
    void onPlaybackQueueChanged();

}