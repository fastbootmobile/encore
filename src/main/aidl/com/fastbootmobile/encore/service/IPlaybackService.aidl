package com.fastbootmobile.encore.service;

import com.fastbootmobile.encore.model.Song;
import com.fastbootmobile.encore.model.Album;
import com.fastbootmobile.encore.model.Playlist;
import com.fastbootmobile.encore.model.Artist;

import com.fastbootmobile.encore.providers.ProviderIdentifier;

import com.fastbootmobile.encore.service.IPlaybackCallback;

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
     * Queues the song after the currently playing song
     * @param s The song to queue
     */
    void playNext(in Song s);

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
     * Returns the current playback state
     * @return One of PlaybackService.STATE_*
     */
    int getState();

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
     * Returns the index of the current track in the playback queue
     */
    int getCurrentTrackIndex();

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

    /**
     * Restarts the current song if the progress is less than 4 seconds. Goes to the previous track
     * (if any) otherwise.
     */
    void previous();

    /**
     * Plays the song at the specified index in the playback queue
     */
    void playAtQueueIndex(int index);

    /**
     * Stops the playback and remove the notification
     */
    void stop();

    /**
     * Sets whether or not the current playback queue should be repeated forever
     */
    void setRepeatMode(boolean repeat);

    /**
     * Returns whether or not the repeat mode is enabled
     */
    boolean isRepeatMode();

    /**
     * Sets whether or not the current playback queue will be played in a random order
     */
    void setShuffleMode(boolean shuffle);

    /**
     * Returns whether or not the shuffle mode is enabled
     */
    boolean isShuffleMode();

    /**
     * Clears the playback queue
     */
    void clearPlaybackQueue();

    /**
     * Sets the timeout (sleep timer) to stop playback
     * @param uptime The point (in SystemClock.uptimeMillis() spacetime) at which the playback
     * should stop.
     */
    void setSleepTimer(long uptime);

    /**
     * Returns the time (in SystemClock.uptimeMillis() spacetime) at which the playback should stop
     */
    long getSleepTimerEndTime();
}