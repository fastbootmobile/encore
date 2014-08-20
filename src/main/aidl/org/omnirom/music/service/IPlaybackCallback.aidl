package org.omnirom.music.service;

import org.omnirom.music.model.Song;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Artist;

interface IPlaybackCallback {

    /**
     * Notifies the app that playback of a song started
     */
    void onSongStarted(in Song s);

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