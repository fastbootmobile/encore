package org.omnirom.music.service;

import org.omnirom.music.model.Song;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Artist;

import org.omnirom.music.service.IPlaybackCallback;

interface IPlaybackService {

    void addCallback(in IPlaybackCallback cb);

    void removeCallback(in IPlaybackCallback cb);

    /**
     * Plays the provided playlist fully, overwriting the existing listening queue
     * @param p The playlist to play right now
     */
    void playPlaylist(in Playlist p);

    /**
     * Plays the provided song immediately, overwriting the existing listening queue
     * @param s The song to play right now
     */
    void playSong(in Song s);

    /**
     * Queue the provided playlist in the listening queue
     * @param p The playlist to queue
     * @param top If set to true, the playlist will be queued at the top of the queue
     */
    void queuePlaylist(in Playlist p, boolean top);

    /**
     * Queue the provided song in the listening queue
     * @param p The song to queue
     * @param top If set to true, the song will be queued at the top of the queue
     */
    void queueSong(in Song s, boolean top);

    /**
     * Pauses the playback
     */
    void pause();

    /**
     * Resumes the playback
     * @return true if something is indeed resumable, otherwise false (nothing happened)
     */
    boolean play();

    /**
     * Returns whether or not a song is currently playing
     */
    boolean isPlaying();

    /**
     * Returns the current RMS level of the currently playing output
     */
    int getCurrentRms();

}