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
     * Notifies the playback position (scrobbling)
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

}