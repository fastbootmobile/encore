package org.omnirom.music.framework;

import android.os.RemoteException;

import org.omnirom.music.model.Song;
import org.omnirom.music.service.IPlaybackCallback;

/**
 * Implementation of the Playback Callback AIDL
 */
public class PlaybackCallbackImpl extends IPlaybackCallback.Stub {

    private PlaybackState mState;

    public PlaybackCallbackImpl(PlaybackState state) {
        mState = state;
    }

    @Override
    public void onSongStarted(Song s) throws RemoteException {
        mState.setCurrentSong(s);
        mState.setPlaybackPosition(0);
    }

    @Override
    public void onSongScrobble(int timeMs) throws RemoteException {
        mState.setPlaybackPosition(timeMs);
    }

    @Override
    public void onPlaybackPause() throws RemoteException {

    }

    @Override
    public void onPlaybackResume() throws RemoteException {

    }
}
