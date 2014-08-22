package org.omnirom.music.service;

import android.os.RemoteException;

import org.omnirom.music.model.Song;

/**
 * Created by Guigui on 22/08/2014.
 */
public class BasePlaybackCallback extends IPlaybackCallback.Stub {
    @Override
    public void onSongStarted(Song s) throws RemoteException {

    }

    @Override
    public void onSongScrobble(int timeMs) throws RemoteException {

    }

    @Override
    public void onPlaybackPause() throws RemoteException {

    }

    @Override
    public void onPlaybackResume() throws RemoteException {

    }

    @Override
    public void onPlaybackQueueChanged() throws RemoteException {

    }
}
