package org.omnirom.music.framework;

import org.omnirom.music.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * Retains the current status of the playback
 */
public class PlaybackState {

    private List<Listener> mListeners;
    private Song mCurrentSong;
    private int mPlaybackPosition;
    private int mPlaybackState;

    public static final int STATE_PLAYING = 0;
    public static final int STATE_PAUSED = 1;

    public interface Listener {
        public void onCurrentSongChanged(PlaybackState state, Song song);
        public void onPlaybackPositionChanged(PlaybackState state, int posMs);
        public void onPlaybackStateChanged(PlaybackState state, int newState);
    }

    public PlaybackState() {
        mListeners = new ArrayList<Listener>();
    }

    public void addListener(Listener listener) {
        mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    /**
     * Sets the current playing song
     * @param currentSong The song currently playing
     */
    public void setCurrentSong(Song currentSong) {
        mCurrentSong = currentSong;
        for (Listener listener : mListeners) {
            listener.onCurrentSongChanged(this, currentSong);
        }
    }

    /**
     * @return The currently playing song
     */
    public Song getCurrentSong() {
        return mCurrentSong;
    }

    /**
     * Sets the current playback position of the current song
     * @param posMs The playback position, in milliseconds
     */
    public void setPlaybackPosition(int posMs) {
        mPlaybackPosition = posMs;
        for (Listener listener : mListeners) {
            listener.onPlaybackPositionChanged(this, posMs);
        }
    }

    /**
     * @return The current playback position of the current song, in milliseconds
     */
    public int getPlaybackPosition() {
        return mPlaybackPosition;
    }

    /**
     * Sets the current playback state
     * @param state One of STATE_**
     */
    public void setPlaybackState(int state) {
        mPlaybackState = state;
        for (Listener listener : mListeners) {
            listener.onPlaybackStateChanged(this, state);
        }
    }

    /**
     * Returns the current state of the playback
     * @return One of STATE_**
     */
    public int getPlaybackState() {
        return mPlaybackState;
    }


}
