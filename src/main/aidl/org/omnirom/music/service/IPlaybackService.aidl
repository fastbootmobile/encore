package org.omnirom.music.service;

import org.omnirom.music.model.Song;
import org.omnirom.music.model.Album;
import org.omnirom.music.model.Playlist;
import org.omnirom.music.model.Artist;

import org.omnirom.music.providers.ProviderIdentifier;

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
     * Plays the provided album fully, overwriting the existing listening queue
     * @param a The Album to play right now
     */
    void playAlbum(in Album a);

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
     * Queue the provided album in the listening queue
     * @param a The album to queue
     * @param top If set to true, the album will be queued at the top of the queue
     */
    void queueAlbum(in Album p, boolean top);

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
     * Returns whether or not the current song is paused
     */
    boolean isPaused();

    /**
     * Returns the current song length, in milliseconds
     */
    int getCurrentTrackLength();

    /**
     * Returns the current playback position of the track, in milliseconds
     */
    int getCurrentTrackPosition();

    /**
     * Returns the currently playing track, or null if none
     */
    Song getCurrentTrack();

    /**
     * Returns the current playback queue, including the currently playing track
     */
    List<Song> getCurrentPlaybackQueue();

    /**
     * Returns the current RMS level of the currently playing output
     */
    int getCurrentRms();

    /**
     * Returns the current DSP chain
     */
    List<ProviderIdentifier> getDSPChain();

    /**
     * Sets the active DSP chain to use
     */
    void setDSPChain(in List<ProviderIdentifier> chain);

    /**
     * Seek the current track to the specified position
     */
    void seek(long timeMs);

    /**
     * Fast-forward to the next track of the queue
     */
    void next();
}